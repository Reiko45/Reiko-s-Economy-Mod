package com.reiko.economy.shop;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.core.EconomyService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * News for shop owners. Online owners hear about a shop selling out or filling up straight away; offline
 * owners get it when they next join, together with what their shops earned and spent while they were away.
 * Saved with the overworld in {@code data/reikoeconomy_shop_notices.dat}.
 */
public final class ShopNotices extends SavedData {
    public static final String DATA_NAME = ReikoEconomy.MODID + "_shop_notices";
    private static final int MAX_ALERTS = 10;

    private final Map<UUID, Away> owners = new HashMap<>();

    /** What happened to one owner's shops while they were offline. */
    private static final class Away {
        private long earned;
        private int sales;
        private long spent;
        private int purchases;
        private final List<Component> alerts = new ArrayList<>();
    }

    public static ShopNotices get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(ShopNotices::new, ShopNotices::load), DATA_NAME);
    }

    /** A sale at one of the owner's shops. Only remembered if they're offline (online owners get a message). */
    public static void sale(MinecraftServer server, UUID owner, long amount) {
        if (server.getPlayerList().getPlayer(owner) == null) {
            ShopNotices notices = get(server);
            Away away = notices.owners.computeIfAbsent(owner, id -> new Away());
            away.earned += amount;
            away.sales++;
            notices.setDirty();
        }
    }

    /** One of the owner's shops bought from a player. Only remembered if they're offline. */
    public static void purchase(MinecraftServer server, UUID owner, long amount) {
        if (server.getPlayerList().getPlayer(owner) == null) {
            ShopNotices notices = get(server);
            Away away = notices.owners.computeIfAbsent(owner, id -> new Away());
            away.spent += amount;
            away.purchases++;
            notices.setDirty();
        }
    }

    /** Tells the owner now if they're online, or when they next join. The same alert is only kept once. */
    public static void alert(MinecraftServer server, UUID owner, Component message) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            player.sendSystemMessage(message.copy().withStyle(ChatFormatting.GOLD));
            return;
        }
        ShopNotices notices = get(server);
        List<Component> alerts = notices.owners.computeIfAbsent(owner, id -> new Away()).alerts;
        if (alerts.stream().noneMatch(existing -> existing.getString().equals(message.getString()))) {
            alerts.add(message);
            while (alerts.size() > MAX_ALERTS) {
                alerts.remove(0);
            }
            notices.setDirty();
        }
    }

    /** What's waiting for an owner who is offline, if anything. */
    public static Optional<Pending> pending(MinecraftServer server, UUID owner) {
        Away away = get(server).owners.get(owner);
        return away == null ? Optional.empty()
                : Optional.of(new Pending(away.earned, away.sales, away.spent, away.purchases, List.copyOf(away.alerts)));
    }

    /** A copy of what's waiting for an offline owner. */
    public record Pending(long earned, int sales, long spent, int purchases, List<Component> alerts) {
    }

    /** Gives a player who just joined everything that happened to their shops while they were away. */
    public static void deliver(ServerPlayer player) {
        ShopNotices notices = get(player.server);
        Away away = notices.owners.remove(player.getUUID());
        if (away == null) {
            return;
        }
        notices.setDirty();
        EconomyService eco = EconomyService.INSTANCE;
        if (away.sales > 0) {
            player.sendSystemMessage((away.sales == 1
                    ? Component.translatable("shop.reikoeconomy.notice.earned_one", eco.format(away.earned))
                    : Component.translatable("shop.reikoeconomy.notice.earned", eco.format(away.earned), away.sales))
                    .withStyle(ChatFormatting.GOLD));
        }
        if (away.purchases > 0) {
            player.sendSystemMessage((away.purchases == 1
                    ? Component.translatable("shop.reikoeconomy.notice.spent_one", eco.format(away.spent))
                    : Component.translatable("shop.reikoeconomy.notice.spent", eco.format(away.spent), away.purchases))
                    .withStyle(ChatFormatting.GOLD));
        }
        for (Component alert : away.alerts) {
            player.sendSystemMessage(alert.copy().withStyle(ChatFormatting.GOLD));
        }
    }

    public static ShopNotices load(CompoundTag tag, HolderLookup.Provider registries) {
        ShopNotices notices = new ShopNotices();
        ListTag list = tag.getList("owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("owner")) {
                continue;
            }
            Away away = new Away();
            away.earned = entry.getLong("earned");
            away.sales = entry.getInt("sales");
            away.spent = entry.getLong("spent");
            away.purchases = entry.getInt("purchases");
            ListTag alerts = entry.getList("alerts", Tag.TAG_COMPOUND);
            for (int j = 0; j < alerts.size(); j++) {
                ComponentSerialization.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), alerts.getCompound(j).get("text"))
                        .result().ifPresent(away.alerts::add);
            }
            notices.owners.put(entry.getUUID("owner"), away);
        }
        return notices;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        owners.forEach((owner, away) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("owner", owner);
            entry.putLong("earned", away.earned);
            entry.putInt("sales", away.sales);
            entry.putLong("spent", away.spent);
            entry.putInt("purchases", away.purchases);
            ListTag alerts = new ListTag();
            for (Component alert : away.alerts) {
                CompoundTag saved = new CompoundTag();
                ComponentSerialization.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), alert)
                        .result().ifPresent(text -> saved.put("text", text));
                alerts.add(saved);
            }
            entry.put("alerts", alerts);
            list.add(entry);
        });
        tag.put("owners", list);
        return tag;
    }
}
