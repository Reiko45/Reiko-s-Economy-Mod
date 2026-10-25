package com.reiko.economy.shop;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * How a buyer wants to pay: by card (with its PIN) or in cash from their inventory. A bank balance or credit
 * can only be spent through a card.
 *
 * @param cardId which of the buyer's cards pays, debit or credit; null means the first card they carry
 */
public record Payment(int method, String pin, @Nullable UUID cardId) {
    public static final int CARD = 0;
    public static final int CASH = 1;

    public static final StreamCodec<RegistryFriendlyByteBuf, Payment> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Payment::method,
            ByteBufCodecs.stringUtf8(8), Payment::pin,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), payment -> Optional.ofNullable(payment.cardId()),
            (method, pin, cardId) -> new Payment(method, pin, cardId.orElse(null)));

    /** Pays with the first card the buyer carries. */
    public static Payment card(String pin) {
        return new Payment(CARD, pin, null);
    }

    /** Pays with a particular card the buyer carries. */
    public static Payment card(String pin, @Nullable UUID cardId) {
        return new Payment(CARD, pin, cardId);
    }

    public static Payment cash() {
        return new Payment(CASH, "", null);
    }
}
