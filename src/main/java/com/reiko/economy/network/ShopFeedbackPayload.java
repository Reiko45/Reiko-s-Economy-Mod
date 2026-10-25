package com.reiko.economy.network;

import com.reiko.economy.ReikoEconomy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: the result of a shop action, shown inside the open shop screen. */
public record ShopFeedbackPayload(boolean success, Component message) implements CustomPacketPayload {
    public static final Type<ShopFeedbackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, "shop_feedback"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShopFeedbackPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ShopFeedbackPayload::success,
            ComponentSerialization.TRUSTED_STREAM_CODEC, ShopFeedbackPayload::message,
            ShopFeedbackPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
