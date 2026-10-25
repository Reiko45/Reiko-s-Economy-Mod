package com.reiko.economy;

import com.mojang.logging.LogUtils;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.registry.ModBlockEntities;
import com.reiko.economy.registry.ModBlocks;
import com.reiko.economy.registry.ModCreativeTabs;
import com.reiko.economy.registry.ModDataComponents;
import com.reiko.economy.registry.ModItems;
import com.reiko.economy.registry.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Entry point for Reiko's Economy Mod.
 *
 * <p>Game-event listeners live in {@code com.reiko.economy.event} and register themselves with
 * {@code @EventBusSubscriber}, so this class only wires up registries and configs.
 */
@Mod(ReikoEconomy.MODID)
public final class ReikoEconomy {
    public static final String MODID = "reikoeconomy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ReikoEconomy(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        // SERVER configs load when a world starts. The file lives in config/, and a copy in a world's
        // serverconfig/ folder overrides it, so one world can have its own currency symbol or starting balance.
        modContainer.registerConfig(ModConfig.Type.SERVER, EconomyConfig.SPEC);
    }
}
