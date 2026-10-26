package com.reiko.economy.event;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.bounty.BountyService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/** Pays out bounties when a player is killed by another, and gives back bounties that ran out. */
@EventBusSubscriber(modid = ReikoEconomy.MODID)
public final class BountyEvents {
    private static final int CHECK_EVERY_TICKS = 100;

    private BountyEvents() {
    }

    /** Lowest priority, so a mod that saves the player from dying gets to cancel first. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer target) {
            ServerPlayer killer = killer(target, event.getSource());
            if (killer != null) {
                BountyService.claim(target, killer);
            }
        }
    }

    /**
     * The player who gets the kill: whoever hit or shot them, the owner of a pet that did, or (for a fall or lava
     * after being hit) whoever hit them last, as vanilla counts kills.
     */
    @Nullable
    static ServerPlayer killer(ServerPlayer target, DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player) {
            return player;
        }
        if (attacker instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        return target.getKillCredit() instanceof ServerPlayer credited ? credited : null;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_EVERY_TICKS == 0) {
            BountyService.tick(event.getServer());
        }
    }
}
