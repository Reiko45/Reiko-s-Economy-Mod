package com.reiko.economy.network;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.shop.Payment;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: make a villager trade from the trading screen that is open, paid in money.
 * {@code expectedNet} is the per-trade price the player saw (negative when the villager pays them); the
 * server refuses the trade if it has changed. {@code paymentMethod} and {@code pin} say how a purchase is paid.
 */
public record VillagerTradePayload(int containerId, int offerIndex, int times, long expectedNet, int paymentMethod, String pin)
        implements CustomPacketPayload {
    public static final Type<VillagerTradePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, "villager_trade"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerTradePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VillagerTradePayload::containerId,
            ByteBufCodecs.VAR_INT, VillagerTradePayload::offerIndex,
            ByteBufCodecs.VAR_INT, VillagerTradePayload::times,
            ByteBufCodecs.VAR_LONG, VillagerTradePayload::expectedNet,
            ByteBufCodecs.VAR_INT, VillagerTradePayload::paymentMethod,
            ByteBufCodecs.stringUtf8(8), VillagerTradePayload::pin,
            VillagerTradePayload::new);

    public Payment payment() {
        return new Payment(paymentMethod, pin);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
