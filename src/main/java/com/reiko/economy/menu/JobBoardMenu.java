package com.reiko.economy.menu;

import com.reiko.economy.jobs.ContractView;
import com.reiko.economy.jobs.JobBoardData;
import com.reiko.economy.jobs.JobView;
import com.reiko.economy.registry.ModBlocks;
import com.reiko.economy.registry.ModMenus;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * The job board's menu. It has no slots: the server sends the jobs, the player's levels and their contracts when
 * it opens and again after each change ({@code JobBoardSyncPayload}).
 */
public final class JobBoardMenu extends ShopMenuBase {
    public static final int WIDTH = 230;
    public static final int HEIGHT = 200;

    private final ContainerLevelAccess access;
    private JobBoardData data;
    /** When the data arrived, for counting down to new contracts. */
    private long receivedAt = System.currentTimeMillis();

    /** Client constructor. */
    public JobBoardMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, ContainerLevelAccess.NULL, JobBoardData.STREAM_CODEC.decode(buffer));
    }

    public JobBoardMenu(int containerId, Inventory inventory, ContainerLevelAccess access, JobBoardData data) {
        super(ModMenus.JOB_BOARD.get(), containerId);
        this.access = access;
        this.data = data;
    }

    public List<JobView> views() {
        return data.views();
    }

    public int maxJobs() {
        return data.maxJobs();
    }

    public long activeCount() {
        return data.views().stream().filter(JobView::active).count();
    }

    public List<ContractView> contracts() {
        return data.contracts();
    }

    public boolean hasJobs() {
        return data.hasJobs();
    }

    /** Seconds until new contracts, counting down while the screen is open. */
    public long secondsToNew() {
        return Math.max(0, data.secondsToNew() - (System.currentTimeMillis() - receivedAt) / 1000);
    }

    /** New data from the server after a change. */
    public void update(JobBoardData data) {
        this.data = data;
        this.receivedAt = System.currentTimeMillis();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.JOB_BOARD.get());
    }
}
