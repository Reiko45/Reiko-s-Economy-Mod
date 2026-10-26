package com.reiko.economy.auction;

import com.reiko.economy.config.EconomyConfig;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/**
 * What the auction screen is showing: which tab, the search text, the sort order and the page. The server turns
 * it into an {@link AuctionPage}.
 */
public record AuctionQuery(int tab, String search, int sort, int page) {
    public static final int BROWSE = 0;
    public static final int MINE = 1;
    public static final int SELL = 2;
    public static final int COLLECT = 3;

    public static final int NEWEST = 0;
    public static final int CHEAPEST = 1;
    public static final int PRICIEST = 2;
    public static final int ENDING_SOON = 3;
    public static final int SORTS = 4;

    public static final int PAGE_SIZE = 6;
    public static final AuctionQuery DEFAULT = new AuctionQuery(BROWSE, "", NEWEST, 1);

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionQuery> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AuctionQuery::tab,
            ByteBufCodecs.stringUtf8(64), AuctionQuery::search,
            ByteBufCodecs.VAR_INT, AuctionQuery::sort,
            ByteBufCodecs.VAR_INT, AuctionQuery::page,
            AuctionQuery::new);

    /** Builds the page this query asks for, as {@code viewer} sees it. */
    public AuctionPage run(MinecraftServer server, UUID viewer) {
        AuctionData data = AuctionData.get(server);
        AuctionRules rules = EconomyConfig.auctionRules();
        long now = AuctionService.now();
        int mine = (int) data.listings().stream().filter(listing -> listing.seller().equals(viewer)).count();
        List<ItemStack> claims = data.claims(viewer);

        List<AuctionRow> all;
        if (tab == COLLECT) {
            all = claims.stream().map(item -> new AuctionRow(null, item.copy(), "", false, 0, 0, "", 0, true, false)).toList();
        } else if (tab == BROWSE || tab == MINE) {
            String wanted = search.trim().toLowerCase(Locale.ROOT);
            Comparator<AuctionData.Listing> order = switch (sort) {
                case CHEAPEST -> Comparator.comparingDouble(AuctionQuery::pricePerItem);
                case PRICIEST -> Comparator.comparingDouble(AuctionQuery::pricePerItem).reversed();
                case ENDING_SOON -> Comparator.comparingLong(AuctionData.Listing::endsAt);
                default -> Comparator.comparingLong(AuctionData.Listing::createdAt).reversed();
            };
            all = data.listings().stream()
                    .filter(listing -> listing.endsAt() > now)
                    .filter(listing -> tab == BROWSE || listing.seller().equals(viewer))
                    .filter(listing -> wanted.isEmpty() || matches(listing.item(), wanted))
                    .sorted(order)
                    .map(listing -> new AuctionRow(listing.id(), listing.item().copy(), listing.sellerName(), listing.auction(),
                            listing.currentPrice(), rules.minimumBid(listing.price(), listing.bid(), listing.hasBids()),
                            listing.bidderName(), listing.endsAt() - now, listing.seller().equals(viewer),
                            viewer.equals(listing.bidder())))
                    .toList();
        } else {
            all = List.of();
        }
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int shown = Math.max(1, Math.min(page, pages));
        List<AuctionRow> rows = all.subList((shown - 1) * PAGE_SIZE, Math.min(all.size(), shown * PAGE_SIZE));
        return new AuctionPage(tab, List.copyOf(rows), shown, pages, all.size(), mine, claims.size());
    }

    private static double pricePerItem(AuctionData.Listing listing) {
        return (double) listing.currentPrice() / listing.item().getCount();
    }

    /** The item's name or id contains the search text. */
    private static boolean matches(ItemStack item, String wanted) {
        return item.getHoverName().getString().toLowerCase(Locale.ROOT).contains(wanted)
                || BuiltInRegistries.ITEM.getKey(item.getItem()).getPath().contains(wanted.replace(' ', '_'));
    }
}
