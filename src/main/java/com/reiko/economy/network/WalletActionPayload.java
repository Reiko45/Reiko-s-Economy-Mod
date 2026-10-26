package com.reiko.economy.network;

import com.reiko.economy.ReikoEconomy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: take cash out of the open wallet. {@code denomination} is the coin or bill's ordinal, or
 * {@code WalletMenu.ALL} to empty the wallet; {@code count} is how many.
 */
public record WalletActionPayload(int containerId, int denomination, int count) implements CustomPacketPayload {
    public static final Type<WalletActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, "wallet_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WalletActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WalletActionPayload::containerId,
            ByteBufCodecs.INT, WalletActionPayload::denomination,
            ByteBufCodecs.VAR_INT, WalletActionPayload::count,
            WalletActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
