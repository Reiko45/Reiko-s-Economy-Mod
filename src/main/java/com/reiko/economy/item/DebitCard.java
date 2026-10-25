package com.reiko.economy.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What's printed on a debit card: whose account it draws from and which card it is. The card holds no money
 * and no PIN; the ledger decides whether this card still works and checks the PIN.
 */
public record DebitCard(UUID account, UUID cardId, String ownerName) {
    public static final Codec<DebitCard> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("account").forGetter(DebitCard::account),
            UUIDUtil.CODEC.fieldOf("card_id").forGetter(DebitCard::cardId),
            Codec.STRING.fieldOf("owner_name").forGetter(DebitCard::ownerName)
    ).apply(instance, DebitCard::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DebitCard> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, DebitCard::account,
            UUIDUtil.STREAM_CODEC, DebitCard::cardId,
            ByteBufCodecs.STRING_UTF8, DebitCard::ownerName,
            DebitCard::new);

    /** The last four digits shown on the card, e.g. "4821". */
    public String lastFour() {
        return String.format("%04d", Math.floorMod(cardId.getLeastSignificantBits(), 10_000L));
    }
}
