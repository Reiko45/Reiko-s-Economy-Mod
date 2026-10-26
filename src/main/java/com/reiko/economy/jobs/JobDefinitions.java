package com.reiko.economy.jobs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.reiko.economy.ReikoEconomy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Loads jobs from datapacks: every {@code data/<namespace>/jobs/<name>.json}. A job file looks like this:
 *
 * <pre>{@code
 * {
 *   "name": "Miner",            // shown if the lang file has no job.<namespace>.<name> entry
 *   "icon": "minecraft:iron_pickaxe",
 *   "order": 1,                 // position on the job board
 *   "break": [ { "block": "#c:ores/diamond", "pay": 5.00, "xp": 25 },
 *              { "block": "minecraft:wheat", "pay": 0.10, "xp": 2, "mature": true } ],
 *   "kill":  [ { "entity": "minecraft:creeper", "pay": 1.50, "xp": 10 },
 *              { "entity": "category:monster", "pay": 0.75, "xp": 5 } ],
 *   "fish":  [ { "item": "minecraft:cod", "pay": 0.50, "xp": 5 } ],
 *   "breed": [ { "entity": "minecraft:cow", "pay": 0.50, "xp": 5 } ]
 * }
 * }</pre>
 *
 * Targets are an id, a {@code #tag}, or for mobs {@code category:<name>}. Lines for blocks, mobs or items that
 * aren't installed are skipped, so job files can mention other mods. Blocks a player placed never pay, except
 * lines marked {@code "mature": true}, which only pay for fully grown crops.
 */
public final class JobDefinitions extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "jobs";
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<ResourceLocation, JobDefinition> jobs = Map.of();

    public JobDefinitions() {
        super(GSON, DIRECTORY);
    }

    /** Every loaded job, in board order. */
    public static Collection<JobDefinition> all() {
        return jobs.values();
    }

    public static Optional<JobDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<JobDefinition> loaded = new ArrayList<>();
        files.forEach((id, json) -> {
            try {
                loaded.add(parse(id, json.getAsJsonObject()));
            } catch (RuntimeException e) {
                ReikoEconomy.LOGGER.error("Skipping job file {}: {}", id, e.getMessage());
            }
        });
        loaded.sort(Comparator.comparingInt(JobDefinition::order).thenComparing(job -> job.id().toString()));
        Map<ResourceLocation, JobDefinition> byId = new LinkedHashMap<>();
        for (JobDefinition job : loaded) {
            byId.put(job.id(), job);
        }
        jobs = Collections.unmodifiableMap(byId);
        ReikoEconomy.LOGGER.info("Loaded {} jobs", byId.size());
    }

    /** Replaces the loaded jobs. For tests. */
    public static void set(Collection<JobDefinition> definitions) {
        Map<ResourceLocation, JobDefinition> byId = new LinkedHashMap<>();
        definitions.forEach(job -> byId.put(job.id(), job));
        jobs = Collections.unmodifiableMap(byId);
    }

    static JobDefinition parse(ResourceLocation id, JsonObject json) {
        String name = json.has("name") ? json.get("name").getAsString() : capitalize(id.getPath());
        Item icon = json.has("icon")
                ? BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(json.get("icon").getAsString())).orElse(Items.PAPER)
                : Items.PAPER;
        int order = json.has("order") ? json.get("order").getAsInt() : 100;
        return new JobDefinition(id, order, icon, name,
                rewards(id, json, "break", "block", JobDefinitions::blockMatcher),
                rewards(id, json, "kill", "entity", JobDefinitions::entityMatcher),
                rewards(id, json, "fish", "item", JobDefinitions::itemMatcher),
                rewards(id, json, "breed", "entity", JobDefinitions::entityMatcher));
    }

    /** A matcher and its label, or null when the target isn't installed. */
    private record Target<T>(Predicate<T> matches, Supplier<Component> label) {
    }

    private static <T> List<JobReward<T>> rewards(ResourceLocation job, JsonObject json, String list, String targetField,
                                                  Function<String, Target<T>> parseTarget) {
        List<JobReward<T>> rewards = new ArrayList<>();
        if (!json.has(list)) {
            return rewards;
        }
        JsonArray lines = json.getAsJsonArray(list);
        for (JsonElement element : lines) {
            JsonObject line = element.getAsJsonObject();
            String target = line.get(targetField).getAsString();
            Target<T> parsed = parseTarget.apply(target);
            if (parsed == null) {
                ReikoEconomy.LOGGER.debug("Job {}: skipping {} '{}', which isn't installed", job, targetField, target);
                continue;
            }
            double pay = line.get("pay").getAsDouble();
            int xp = line.has("xp") ? line.get("xp").getAsInt() : 0;
            boolean mature = line.has("mature") && line.get("mature").getAsBoolean();
            rewards.add(new JobReward<>(parsed.matches(), parsed.label(), Math.max(0, pay * 100), Math.max(0, xp), mature));
        }
        return List.copyOf(rewards);
    }

    @Nullable
    private static Target<BlockState> blockMatcher(String target) {
        if (target.startsWith("#")) {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, ResourceLocation.parse(target.substring(1)));
            return new Target<>(state -> state.is(tag), () -> tagLabel(BuiltInRegistries.BLOCK, tag, Block::getName, target));
        }
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(target));
        return block.<Target<BlockState>>map(found -> new Target<>(state -> state.is(found), found::getName)).orElse(null);
    }

    @Nullable
    private static Target<Entity> entityMatcher(String target) {
        if (target.startsWith("category:")) {
            String name = target.substring("category:".length()).toLowerCase(Locale.ROOT);
            for (MobCategory category : MobCategory.values()) {
                if (category.getName().equals(name)) {
                    return new Target<>(entity -> entity.getType().getCategory() == category,
                            () -> Component.translatableWithFallback("gui.reikoeconomy.jobs.category." + name, "Any " + name));
                }
            }
            return null;
        }
        if (target.startsWith("#")) {
            TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(target.substring(1)));
            return new Target<>(entity -> entity.getType().is(tag),
                    () -> tagLabel(BuiltInRegistries.ENTITY_TYPE, tag, EntityType::getDescription, target));
        }
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(target));
        return type.<Target<Entity>>map(found -> new Target<>(entity -> entity.getType() == found, found::getDescription)).orElse(null);
    }

    @Nullable
    private static Target<ItemStack> itemMatcher(String target) {
        if (target.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(target.substring(1)));
            return new Target<>(stack -> stack.is(tag), () -> tagLabel(BuiltInRegistries.ITEM, tag, Item::getDescription, target));
        }
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(target));
        return item.<Target<ItemStack>>map(found -> new Target<>(stack -> stack.is(found), found::getDescription)).orElse(null);
    }

    /** Names a tag by its first entry ("Diamond Ore" for #c:ores/diamond), or by the tag itself if it's empty. */
    private static <T> Component tagLabel(Registry<T> registry, TagKey<T> tag, Function<T, Component> name, String fallback) {
        return registry.getTag(tag)
                .flatMap(set -> set.stream().findFirst())
                .map(Holder::value)
                .map(name)
                .orElse(Component.literal(fallback));
    }

    private static String capitalize(String path) {
        String last = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
        return last.isEmpty() ? path : Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }
}
