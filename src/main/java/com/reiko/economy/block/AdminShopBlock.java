package com.reiko.economy.block;

import com.mojang.serialization.MapCodec;
import com.reiko.economy.block.entity.AdminShopBlockEntity;
import com.reiko.economy.menu.AdminShopSetupMenu;
import com.reiko.economy.menu.AdminShopTradeMenu;
import com.reiko.economy.shop.ShopAccess;
import com.reiko.economy.shop.ShopDirectory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The server's shop. Everyone gets the trade screen; admins sneak + right-click to set it up (or just
 * right-click while it isn't set up yet).
 */
public final class AdminShopBlock extends BaseEntityBlock {
    public static final MapCodec<AdminShopBlock> CODEC = simpleCodec(AdminShopBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public AdminShopBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<AdminShopBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdminShopBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            ShopDirectory.remove(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level.getBlockEntity(pos) instanceof AdminShopBlockEntity shop)) {
            return InteractionResult.PASS;
        }
        boolean configured = shop.sellsToPlayers() || shop.buysFromPlayers();
        boolean admin = ShopAccess.isShopAdmin(serverPlayer);
        if (admin && (serverPlayer.isShiftKeyDown() || !configured)) {
            AdminShopSetupMenu.open(serverPlayer, shop);
        } else if (!configured) {
            serverPlayer.displayClientMessage(Component.translatable("shop.reikoeconomy.not_set_up"), true);
        } else {
            AdminShopTradeMenu.open(serverPlayer, shop);
        }
        return InteractionResult.CONSUME;
    }
}
