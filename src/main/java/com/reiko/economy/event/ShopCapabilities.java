package com.reiko.economy.event;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.registry.ModBlockEntities;
import com.reiko.economy.shop.InsertOnlyStock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Lets hoppers and pipes restock player shops. They can only add the shop's item, never take stock out. */
@EventBusSubscriber(modid = ReikoEconomy.MODID)
public final class ShopCapabilities {
    private ShopCapabilities() {
    }

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.SHOP.get(),
                (shop, side) -> new InsertOnlyStock(shop.stock()));
    }
}
