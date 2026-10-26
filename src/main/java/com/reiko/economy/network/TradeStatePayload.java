package com.reiko.economy.network;

import com.reiko.economy.ReikoEconomy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the money and accepts in an open trade window.
 *
 * @param myFee      the fee on the money this player offers
 * @param revision   which version of the offers this is; accepting sends it back
 * @param lockMillis how long until anyone can accept, after the last change
 */
public record TradeStatePayload(int containerId, long myMoney, long theirMoney, long myFee, boolean myAccepted,
                                boolean theirAccepted, int revision, int lockMillis) implements CustomPacketPayload {
    public static final TradeStatePayload EMPTY = new TradeStatePayload(-1, 0, 0, 0, false, false, 0, 0);

    public static final Type<TradeStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, "trade_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, state) -> {
                buf.writeVarInt(state.containerId());
                buf.writeVarLong(state.myMoney());
                buf.writeVarLong(state.theirMoney());
                buf.writeVarLong(state.myFee());
                buf.writeBoolean(state.myAccepted());
                buf.writeBoolean(state.theirAccepted());
                buf.writeVarInt(state.revision());
                buf.writeVarInt(state.lockMillis());
            },
            buf -> new TradeStatePayload(buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                    buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
