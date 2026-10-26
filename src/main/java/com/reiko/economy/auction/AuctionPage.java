package com.reiko.economy.auction;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * One page of the auction screen's list, and the counts its tabs show.
 *
 * @param tab     which list this is (see {@link AuctionQuery})
 * @param total   rows in the whole list, across pages
 * @param mine    how many listings the viewer has up
 * @param waiting how many items are waiting in the viewer's Collect tab
 */
public record AuctionPage(int tab, List<AuctionRow> rows, int page, int pages, int total, int mine, int waiting) {
    public static final AuctionPage EMPTY = new AuctionPage(AuctionQuery.BROWSE, List.of(), 1, 1, 0, 0, 0);

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionPage> STREAM_CODEC = StreamCodec.of(AuctionPage::write, AuctionPage::read);

    private static void write(RegistryFriendlyByteBuf buffer, AuctionPage page) {
        buffer.writeVarInt(page.tab);
        buffer.writeVarInt(page.rows.size());
        for (AuctionRow row : page.rows) {
            AuctionRow.STREAM_CODEC.encode(buffer, row);
        }
        buffer.writeVarInt(page.page);
        buffer.writeVarInt(page.pages);
        buffer.writeVarInt(page.total);
        buffer.writeVarInt(page.mine);
        buffer.writeVarInt(page.waiting);
    }

    private static AuctionPage read(RegistryFriendlyByteBuf buffer) {
        int tab = buffer.readVarInt();
        int count = Math.min(buffer.readVarInt(), AuctionQuery.PAGE_SIZE);
        List<AuctionRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(AuctionRow.STREAM_CODEC.decode(buffer));
        }
        return new AuctionPage(tab, List.copyOf(rows), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt());
    }
}
