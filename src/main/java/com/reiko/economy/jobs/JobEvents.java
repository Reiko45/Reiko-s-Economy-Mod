package com.reiko.economy.jobs;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.config.EconomyConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Watches for work that jobs pay for. The handlers run last and skip anything another mod cancelled, so a
 * protected block or a prevented death never pays.
 */
@EventBusSubscriber(modid = ReikoEconomy.MODID)
public final class JobEvents {
    /** Marks mobs that came from a monster spawner. */
    private static final String SPAWNER_TAG = ReikoEconomy.MODID + ":from_spawner";

    private JobEvents() {
    }

    @SubscribeEvent
    static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new JobDefinitions());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getPlayer() instanceof ServerPlayer player && event.getLevel() instanceof Level) {
            JobService.blockBroken(player, event.getState(), event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof Player && event.getLevel() instanceof Level level
                && !level.isClientSide() && JobService.paysForBreaking(event.getPlacedBlock())) {
            PlacedBlocks.markPlaced(level, event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onMobSpawned(FinalizeSpawnEvent event) {
        if (event.getSpawnType() == MobSpawnType.SPAWNER) {
            event.getEntity().getPersistentData().putBoolean(SPAWNER_TAG, true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!EconomyConfig.paySpawnerMobs() && event.getEntity().getPersistentData().getBoolean(SPAWNER_TAG)) {
            return;
        }
        JobService.mobKilled(player, event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onFished(ItemFishedEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            JobService.fishCaught(player, event.getDrops());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBred(BabyEntitySpawnEvent event) {
        if (!event.isCanceled() && event.getCausedByPlayer() instanceof ServerPlayer player) {
            JobService.animalsBred(player, event.getParentA());
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % JobService.DEPOSIT_EVERY_TICKS == 0) {
            JobService.depositAll(event.getServer());
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JobService.deposit(player.server, player.getUUID());
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        JobService.depositAll(event.getServer());
        JobService.clear();
    }
}
