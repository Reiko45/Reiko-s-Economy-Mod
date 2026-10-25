package com.reiko.economy.registry;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.block.AdminShopBlock;
import com.reiko.economy.block.ShopBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ReikoEconomy.MODID);

    /** A player-owned shop. Explosion-proof so griefers can't blow the stock out of it. */
    public static final DeferredBlock<ShopBlock> SHOP = BLOCKS.register("shop", () -> new ShopBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F, 1200.0F).sound(SoundType.WOOD)));

    /** The server's infinite shop. Unbreakable outside creative mode, like bedrock. */
    public static final DeferredBlock<AdminShopBlock> ADMIN_SHOP = BLOCKS.register("admin_shop", () -> new AdminShopBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3_600_000.0F)
                    .sound(SoundType.METAL).noLootTable()));

    private ModBlocks() {
    }
}
