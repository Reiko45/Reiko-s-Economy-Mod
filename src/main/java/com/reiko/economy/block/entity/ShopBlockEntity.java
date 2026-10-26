package com.reiko.economy.block.entity;

import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.bank.InventorySpace;
import com.reiko.economy.bank.Purse;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.Money;
import com.reiko.economy.registry.ModBlockEntities;
import com.reiko.economy.shop.InventorySync;
import com.reiko.economy.shop.Payment;
import com.reiko.economy.shop.ShopActionResult;
import com.reiko.economy.shop.ShopNotices;
import com.reiko.economy.shop.ShopPayments;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A player-owned shop: the owner stocks it with one kind of item and sets a price per bundle; other players
 * buy from it, and the money goes straight into the owner's bank account, even while they are offline.
 *
 * <p>The owner can also set a "shop pays" price. Then players can sell the item to the shop: they get cash,
 * the money comes out of the owner's bank account, and the items go into the stock. The shop stops buying
 * when the stock is full or the owner can't afford another bundle. Either price can be 0 to turn that side
 * off, so a shop can sell, buy, or both.
 *
 * <p>Hoppers and pipes can add the shop's item to the stock but can't take anything out (see
 * {@link com.reiko.economy.shop.InsertOnlyStock}), so nobody can drain someone else's shop.
 */
public final class ShopBlockEntity extends AbstractShopBlockEntity {
    public static final int STOCK_SLOTS = 27;

    @Nullable
    private UUID owner;
    private String ownerName = "";
    /** What the shop pays per bundle when players sell to it. 0 means it doesn't buy. */
    private long sellPrice;
    /** Whether at least one bundle is in stock. Synced so the floating label can say "Out of stock". */
    private boolean inStock;

