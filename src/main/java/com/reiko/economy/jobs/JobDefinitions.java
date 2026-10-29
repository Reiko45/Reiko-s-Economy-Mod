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
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
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
 *   "breed": [ { "entity": "minecraft:cow", "pay": 0.50, "xp": 5 } ],
 *   "work":  [ { "action": "reikocars:taxi_ride", "label": "Taxi ride", "pay": 2.00, "xp": 10 } ],
 *   "contracts": [ { "deliver": "minecraft:wheat", "count": [32, 64], "pay": 0.35, "xp": 2 },
 *                  { "break": "#c:ores/iron", "label": "Iron Ore", "count": [12, 24], "pay": 2.00, "xp": 6 },
 *                  { "work": "reikocars:taxi_ride", "label": "Taxi Rides", "icon": "reikocars:taxi", "count": [3, 6], "pay": 3.00 } ]
 * }
 * }</pre>
 *
 * Targets are an id, a {@code #tag}, or for mobs {@code category:<name>}. Lines for blocks, mobs or items that
 * aren't installed are skipped, so job files can mention other mods. {@code work} lines name an action another
 * mod reports (see {@link com.reiko.economy.api.JobsApi}); they're skipped when that mod isn't loaded. Any line
 * can have a {@code label}, and a contract line an {@code icon} item. Blocks a player placed never pay, except
 * lines marked {@code "mature": true}, which only pay for fully grown crops.
 *
 * <p>{@code contracts} lists the daily contracts the job can give out (see {@link ContractService}): one of
 * {@code deliver}, {@code break}, {@code kill}, {@code fish} or {@code breed} with its target, a count or
 * {@code [min, max]} range, and {@code pay} and {@code xp} per unit. An optional {@code label} names the target.
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

    /** Reads one job file. Public for game tests, which make up their own jobs. */
    public static JobDefinition parse(ResourceLocation id, JsonObject json) {
        String name = json.has("name") ? json.get("name").getAsString() : capitalize(id.getPath());
        Item icon = json.has("icon")
                ? BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(json.get("icon").getAsString())).orElse(Items.PAPER)
                : Items.PAPER;
        int order = json.has("order") ? json.get("order").getAsInt() : 100;
        return new JobDefinition(id, order, icon, name,
                rewards(id, json, "break", "block", JobDefinitions::blockMatcher),
                rewards(id, json, "kill", "entity", JobDefinitions::entityMatcher),
                rewards(id, json, "fish", "item", JobDefinitions::itemMatcher),
                rewards(id, json, "breed", "entity", JobDefinitions::entityMatcher),
                rewards(id, json, "work", "action", JobDefinitions::actionMatcher),
                contracts(id, json));
    }

    /**
     * The job's {@code "contracts"} list. Each line names one kind of work and its target, like
     * {@code {"deliver": "minecraft:wheat", "count": [32, 64], "pay": 0.35, "xp": 2}} or
     * {@code {"break": "#c:ores/iron", "label": "Iron Ore", "count": [12, 24], "pay": 2.00}}. {@code pay} and
     * {@code xp} are per unit. {@code label} names a tag target better than its first entry would. Lines whose
     * target isn't installed are skipped.
     */
    private static List<ContractLine> contracts(ResourceLocation job, JsonObject json) {
        List<ContractLine> lines = new ArrayList<>();
        if (!json.has("contracts")) {
            return lines;
        }
        for (JsonElement element : json.getAsJsonArray("contracts")) {
            JsonObject line = element.getAsJsonObject();
            ContractKind kind = null;
            for (ContractKind candidate : ContractKind.values()) {
                if (line.has(candidate.key())) {
                    kind = candidate;
                }
            }
            if (kind == null) {
                ReikoEconomy.LOGGER.warn("Job {}: a contract needs one of deliver, break, kill, fish, breed or work", job);
                continue;
            }
            String target = line.get(kind.key()).getAsString();
            Target<?> parsed = switch (kind) {
                case BREAK -> blockMatcher(target);
                case KILL, BREED -> entityMatcher(target);
                case DELIVER, FISH -> itemMatcher(target);
                case WORK -> actionMatcher(target);
            };
            if (parsed == null) {
                ReikoEconomy.LOGGER.debug("Job {}: skipping contract for '{}', which isn't installed", job, target);
                continue;
            }
            int min;
            int max;
            JsonElement count = line.get("count");
            if (count != null && count.isJsonArray()) {
                min = count.getAsJsonArray().get(0).getAsInt();
                max = count.getAsJsonArray().get(1).getAsInt();
            } else {
                min = max = count == null ? 1 : count.getAsInt();
            }
            min = Math.max(1, min);
            max = Math.max(min, max);
            @SuppressWarnings("unchecked")
            Predicate<Object> matches = (Predicate<Object>) (Predicate<?>) parsed.matches();
            String labelText = line.has("label") ? line.get("label").getAsString() : null;
            Supplier<Component> fixedLabel = labelText == null ? parsed.label()
                    : () -> Component.translatableWithFallback(labelText, labelText);
            Supplier<ItemStack> icon = line.has("icon") ? iconItem(line.get("icon").getAsString()) : iconFor(kind, target);
            lines.add(new ContractLine(kind, matches, fixedLabel, icon, min, max,
                    Math.max(0, line.get("pay").getAsDouble() * 100), line.has("xp") ? Math.max(0, line.get("xp").getAsInt()) : 1,
                    line.has("mature") && line.get("mature").getAsBoolean()));
        }
        return List.copyOf(lines);
    }

    /** A contract line's own {@code "icon"}: an item id, or nothing (the job's icon) if it isn't installed. */
    private static Supplier<ItemStack> iconItem(String id) {
        return () -> BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    /** The item shown next to a contract: the item or block itself, a mob's spawn egg, or a tag's first entry. */
    private static Supplier<ItemStack> iconFor(ContractKind kind, String target) {
        return () -> {
            if (kind == ContractKind.WORK) {
                return ItemStack.EMPTY; // the job's icon
            }
            if (target.startsWith("#")) {
                ResourceLocation id = ResourceLocation.parse(target.substring(1));
                return switch (kind) {
                    case BREAK -> first(BuiltInRegistries.BLOCK, TagKey.create(Registries.BLOCK, id)).map(ItemStack::new).orElse(ItemStack.EMPTY);
                    case DELIVER, FISH -> first(BuiltInRegistries.ITEM, TagKey.create(Registries.ITEM, id)).map(ItemStack::new).orElse(ItemStack.EMPTY);
                    default -> ItemStack.EMPTY;
                };
            }
            if (target.startsWith("category:")) {
                return ItemStack.EMPTY;
            }
            ResourceLocation id = ResourceLocation.parse(target);
            return switch (kind) {
                case BREAK -> BuiltInRegistries.BLOCK.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
                case DELIVER, FISH -> BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
                case KILL, BREED -> BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                        .map(type -> SpawnEggItem.byId(type))
                        .map(egg -> egg == null ? ItemStack.EMPTY : new ItemStack(egg)).orElse(ItemStack.EMPTY);
                case WORK -> ItemStack.EMPTY;
            };
        };
    }

    private static <T> Optional<T> first(Registry<T> registry, TagKey<T> tag) {
        return registry.getTag(tag).flatMap(set -> set.stream().findFirst()).map(Holder::value);
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
            String labelText = line.has("label") ? line.get("label").getAsString() : null;
            Supplier<Component> label = labelText == null ? parsed.label() : () -> Component.translatableWithFallback(labelText, labelText);
            rewards.add(new JobReward<>(parsed.matches(), label, Math.max(0, pay * 100), Math.max(0, xp), mature));
        }
        return List.copyOf(rewards);
    }

    /**
     * Another mod's action, like {@code reikocars:taxi_ride}, or null when that mod isn't loaded. Named by the lang
     * entry {@code job_action.<namespace>.<path>} if there is one, else by the id's path ("Taxi ride").
     */
    @Nullable
    private static Target<ResourceLocation> actionMatcher(String target) {
        ResourceLocation action = ResourceLocation.parse(target);
        if (!action.getNamespace().equals("minecraft") && !ModList.get().isLoaded(action.getNamespace())) {
            return null;
        }
        return new Target<>(action::equals, () -> Component.translatableWithFallback(
                "job_action." + action.getNamespace() + "." + action.getPath(), capitalize(action.getPath())));
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
