package com.reiko.economy.network;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.auction.AuctionPage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: a page of the open auction screen. */
public record AuctionPagePayload(int containerId, AuctionPage page) implements CustomPacketPayload {
    public static final Type<AuctionPagePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, "auction_page"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionPagePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AuctionPagePayload::containerId,
            AuctionPage.STREAM_CODEC, AuctionPagePayload::page,
            AuctionPagePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
