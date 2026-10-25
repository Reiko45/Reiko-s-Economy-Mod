package com.reiko.economy.event;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.block.entity.AdminShopBlockEntity;
import com.reiko.economy.block.entity.ShopBlockEntity;
import com.reiko.economy.registry.ModBlocks;
import com.reiko.economy.shop.ShopAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Stops players from breaking shops they don't own and from placing shops they aren't allowed to. */
@EventBusSubscriber(modid = ReikoEconomy.MODID)
public final class ShopProtectionEvents {
    private ShopProtectionEvents() {
    }

    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
        if (blockEntity instanceof ShopBlockEntity shop && !ShopAccess.canManage(player, shop)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("shop.reikoeconomy.protected", shop.ownerName()), true);
        } else if (blockEntity instanceof AdminShopBlockEntity && !ShopAccess.isShopAdmin(player)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("shop.reikoeconomy.admin.protected"), true);
        }
    }

    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockState placed = event.getPlacedBlock();
        if (placed.is(ModBlocks.SHOP.get()) && !ShopAccess.canCreateShops(player)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("shop.reikoeconomy.no_permission_place"), true);
        } else if (placed.is(ModBlocks.ADMIN_SHOP.get()) && !ShopAccess.isShopAdmin(player)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("shop.reikoeconomy.admin.no_permission_place"), true);
        }
    }
}
