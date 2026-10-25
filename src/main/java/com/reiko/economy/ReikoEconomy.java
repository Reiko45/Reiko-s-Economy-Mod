package com.reiko.economy;

import com.mojang.logging.LogUtils;
import com.reiko.economy.config.EconomyConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Entry point for Reiko's Economy Mod.
 *
 * <p>Game-event listeners live in {@code com.reiko.economy.event} and register themselves with
 * {@code @EventBusSubscriber}, so this class only wires up things that need the mod event bus or the
 * mod container (configs now; registries such as items and blocks in later phases).
 */
@Mod(ReikoEconomy.MODID)
public final class ReikoEconomy {
    public static final String MODID = "reikoeconomy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ReikoEconomy(IEventBus modEventBus, ModContainer modContainer) {
        // SERVER configs load when a world starts. The file lives in config/, and a copy in a world's
        // serverconfig/ folder overrides it, so one world can have its own currency symbol or starting balance.
        modContainer.registerConfig(ModConfig.Type.SERVER, EconomyConfig.SPEC);
    }
}