    private final ItemStackHandler stock = new ItemStackHandler(STOCK_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            onStockChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return matchesTemplate(stack);
        }
    };

    public ShopBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHOP.get(), pos, state);
    }

    // ---- ownership ----

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name;
        markChangedAndSync();
    }

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    // ---- stock ----

    public ItemStackHandler stock() {
        return stock;
    }

    public boolean matchesTemplate(ItemStack stack) {
        return !template.isEmpty() && ItemStack.isSameItemSameComponents(stack, template);
    }

    /** Number of items (not bundles) in stock. */
    public int stockCount() {
        int count = 0;
        for (int i = 0; i < stock.getSlots(); i++) {
            ItemStack stack = stock.getStackInSlot(i);
            if (matchesTemplate(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public int availableBundles() {
        return template.isEmpty() ? 0 : stockCount() / template.getCount();
    }

    public boolean isStockEmpty() {
        for (int i = 0; i < stock.getSlots(); i++) {
            if (!stock.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** The space left in the stock, in whole bundles. */
    public int roomInBundles() {
        if (template.isEmpty()) {
            return 0;
        }
        int room = 0;
        for (int i = 0; i < stock.getSlots(); i++) {
            ItemStack stack = stock.getStackInSlot(i);
            int limit = Math.min(stock.getSlotLimit(i), template.getMaxStackSize());
            if (stack.isEmpty()) {
                room += limit;
            } else if (matchesTemplate(stack)) {
                room += Math.max(0, limit - stack.getCount());
            }
        }
        return room / template.getCount();
    }

    public long sellPrice() {
        return sellPrice;
    }

    /** True if players can buy the item here. */
    public boolean sellsToPlayers() {
        return !template.isEmpty() && price > 0;
    }

    /** True if players can sell the item here. */
    public boolean buysFromPlayers() {
        return !template.isEmpty() && sellPrice > 0;
    }

    public boolean isConfigured() {
        return sellsToPlayers() || buysFromPlayers();
    }

    /**
     * How many bundles the shop can buy right now: limited by the room in its stock and by what the owner can
     * afford. Server only.
     */
    public int bundlesWanted(MinecraftServer server) {
        if (!buysFromPlayers() || owner == null) {
            return 0;
        }
        long affordable = EconomyService.INSTANCE.getBalance(server, owner) / sellPrice;
        return (int) Math.min(roomInBundles(), affordable);
    }

    /** Client-side display flag; on the server use {@link #availableBundles()}. */
    public boolean displayInStock() {
        return inStock;
    }

    private void onStockChanged() {
        setChanged();
        refreshInStock();
    }

    private void refreshInStock() {
        boolean now = availableBundles() > 0;
        if (now != inStock) {
            inStock = now;
            markChangedAndSync();
        }
    }

    public void dropStock(Level level, BlockPos pos) {
        for (int i = 0; i < stock.getSlots(); i++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stock.getStackInSlot(i));
            stock.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    // ---- owner setup ----

    /**
     * Sets the item for sale. The stack's count becomes the bundle size. The item can't change while the shop
     * still holds stock of a different item, so stock never ends up stuck in a shop that won't sell it.
     */
    public ShopActionResult trySetTemplate(ItemStack requested) {
        boolean sameItem = !requested.isEmpty() && matchesTemplate(requested);
        if (!sameItem && !isStockEmpty()) {
            return ShopActionResult.fail("shop.reikoeconomy.template.stock_not_empty");
        }
        if (requested.isEmpty()) {
            setTemplateInternal(ItemStack.EMPTY);
            refreshInStock();
            return ShopActionResult.ok(Component.translatable("shop.reikoeconomy.template.cleared"));
        }
        setTemplateInternal(requested.copyWithCount(Math.min(requested.getCount(), requested.getMaxStackSize())));
        refreshInStock();
        return ShopActionResult.ok(Component.translatable("shop.reikoeconomy.template.set",
                template.getCount(), template.getHoverName()));
    }

    /** Sets what players pay, keeping what the shop pays. */
    public ShopActionResult trySetPrice(String text) {
        OptionalLong parsed = Money.parsePositive(text);
        if (parsed.isEmpty()) {
            return ShopActionResult.fail(TransactionResult.INVALID_AMOUNT.translationKey());
        }
        if (sellPrice > parsed.getAsLong()) {
            return ShopActionResult.fail("shop.reikoeconomy.price.pays_more");
        }
        setPriceInternal(parsed.getAsLong());
        return ShopActionResult.ok(Component.translatable("shop.reikoeconomy.price.set",
                EconomyService.INSTANCE.format(price)));
    }

    /**
     * Sets both prices from text: what players pay to buy a bundle, and what the shop pays them to sell one.
     * Blank means 0, which turns that side off, but at least one has to be on. The shop can't pay more than
     * it charges, or anyone could buy a bundle and sell it straight back at a profit, draining the owner.
     */
    public ShopActionResult trySetPrices(String priceText, String sellText) {
        OptionalLong buy = priceText.isBlank() ? OptionalLong.of(0) : Money.parse(priceText);
        OptionalLong sell = sellText.isBlank() ? OptionalLong.of(0) : Money.parse(sellText);
        if (buy.isEmpty() || sell.isEmpty() || !validPrice(buy.getAsLong()) || !validPrice(sell.getAsLong())) {
            return ShopActionResult.fail(TransactionResult.INVALID_AMOUNT.translationKey());
        }
        long newPrice = buy.getAsLong();
        long newSellPrice = sell.getAsLong();
        if (newPrice == 0 && newSellPrice == 0) {
            return ShopActionResult.fail("shop.reikoeconomy.price.none");
        }
        if (newPrice > 0 && newSellPrice > newPrice) {
            return ShopActionResult.fail("shop.reikoeconomy.price.pays_more");
        }
        price = newPrice;
        sellPrice = newSellPrice;
        markChangedAndSync();
        EconomyService eco = EconomyService.INSTANCE;
        if (sellPrice == 0) {
            return ShopActionResult.ok(Component.translatable("shop.reikoeconomy.prices_set.sell_only", eco.format(price)));
        }
        if (price == 0) {
            return ShopActionResult.ok(Component.translatable("shop.reikoeconomy.prices_set.buy_only", eco.format(sellPrice)));
        }
        return ShopActionResult.ok(Component.translatable("shop.reikoeconomy.prices_set", eco.format(price), eco.format(sellPrice)));
    }

    // ---- selling ----

    /**
     * Sells {@code bundles} bundles to {@code buyer}. All checks happen before anything changes: the price the
     * buyer saw must still be the price, the stock must cover the whole order and the buyer must be able to pay
     * by card or cash. The money moves first; the items only leave the shop once the payment has gone through.
     */
    public ShopActionResult sellTo(ServerPlayer buyer, int bundles, long expectedPrice, Payment payment) {
        if (!isConfigured()) {
            return ShopActionResult.fail("shop.reikoeconomy.not_set_up");
        }
        if (!sellsToPlayers()) {
            return ShopActionResult.fail("shop.reikoeconomy.admin.not_selling");
        }
        if (owner == null) {
            return ShopActionResult.fail("shop.reikoeconomy.no_owner");
        }
        if (isOwner(buyer)) {
            return ShopActionResult.fail("shop.reikoeconomy.own_shop");
        }
        if (expectedPrice != price) {
            return ShopActionResult.fail("shop.reikoeconomy.price_changed");
        }
        if (bundles < 1 || bundles > MAX_BUNDLES_PER_TRADE) {
            return ShopActionResult.fail("shop.reikoeconomy.bad_quantity", MAX_BUNDLES_PER_TRADE);
        }
        int itemsNeeded = template.getCount() * bundles;
        if (stockCount() < itemsNeeded) {
            return ShopActionResult.fail("shop.reikoeconomy.out_of_stock", availableBundles());
        }
        long total = price * bundles; // price <= 1e14 and bundles <= 64, so this cannot overflow
        if (total > Money.MAX_BALANCE) {
            return ShopActionResult.fail(TransactionResult.BALANCE_LIMIT.translationKey());
        }

        MinecraftServer server = buyer.server;
        EconomyService eco = EconomyService.INSTANCE;
        // The owner has an account from when they joined; this only matters if the ledger was reset since.
        eco.ensureAccount(server, owner, ownerName);
        ShopPayments.Charge charge = ShopPayments.charge(buyer, payment, total, owner, "shop.buy",
                Component.translatable("credit.reikoeconomy.history.shop", ownerName, itemsNeeded, template.getHoverName()));
        if (!charge.success()) {
            return ShopActionResult.fail(charge.message());
        }

        ItemStack sold = template.copyWithCount(itemsNeeded);
        List<ItemStack> before = InventorySync.snapshot(buyer);
        for (ItemStack stack : takeFromStock(itemsNeeded)) {
            ItemHandlerHelper.giveItemToPlayer(buyer, stack);
        }
        InventorySync.sendChanges(buyer, before);

        String formattedTotal = eco.format(total);
        ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(owner);
        if (ownerPlayer != null) {
            ownerPlayer.sendSystemMessage(Component.translatable("shop.reikoeconomy.sold_notice",
                    buyer.getGameProfile().getName(), itemsNeeded, sold.getHoverName(), formattedTotal));
        } else {
            ShopNotices.sale(server, owner, total);
        }
        if (availableBundles() == 0) {
            ShopNotices.alert(server, owner, Component.translatable("shop.reikoeconomy.alert.sold_out", sold.getHoverName(),
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()));
        }
        return ShopActionResult.ok(ShopPayments.receipt(Component.translatable("shop.reikoeconomy.bought",
                itemsNeeded, sold.getHoverName(), formattedTotal), payment, charge));
    }

    /**
     * Buys up to {@code bundles} bundles from {@code seller}'s main inventory and hotbar (never their armor),
     * paying them in cash from the owner's bank account. Pass {@link Integer#MAX_VALUE} to buy everything they
     * have, as far as the stock's room and the owner's money go. Only exact matches count, so damaged or
     * enchanted copies of the item aren't accepted. All checks happen before anything moves.
     */
    public ShopActionResult buyFrom(ServerPlayer seller, int bundles, long expectedSellPrice) {
        if (!buysFromPlayers()) {
            return ShopActionResult.fail("shop.reikoeconomy.admin.not_buying");
        }
        if (owner == null) {
            return ShopActionResult.fail("shop.reikoeconomy.no_owner");
        }
        if (isOwner(seller)) {
            return ShopActionResult.fail("shop.reikoeconomy.own_shop_sell");
        }
        if (expectedSellPrice != sellPrice) {
            return ShopActionResult.fail("shop.reikoeconomy.price_changed");
        }
        if (bundles < 1) {
            return ShopActionResult.fail("shop.reikoeconomy.bad_quantity", MAX_BUNDLES_PER_TRADE);
        }
        List<ItemStack> slots = seller.getInventory().items;
        int heldBundles = countMatching(slots) / template.getCount();
        if (heldBundles < 1) {
            return ShopActionResult.fail("shop.reikoeconomy.admin.nothing_to_sell", template.getCount(), template.getHoverName());
        }
        int room = roomInBundles();
        if (room < 1) {
            return ShopActionResult.fail("shop.reikoeconomy.shop_full");
        }
        MinecraftServer server = seller.server;
        EconomyService eco = EconomyService.INSTANCE;
        eco.ensureAccount(server, owner, ownerName);
        long affordable = eco.getBalance(server, owner) / sellPrice;
        if (affordable < 1) {
            return ShopActionResult.fail("shop.reikoeconomy.owner_cant_pay", ownerName);
        }
        int sellable = (int) Math.min(Math.min(heldBundles, bundles), Math.min(room, affordable));
        long total = sellPrice * sellable; // at most what the owner has, so it can't overflow
        int items = sellable * template.getCount();
        // The cash has to fit (in a wallet, or the inventory once the sold items are gone), or nothing happens.
        List<ItemStack> after = InventorySpace.copyOf(slots);
        removeMatching(after, items);
        if (!Purse.fits(seller, after, total)) {
            return ShopActionResult.fail("payment.reikoeconomy.no_room_for_cash");
        }
        if (!eco.withdraw(server, owner, total, "shop.restock").isSuccess()) {
            return ShopActionResult.fail("shop.reikoeconomy.owner_cant_pay", ownerName);
        }

        // Nothing can fail from here on: the room was checked, so every item fits in the stock.
        List<ItemStack> before = InventorySync.snapshot(seller);
        removeMatching(slots, items);
        int maxStack = template.getMaxStackSize();
        for (int left = items; left > 0; left -= maxStack) {
            ItemStack rest = ItemHandlerHelper.insertItemStacked(stock, template.copyWithCount(Math.min(left, maxStack)), false);
            if (!rest.isEmpty()) {
                ItemHandlerHelper.giveItemToPlayer(seller, rest); // can't happen, but never lose items
            }
        }
        Purse.give(seller, total);
        seller.getInventory().setChanged();
        InventorySync.sendChanges(seller, before);

        String formattedTotal = eco.format(total);
        ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(owner);
        if (ownerPlayer != null) {
            ownerPlayer.sendSystemMessage(Component.translatable("shop.reikoeconomy.bought_notice",
                    seller.getGameProfile().getName(), items, template.getHoverName(), formattedTotal));
        } else {
            ShopNotices.purchase(server, owner, total);
        }
        if (roomInBundles() == 0) {
            ShopNotices.alert(server, owner, Component.translatable("shop.reikoeconomy.alert.full", template.getHoverName(),
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()));
        } else if (eco.getBalance(server, owner) < sellPrice) {
            ShopNotices.alert(server, owner, Component.translatable("shop.reikoeconomy.alert.cant_pay", template.getHoverName(),
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()));
        }
        return ShopActionResult.ok(Component.translatable("shop.reikoeconomy.admin.sold",
                items, template.getHoverName(), formattedTotal));
    }

    /** Matching items in {@code slots}. */
    public int countMatching(List<ItemStack> slots) {
        int count = 0;
        for (ItemStack stack : slots) {
            if (matchesTemplate(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void removeMatching(List<ItemStack> slots, int count) {
        int remaining = count;
        for (ItemStack stack : slots) {
            if (remaining <= 0) {
                break;
            }
            if (matchesTemplate(stack)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    private List<ItemStack> takeFromStock(int count) {
        List<ItemStack> taken = new ArrayList<>();
        int remaining = count;
        for (int i = 0; i < stock.getSlots() && remaining > 0; i++) {
            if (!matchesTemplate(stock.getStackInSlot(i))) {
                continue;
            }
            ItemStack extracted = stock.extractItem(i, remaining, false);
            remaining -= extracted.getCount();
            if (!extracted.isEmpty()) {
                taken.add(extracted);
            }
        }
        return taken;
    }

    // ---- display ----

    @Override
    public List<Component> labelLines() {
        List<Component> lines = new ArrayList<>();
        if (!isConfigured()) {
            lines.add(Component.translatable("shop.reikoeconomy.label.not_set_up").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        EconomyService eco = EconomyService.INSTANCE;
        if (sellsToPlayers()) {
            // A shop that only sells keeps the short "16 for $5.00"; one that does both says which is which.
            String key = buysFromPlayers() ? "shop.reikoeconomy.label.buy" : "shop.reikoeconomy.label.price";
            lines.add(Component.translatable(key, template.getCount(), eco.format(price)).withStyle(ChatFormatting.GOLD));
            if (!inStock) {
                lines.add(Component.translatable("shop.reikoeconomy.label.out_of_stock").withStyle(ChatFormatting.RED));
            }
        }
        if (buysFromPlayers()) {
            lines.add(Component.translatable("shop.reikoeconomy.label.sell", template.getCount(), eco.format(sellPrice))
                    .withStyle(ChatFormatting.GREEN));
        }
        return lines;
    }

    // ---- saving and syncing ----

    @Override
    protected void writeDisplayData(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeDisplayData(tag, registries);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        tag.putString("owner_name", ownerName);
        tag.putBoolean("in_stock", inStock);
        tag.putLong("sell_price", sellPrice);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("stock", stock.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        inStock = tag.getBoolean("in_stock");
        sellPrice = tag.getLong("sell_price");
        if (tag.contains("stock")) { // only present on the server; clients get display data only
            stock.deserializeNBT(registries, tag.getCompound("stock"));
            inStock = availableBundles() > 0;
        }
    }
}
