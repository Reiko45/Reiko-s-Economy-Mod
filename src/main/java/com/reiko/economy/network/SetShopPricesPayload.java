package com.reiko.economy.network;

import com.reiko.economy.ReikoEconomy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: save the prices typed into a shop setup screen. The text is parsed on the server, which
 * never trusts the client's idea of an amount. {@code sellPrice} is only used by admin shops.
 */
public record SetShopPricesPayload(int containerId, String price, String sellPrice) implements CustomPacketPayload {
    public static final Type<SetShopPricesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, "set_shop_prices"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetShopPricesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetShopPricesPayload::containerId,
            ByteBufCodecs.stringUtf8(32), SetShopPricesPayload::price,
            ByteBufCodecs.stringUtf8(32), SetShopPricesPayload::sellPrice,
            SetShopPricesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
