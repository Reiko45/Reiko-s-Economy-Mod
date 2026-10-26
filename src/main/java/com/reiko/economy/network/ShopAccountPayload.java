package com.reiko.economy.network;

import com.reiko.economy.ReikoEconomy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: on the shop owner screen, switch which account the shop runs from (the owner's own, or the
 * next shared account they're in).
 */
public record ShopAccountPayload(int containerId) implements CustomPacketPayload {
    public static final Type<ShopAccountPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, "shop_account"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShopAccountPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShopAccountPayload::containerId,
            ShopAccountPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
