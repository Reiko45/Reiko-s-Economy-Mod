package com.reiko.economy.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What's printed on a debit or credit card: whose account it belongs to and which card it is. The card holds
 * no money and no PIN; the ledger (bank or credit) decides whether this card still works and checks the PIN.
 */
public record BankCard(UUID account, UUID cardId, String ownerName) {
    public static final Codec<BankCard> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("account").forGetter(BankCard::account),
            UUIDUtil.CODEC.fieldOf("card_id").forGetter(BankCard::cardId),
            Codec.STRING.fieldOf("owner_name").forGetter(BankCard::ownerName)
    ).apply(instance, BankCard::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BankCard> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, BankCard::account,
            UUIDUtil.STREAM_CODEC, BankCard::cardId,
            ByteBufCodecs.STRING_UTF8, BankCard::ownerName,
            BankCard::new);

    /** The last four digits shown on the card, e.g. "4821". */
    public String lastFour() {
        return String.format("%04d", Math.floorMod(cardId.getLeastSignificantBits(), 10_000L));
    }
}
