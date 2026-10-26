package com.reiko.economy.network;

import com.reiko.economy.ReikoEconomy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: do something in the open trade window.
 *
 * @param revision for accepting: the revision of the offers the player saw
 * @param amount   for setting money: what the player typed
 */
public record TradeActionPayload(int containerId, int action, int revision, String amount) implements CustomPacketPayload {
    public static final int MONEY = 0;
    public static final int ACCEPT = 1;
    public static final int UNACCEPT = 2;

    public static final Type<TradeActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, "trade_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TradeActionPayload::containerId,
            ByteBufCodecs.VAR_INT, TradeActionPayload::action,
            ByteBufCodecs.VAR_INT, TradeActionPayload::revision,
            ByteBufCodecs.stringUtf8(32), TradeActionPayload::amount,
            TradeActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
