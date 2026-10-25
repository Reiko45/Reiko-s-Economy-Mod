package com.reiko.economy.network;

import com.reiko.economy.ReikoEconomy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: an ATM action on the ATM screen the player has open. {@code amount} is the typed text,
 * parsed on the server, and only used for withdrawals.
 */
public record AtmActionPayload(int containerId, int action, String amount) implements CustomPacketPayload {
    public static final int WITHDRAW = 0;
    public static final int DEPOSIT_SLOTS = 1;
    public static final int DEPOSIT_ALL = 2;

    public static final Type<AtmActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, "atm_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtmActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AtmActionPayload::containerId,
            ByteBufCodecs.VAR_INT, AtmActionPayload::action,
            ByteBufCodecs.stringUtf8(32), AtmActionPayload::amount,
            AtmActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
