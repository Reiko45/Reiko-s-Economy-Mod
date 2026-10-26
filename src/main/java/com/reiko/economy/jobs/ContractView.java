package com.reiko.economy.jobs;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * What the job board shows about one of the player's contracts.
 *
 * @param text    "Deliver Wheat × 48"
 * @param reward  money for finishing it, in cents
 * @param deliver true for deliveries, which have a Deliver button
 * @param held    for deliveries: how many matching items the player is carrying
 */
public record ContractView(ItemStack icon, Component text, Component job, int progress, int count, long reward, int xp,
                           boolean deliver, boolean done, int held) {

    public static final StreamCodec<RegistryFriendlyByteBuf, ContractView> STREAM_CODEC = StreamCodec.of(ContractView::write, ContractView::read);
    public static final StreamCodec<RegistryFriendlyByteBuf, List<ContractView>> LIST_CODEC = STREAM_CODEC.apply(ByteBufCodecs.list(16));

    private static void write(RegistryFriendlyByteBuf buffer, ContractView view) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, view.icon);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, view.text);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, view.job);
        buffer.writeVarInt(view.progress);
        buffer.writeVarInt(view.count);
        buffer.writeVarLong(view.reward);
        buffer.writeVarInt(view.xp);
        buffer.writeBoolean(view.deliver);
        buffer.writeBoolean(view.done);
        buffer.writeVarInt(view.held);
    }

    private static ContractView read(RegistryFriendlyByteBuf buffer) {
        return new ContractView(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarLong(),
                buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt());
    }
}
