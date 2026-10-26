package com.reiko.economy.block;

import com.mojang.serialization.MapCodec;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.jobs.JobService;
import com.reiko.economy.jobs.JobView;
import com.reiko.economy.menu.JobBoardMenu;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A notice board on two posts. Right-click it to see the jobs, join up to the limit, or quit. */
public final class JobBoardBlock extends Block {
    public static final MapCodec<JobBoardBlock> CODEC = simpleCodec(JobBoardBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape NORTH_SOUTH = Block.box(1, 0, 7, 15, 16, 9);
    private static final VoxelShape EAST_WEST = Block.box(7, 0, 1, 9, 16, 15);

    public JobBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<JobBoardBlock> codec() {
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NORTH_SOUTH : EAST_WEST;
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (!EconomyConfig.jobsEnabled()) {
                serverPlayer.displayClientMessage(Component.translatable("job.reikoeconomy.disabled"), true);
                return InteractionResult.CONSUME;
            }
            List<JobView> views = JobService.views(serverPlayer);
            int maxJobs = EconomyConfig.maxJobs();
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new JobBoardMenu(id, inventory, ContainerLevelAccess.create(level, pos), views, maxJobs),
                    Component.translatable("container.reikoeconomy.job_board")),
                    buffer -> {
                        JobView.LIST_CODEC.encode(buffer, views);
                        buffer.writeVarInt(maxJobs);
                    });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
