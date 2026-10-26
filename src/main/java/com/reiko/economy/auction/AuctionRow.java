package com.reiko.economy.auction;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * One row of the auction screen, as the client sees it: a listing, or an item waiting to be collected.
 *
 * @param id          the listing, or null for an item waiting to be collected
 * @param price       buy-now price, or the auction's current price (highest bid, or starting bid)
 * @param minimumBid  the lowest bid allowed now (auctions)
 * @param bidder      the highest bidder's name, or "" (auctions)
 * @param millisLeft  time left when the row was sent
 * @param own         the viewer is the seller
 * @param leading     the viewer is the highest bidder
 */
public record AuctionRow(@Nullable UUID id, ItemStack item, String seller, boolean auction, long price, long minimumBid,
                         String bidder, long millisLeft, boolean own, boolean leading) {

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionRow> STREAM_CODEC = StreamCodec.of(AuctionRow::write, AuctionRow::read);

    private static void write(RegistryFriendlyByteBuf buffer, AuctionRow row) {
        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buffer, Optional.ofNullable(row.id));
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, row.item);
        buffer.writeUtf(row.seller, 64);
        buffer.writeBoolean(row.auction);
        buffer.writeVarLong(row.price);
        buffer.writeVarLong(row.minimumBid);
        buffer.writeUtf(row.bidder, 64);
        buffer.writeVarLong(row.millisLeft);
        buffer.writeBoolean(row.own);
        buffer.writeBoolean(row.leading);
    }

    private static AuctionRow read(RegistryFriendlyByteBuf buffer) {
        UUID id = ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buffer).orElse(null);
        return new AuctionRow(id, ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readUtf(64), buffer.readBoolean(),
                buffer.readVarLong(), buffer.readVarLong(), buffer.readUtf(64), buffer.readVarLong(), buffer.readBoolean(),
                buffer.readBoolean());
    }
}
