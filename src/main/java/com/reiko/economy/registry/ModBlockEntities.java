package com.reiko.economy.registry;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.block.entity.AdminShopBlockEntity;
import com.reiko.economy.block.entity.ShopBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ReikoEconomy.MODID);

    @SuppressWarnings("DataFlowIssue") // build(null): no DataFixer type for mod block entities
    public static final Supplier<BlockEntityType<ShopBlockEntity>> SHOP = BLOCK_ENTITIES.register("shop",
            () -> BlockEntityType.Builder.of(ShopBlockEntity::new, ModBlocks.SHOP.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<AdminShopBlockEntity>> ADMIN_SHOP = BLOCK_ENTITIES.register("admin_shop",
            () -> BlockEntityType.Builder.of(AdminShopBlockEntity::new, ModBlocks.ADMIN_SHOP.get()).build(null));

    private ModBlockEntities() {
    }
}
