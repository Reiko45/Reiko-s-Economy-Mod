package com.reiko.economy.network;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.auction.AuctionQuery;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: show this tab, search, sort and page of the open auction screen. */
public record AuctionQueryPayload(int containerId, AuctionQuery query) implements CustomPacketPayload {
    public static final Type<AuctionQueryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, "auction_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionQueryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AuctionQueryPayload::containerId,
            AuctionQuery.STREAM_CODEC, AuctionQueryPayload::query,
            AuctionQueryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
