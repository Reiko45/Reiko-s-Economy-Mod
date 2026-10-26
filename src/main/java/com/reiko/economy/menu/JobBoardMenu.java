package com.reiko.economy.menu;

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
 * The job board's menu. It has no slots: the server sends the jobs and the player's levels when it opens and
 * again after each join or quit ({@code JobBoardSyncPayload}).
 */
public final class JobBoardMenu extends ShopMenuBase {
    public static final int WIDTH = 230;
    public static final int HEIGHT = 200;

    private final ContainerLevelAccess access;
    private List<JobView> views;
    private int maxJobs;

    /** Client constructor. */
    public JobBoardMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, ContainerLevelAccess.NULL, JobView.LIST_CODEC.decode(buffer), buffer.readVarInt());
    }

    public JobBoardMenu(int containerId, Inventory inventory, ContainerLevelAccess access, List<JobView> views, int maxJobs) {
        super(ModMenus.JOB_BOARD.get(), containerId);
        this.access = access;
        this.views = List.copyOf(views);
        this.maxJobs = maxJobs;
    }

    public List<JobView> views() {
        return views;
    }

    public int maxJobs() {
        return maxJobs;
    }

    public long activeCount() {
        return views.stream().filter(JobView::active).count();
    }

    /** New data from the server after a join or quit. */
    public void update(List<JobView> views, int maxJobs) {
        this.views = List.copyOf(views);
        this.maxJobs = maxJobs;
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
