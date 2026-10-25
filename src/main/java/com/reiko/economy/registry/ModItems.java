package com.reiko.economy.registry;

import com.reiko.economy.ReikoEconomy;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ReikoEconomy.MODID);

    public static final DeferredItem<BlockItem> SHOP = ITEMS.registerSimpleBlockItem("shop", ModBlocks.SHOP);
    public static final DeferredItem<BlockItem> ADMIN_SHOP = ITEMS.registerSimpleBlockItem("admin_shop", ModBlocks.ADMIN_SHOP,
            new Item.Properties().rarity(Rarity.EPIC));

    private ModItems() {
    }
}
