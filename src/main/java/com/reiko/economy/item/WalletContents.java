package com.reiko.economy.item;

import com.mojang.serialization.Codec;
import com.reiko.economy.core.Denomination;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The coins and bills inside a wallet: how many of each. Immutable, like every data component; the "plus"
 * methods return a new value.
 */
public record WalletContents(Map<Denomination, Integer> pieces) {
    public static final WalletContents EMPTY = new WalletContents(Map.of());

    /** Saved as {"bill_20": 3, "coin_25": 2}, so reordering the denominations never mixes up saved wallets. */
    public static final Codec<WalletContents> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(
            saved -> {
                Map<Denomination, Integer> pieces = new EnumMap<>(Denomination.class);
                for (Denomination denomination : Denomination.values()) {
                    Integer count = saved.get(denomination.id());
                    if (count != null) {
                        pieces.put(denomination, count);
                    }
                }
                return new WalletContents(pieces);
            },
            contents -> {
                Map<String, Integer> saved = new HashMap<>();
                contents.pieces().forEach((denomination, count) -> saved.put(denomination.id(), count));
                return saved;
            });

    public static final StreamCodec<RegistryFriendlyByteBuf, WalletContents> STREAM_CODEC =
            ByteBufCodecs.<RegistryFriendlyByteBuf, Integer, Integer, Map<Integer, Integer>>map(HashMap::new, ByteBufCodecs.VAR_INT, ByteBufCodecs.VAR_INT)
                    .map(byOrdinal -> {
                        Map<Denomination, Integer> pieces = new EnumMap<>(Denomination.class);
                        Denomination[] all = Denomination.values();
                        byOrdinal.forEach((ordinal, count) -> {
                            if (ordinal >= 0 && ordinal < all.length) {
                                pieces.put(all[ordinal], count);
                            }
                        });
                        return new WalletContents(pieces);
                    }, contents -> {
                        Map<Integer, Integer> byOrdinal = new HashMap<>();
                        contents.pieces().forEach((denomination, count) -> byOrdinal.put(denomination.ordinal(), count));
                        return byOrdinal;
                    });

    public WalletContents {
        Map<Denomination, Integer> copy = new EnumMap<>(Denomination.class);
        pieces.forEach((denomination, count) -> {
            if (count != null && count > 0) {
                copy.put(denomination, count);
            }
        });
        pieces = Collections.unmodifiableMap(copy);
    }

    public int count(Denomination denomination) {
        return pieces.getOrDefault(denomination, 0);
    }

    /** What all the coins and bills are worth, in stored units. */
    public long value() {
        long total = 0;
        for (Map.Entry<Denomination, Integer> piece : pieces.entrySet()) {
            total += piece.getKey().cents() * piece.getValue();
        }
        return total;
    }

    public boolean isEmpty() {
        return pieces.isEmpty();
    }

    /** With {@code change} more (or fewer, if negative) of one denomination. Never goes below zero. */
    public WalletContents plus(Denomination denomination, long change) {
        Map<Denomination, Integer> next = new EnumMap<>(Denomination.class);
        next.putAll(pieces);
        long count = Math.max(0, Math.min(Integer.MAX_VALUE, (long) count(denomination) + change));
        next.put(denomination, (int) count);
        return new WalletContents(next);
    }

    /** With all of {@code more} added. */
    public WalletContents plus(Map<Denomination, ? extends Number> more) {
        WalletContents result = this;
        for (Map.Entry<Denomination, ? extends Number> piece : more.entrySet()) {
            result = result.plus(piece.getKey(), piece.getValue().longValue());
        }
        return result;
    }
}
