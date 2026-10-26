package com.reiko.economy.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A job, loaded from a {@code data/<namespace>/jobs/<name>.json} file in a datapack. Each list pays for one
 * kind of work; the first matching line in a list wins. {@code contracts} are the daily contracts the job can
 * hand out.
 */
public record JobDefinition(ResourceLocation id, int order, Item icon, String fallbackName,
                            List<JobReward<BlockState>> breaking, List<JobReward<Entity>> killing,
                            List<JobReward<ItemStack>> fishing, List<JobReward<Entity>> breeding,
                            List<ContractLine> contracts) {

    /** "Miner": from the lang file ({@code job.<namespace>.<name>}) if there's an entry, else the job file's name. */
    public Component name() {
        return Component.translatableWithFallback("job." + id.getNamespace() + "." + id.getPath(), fallbackName);
    }

    public Component description() {
        return Component.translatableWithFallback("job." + id.getNamespace() + "." + id.getPath() + ".description", "");
    }

    /** The reason recorded in the transaction log for this job's pay. */
    public String payReason() {
        return "job." + id.getPath();
    }

    public Optional<JobReward<BlockState>> forBreaking(BlockState state) {
        return first(breaking, state);
    }

    public Optional<JobReward<Entity>> forKilling(Entity entity) {
        return first(killing, entity);
    }

    public Optional<JobReward<ItemStack>> forFishing(ItemStack stack) {
        return first(fishing, stack);
    }

    public Optional<JobReward<Entity>> forBreeding(Entity entity) {
        return first(breeding, entity);
    }

    /** The first few lines of the job file, for the job board. */
    public List<JobReward<?>> examples(int count) {
        List<JobReward<?>> all = new ArrayList<>();
        all.addAll(breaking);
        all.addAll(killing);
        all.addAll(fishing);
        all.addAll(breeding);
        return all.subList(0, Math.min(count, all.size()));
    }

    private static <T> Optional<JobReward<T>> first(List<JobReward<T>> rewards, T target) {
        for (JobReward<T> reward : rewards) {
            if (reward.matches().test(target)) {
                return Optional.of(reward);
            }
        }
        return Optional.empty();
    }
}
