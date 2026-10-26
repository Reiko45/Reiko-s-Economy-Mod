package com.reiko.economy.auction;

import com.reiko.economy.bank.InventorySpace;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.Money;
import com.reiko.economy.core.TaxKind;
import com.reiko.economy.core.TaxService;
import com.reiko.economy.menu.AuctionMenu;
import com.reiko.economy.shop.ShopActionResult;
import com.reiko.economy.shop.ShopNotices;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

/**
 * The auction house. Sellers put items up for a fixed price anyone can pay ("buy now"), or as a timed auction
 * where the highest bid wins. The item is held by the auction house until it sells or comes back.
 *
 * <ul>
 *   <li>Listing costs a small fee, and the auction house keeps a cut of each sale. Both leave the economy.</li>
 *   <li>Buyers and bidders pay from their bank balance. A bid is taken when it's made and given back in full
 *       if someone outbids it. A bid in the last minute pushes the end back to a minute away.</li>
 *   <li>Items bought, won, or coming back go straight into the player's inventory if they're online and it
 *       fits; otherwise they wait in the Collect tab. Money always goes to the bank.</li>
 *   <li>Buy-now listings come back after a few days; an auction nobody bid on comes back when it ends.</li>
 * </ul>
 */
public final class AuctionService {
    private static final LongSupplier SYSTEM_CLOCK = System::currentTimeMillis;
    private static LongSupplier clock = SYSTEM_CLOCK;

    private AuctionService() {
    }

    /** Pretends it's another time, for game tests. Null goes back to the real clock. */
    public static void setClockForTests(Long millis) {
        clock = millis == null ? SYSTEM_CLOCK : () -> millis;
    }

    /** Milliseconds since 1970. Auctions run on real time. */
    public static long now() {
        return clock.getAsLong();
    }

