package com.reiko.economy.network;

import com.reiko.economy.ReikoEconomy;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: do something on the open auction screen.
 *
 * @param listing the listing to buy, bid on or cancel
 * @param amount  the price seen (buy), the bid, or the asking price (list)
 * @param hours   for listing: 0 for buy-now, or the auction's length
 */
public record AuctionActionPayload(int containerId, int action, Optional<UUID> listing, long amount, int hours)
        implements CustomPacketPayload {
    public static final int BUY = 0;
    public static final int BID = 1;
    public static final int CANCEL = 2;
    public static final int LIST = 3;
    public static final int COLLECT = 4;

    public static final Type<AuctionActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, "auction_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AuctionActionPayload::containerId,
            ByteBufCodecs.VAR_INT, AuctionActionPayload::action,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), AuctionActionPayload::listing,
            ByteBufCodecs.VAR_LONG, AuctionActionPayload::amount,
            ByteBufCodecs.VAR_INT, AuctionActionPayload::hours,
            AuctionActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
