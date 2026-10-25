package com.reiko.economy.network;

import com.reiko.economy.ReikoEconomy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: buy from, or sell to, the shop whose screen is open. It names the open container rather
 * than a block position, so a player can only trade with a shop they actually opened and are still near.
 * {@code expectedPrice} is the price the player saw; the server rejects the trade if it has changed since.
 */
public record ShopTradePayload(int containerId, int action, int bundles, long expectedPrice) implements CustomPacketPayload {
    public static final int BUY = 0;
    public static final int SELL = 1;

    public static final Type<ShopTradePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, "shop_trade"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShopTradePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShopTradePayload::containerId,
            ByteBufCodecs.VAR_INT, ShopTradePayload::action,
            ByteBufCodecs.VAR_INT, ShopTradePayload::bundles,
            ByteBufCodecs.VAR_LONG, ShopTradePayload::expectedPrice,
            ShopTradePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