    /** Opens the auction house screen. */
    public static void open(ServerPlayer player) {
        if (!EconomyConfig.auctionRules().enabled()) {
            player.displayClientMessage(Component.translatable("auction.reikoeconomy.disabled"), true);
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new AuctionMenu(id, inventory),
                Component.translatable("container.reikoeconomy.auction")));
    }

    // ---- selling ----

    /**
     * Puts {@code item} up for sale. The caller takes the item away from the player only if this succeeds.
     *
     * @param hours 0 for buy-now at {@code price}; otherwise an auction of that many hours starting at {@code price}
     */
    public static ShopActionResult list(ServerPlayer seller, ItemStack item, long price, int hours) {
        AuctionRules rules = EconomyConfig.auctionRules();
        if (!rules.enabled()) {
            return ShopActionResult.fail("auction.reikoeconomy.disabled");
        }
        if (item.isEmpty()) {
            return ShopActionResult.fail("auction.reikoeconomy.nothing_to_sell");
        }
        if (price <= 0 || price > Money.MAX_BALANCE) {
            return ShopActionResult.fail("auction.reikoeconomy.bad_price");
        }
        boolean auction = hours != 0;
        if (auction && !AuctionRules.validHours(hours)) {
            return ShopActionResult.fail("auction.reikoeconomy.bad_hours");
        }
        MinecraftServer server = seller.server;
        AuctionData data = AuctionData.get(server);
        long mine = data.listings().stream().filter(listing -> listing.seller().equals(seller.getUUID())).count();
        if (mine >= rules.maxListings()) {
            return ShopActionResult.fail("auction.reikoeconomy.too_many", rules.maxListings());
        }
        EconomyService eco = EconomyService.INSTANCE;
        eco.ensureAccount(server, seller.getUUID(), seller.getGameProfile().getName());
        if (rules.listingFee() > 0 && !TaxService.take(server, seller.getUUID(), TaxKind.AUCTION, rules.listingFee())) {
            return ShopActionResult.fail("auction.reikoeconomy.cant_pay_fee", eco.format(rules.listingFee()));
        }
        long now = now();
        AuctionData.Listing listing = new AuctionData.Listing(UUID.randomUUID(), seller.getUUID(), seller.getGameProfile().getName(),
                item.copy(), auction, price, now, now + (auction ? hours * 3_600_000L : rules.listingMillis()));
        data.add(listing);
        Component name = describe(item);
        return ShopActionResult.ok(auction
                ? Component.translatable("auction.reikoeconomy.auction_started", name, eco.format(price), hours)
                : Component.translatable("auction.reikoeconomy.listed", name, eco.format(price)));
    }

    /** Takes a listing down and gives the item back. Auctions with bids can't be cancelled. */
    public static ShopActionResult cancel(ServerPlayer player, UUID id) {
        AuctionData data = AuctionData.get(player.server);
        AuctionData.Listing listing = data.find(id).orElse(null);
        if (listing == null) {
            return ShopActionResult.fail("auction.reikoeconomy.gone");
        }
        if (!listing.seller().equals(player.getUUID())) {
            return ShopActionResult.fail("auction.reikoeconomy.not_yours");
        }
        if (listing.hasBids()) {
            return ShopActionResult.fail("auction.reikoeconomy.has_bids");
        }
        data.remove(listing);
        boolean claimed = !deliver(player.server, player.getUUID(), listing.item());
        return ShopActionResult.ok(Component.translatable(claimed ? "auction.reikoeconomy.cancelled_claim" : "auction.reikoeconomy.cancelled",
                describe(listing.item())));
    }

    // ---- buying ----

    /** Buys a buy-now listing from the buyer's bank balance. {@code expectedPrice} is the price they saw. */
    public static ShopActionResult buy(ServerPlayer buyer, UUID id, long expectedPrice) {
        MinecraftServer server = buyer.server;
        AuctionData data = AuctionData.get(server);
        AuctionData.Listing listing = data.find(id).filter(found -> found.endsAt() > now()).orElse(null);
        if (listing == null) {
            return ShopActionResult.fail("auction.reikoeconomy.gone");
        }
        if (listing.auction()) {
            return ShopActionResult.fail("auction.reikoeconomy.is_auction");
        }
        if (listing.seller().equals(buyer.getUUID())) {
            return ShopActionResult.fail("auction.reikoeconomy.own_listing");
        }
        if (listing.price() != expectedPrice) {
            return ShopActionResult.fail("shop.reikoeconomy.price_changed");
        }
        EconomyService eco = EconomyService.INSTANCE;
        eco.ensureAccount(server, buyer.getUUID(), buyer.getGameProfile().getName());
        if (!eco.withdraw(server, buyer.getUUID(), listing.price(), "auction.buy").isSuccess()) {
            return ShopActionResult.fail("auction.reikoeconomy.cant_afford", eco.format(listing.price()),
                    eco.format(eco.getBalance(server, buyer.getUUID())));
        }
        data.remove(listing);
        long received = paySeller(server, listing, listing.price());
        boolean claimed = !deliver(server, buyer.getUUID(), listing.item());
        ShopNotices.alert(server, listing.seller(), Component.translatable("auction.reikoeconomy.sold_notice", describe(listing.item()),
                buyer.getGameProfile().getName(), eco.format(listing.price()), eco.format(received)));
        return ShopActionResult.ok(Component.translatable(claimed ? "auction.reikoeconomy.bought_claim" : "auction.reikoeconomy.bought",
                describe(listing.item()), eco.format(listing.price())));
    }

    /** Bids {@code amount} on an auction. The bid comes out of the bidder's bank now and is refunded if they're outbid. */
    public static ShopActionResult bid(ServerPlayer bidder, UUID id, long amount) {
        MinecraftServer server = bidder.server;
        AuctionData data = AuctionData.get(server);
        AuctionRules rules = EconomyConfig.auctionRules();
        long now = now();
        AuctionData.Listing listing = data.find(id).filter(found -> found.endsAt() > now).orElse(null);
        if (listing == null) {
            return ShopActionResult.fail("auction.reikoeconomy.gone");
        }
        if (!listing.auction()) {
            return ShopActionResult.fail("auction.reikoeconomy.not_auction");
        }
        if (listing.seller().equals(bidder.getUUID())) {
            return ShopActionResult.fail("auction.reikoeconomy.own_listing");
        }
        if (bidder.getUUID().equals(listing.bidder())) {
            return ShopActionResult.fail("auction.reikoeconomy.already_highest");
        }
        EconomyService eco = EconomyService.INSTANCE;
        long minimum = rules.minimumBid(listing.price(), listing.bid(), listing.hasBids());
        if (amount < minimum) {
            return ShopActionResult.fail("auction.reikoeconomy.bid_too_low", eco.format(minimum));
        }
        if (amount > Money.MAX_BALANCE) {
            return ShopActionResult.fail("auction.reikoeconomy.bad_price");
        }
        eco.ensureAccount(server, bidder.getUUID(), bidder.getGameProfile().getName());
        if (!eco.withdraw(server, bidder.getUUID(), amount, "auction.bid").isSuccess()) {
            return ShopActionResult.fail("auction.reikoeconomy.cant_afford", eco.format(amount),
                    eco.format(eco.getBalance(server, bidder.getUUID())));
        }
        if (listing.hasBids()) {
            UUID outbid = listing.bidder();
            eco.deposit(server, outbid, listing.bid(), "auction.refund");
            ShopNotices.alert(server, outbid, Component.translatable("auction.reikoeconomy.outbid", describe(listing.item()),
                    eco.format(amount), eco.format(listing.bid())));
        }
        data.setBid(listing, amount, bidder.getUUID(), bidder.getGameProfile().getName());
        if (listing.endsAt() - now < AuctionRules.SNIPE_GUARD_MILLIS) {
            data.setEndsAt(listing, now + AuctionRules.SNIPE_GUARD_MILLIS);
        }
        return ShopActionResult.ok(Component.translatable("auction.reikoeconomy.bid_placed", eco.format(amount), describe(listing.item())));
    }

    // ---- ending ----

    /** Ends every auction and buy-now listing whose time is up. */
    public static void tick(MinecraftServer server) {
        AuctionData data = AuctionData.get(server);
        long now = now();
        for (AuctionData.Listing listing : List.copyOf(data.listings())) {
            if (listing.endsAt() <= now) {
                end(server, data, listing);
            }
        }
    }

    private static void end(MinecraftServer server, AuctionData data, AuctionData.Listing listing) {
        EconomyService eco = EconomyService.INSTANCE;
        data.remove(listing);
        if (listing.hasBids()) {
            long received = paySeller(server, listing, listing.bid());
            boolean claimed = !deliver(server, listing.bidder(), listing.item());
            ShopNotices.alert(server, listing.bidder(), Component.translatable(
                    claimed ? "auction.reikoeconomy.won_claim" : "auction.reikoeconomy.won", describe(listing.item()), eco.format(listing.bid())));
            ShopNotices.alert(server, listing.seller(), Component.translatable("auction.reikoeconomy.auction_sold",
                    describe(listing.item()), listing.bidderName(), eco.format(listing.bid()), eco.format(received)));
        } else {
            boolean claimed = !deliver(server, listing.seller(), listing.item());
            ShopNotices.alert(server, listing.seller(), Component.translatable(
                    claimed ? "auction.reikoeconomy.returned_claim" : "auction.reikoeconomy.returned", describe(listing.item())));
        }
    }

    /** Pays the seller the price minus the auction house's cut. Returns what they got. */
    private static long paySeller(MinecraftServer server, AuctionData.Listing listing, long price) {
        EconomyService eco = EconomyService.INSTANCE;
        long cut = Math.min(price, EconomyConfig.auctionRules().cut(price));
        eco.ensureAccount(server, listing.seller(), listing.sellerName());
        if (price - cut > 0) {
            eco.deposit(server, listing.seller(), price - cut, "auction.sale");
        }
        TaxService.collected(server, TaxKind.AUCTION, cut);
        return price - cut;
    }

    /**
     * Gives the player an item: straight into their inventory if they're online and it fits, otherwise into
     * their Collect tab. Returns true if it went into the inventory.
     */
    static boolean deliver(MinecraftServer server, UUID player, ItemStack item) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null && InventorySpace.addAll(InventorySpace.copyOf(online.getInventory().items), List.of(item.copy()))) {
            online.getInventory().add(item.copy());
            online.getInventory().setChanged();
            return true;
        }
        AuctionData.get(server).addClaim(player, item);
        return false;
    }

    /** Moves whatever fits from the player's Collect tab into their inventory. */
    public static ShopActionResult collect(ServerPlayer player) {
        AuctionData data = AuctionData.get(player.server);
        List<ItemStack> waiting = data.claims(player.getUUID());
        if (waiting.isEmpty()) {
            return ShopActionResult.fail("auction.reikoeconomy.nothing_to_collect");
        }
        List<ItemStack> left = new ArrayList<>();
        int collected = 0;
        for (ItemStack item : waiting) {
            if (InventorySpace.addAll(InventorySpace.copyOf(player.getInventory().items), List.of(item.copy()))) {
                player.getInventory().add(item.copy());
                collected++;
            } else {
                left.add(item);
            }
        }
        player.getInventory().setChanged();
        data.setClaims(player.getUUID(), left);
        if (collected == 0) {
            return ShopActionResult.fail("auction.reikoeconomy.inventory_full");
        }
        return ShopActionResult.ok(left.isEmpty()
                ? Component.translatable("auction.reikoeconomy.collected", collected)
                : Component.translatable("auction.reikoeconomy.collected_some", collected, left.size()));
    }

    /** Takes down all of a player's listings (refunding bids) and puts the items in their Collect tab. For admins. */
    public static int removeAll(MinecraftServer server, UUID seller) {
        AuctionData data = AuctionData.get(server);
        EconomyService eco = EconomyService.INSTANCE;
        int removed = 0;
        for (AuctionData.Listing listing : List.copyOf(data.listings())) {
            if (listing.seller().equals(seller)) {
                data.remove(listing);
                if (listing.hasBids()) {
                    eco.deposit(server, listing.bidder(), listing.bid(), "auction.refund");
                    ShopNotices.alert(server, listing.bidder(), Component.translatable("auction.reikoeconomy.removed_refund",
                            describe(listing.item()), eco.format(listing.bid())));
                }
                data.addClaim(seller, listing.item());
                removed++;
            }
        }
        return removed;
    }

    /** "16 × Diamond", or just "Diamond Sword" for one. */
    public static Component describe(ItemStack item) {
        return item.getCount() == 1 ? item.getHoverName()
                : Component.translatable("auction.reikoeconomy.stack", item.getCount(), item.getHoverName());
    }
}
