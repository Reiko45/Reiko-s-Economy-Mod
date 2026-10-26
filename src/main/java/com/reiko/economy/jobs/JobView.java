package com.reiko.economy.jobs;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * What the job board shows about one job for one player: its name and pay examples (at the player's level),
 * their level and XP in it, and whether they have it.
 *
 * @param xpToNext 0 at the highest level
 * @param bonus    the pay bonus at their level, like "+6%"
 */
public record JobView(ResourceLocation id, Component name, Component description, ItemStack icon, List<Component> examples,
                      int level, long xp, long xpToNext, boolean active, String bonus) {

    public static final StreamCodec<RegistryFriendlyByteBuf, JobView> STREAM_CODEC = StreamCodec.of(JobView::write, JobView::read);
    public static final StreamCodec<RegistryFriendlyByteBuf, List<JobView>> LIST_CODEC = STREAM_CODEC.apply(ByteBufCodecs.list());

    private static void write(RegistryFriendlyByteBuf buffer, JobView view) {
        ResourceLocation.STREAM_CODEC.encode(buffer, view.id);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, view.name);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, view.description);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, view.icon);
        buffer.writeVarInt(view.examples.size());
        for (Component example : view.examples) {
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, example);
        }
        buffer.writeVarInt(view.level);
        buffer.writeVarLong(view.xp);
        buffer.writeVarLong(view.xpToNext);
        buffer.writeBoolean(view.active);
        buffer.writeUtf(view.bonus, 32);
    }

    private static JobView read(RegistryFriendlyByteBuf buffer) {
        ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buffer);
        Component name = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
        Component description = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
        ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        int count = Math.min(buffer.readVarInt(), 16);
        List<Component> examples = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            examples.add(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer));
        }
        return new JobView(id, name, description, icon, List.copyOf(examples), buffer.readVarInt(), buffer.readVarLong(),
                buffer.readVarLong(), buffer.readBoolean(), buffer.readUtf(32));
    }
}
