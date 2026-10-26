package com.reiko.economy.network;

import com.reiko.economy.ReikoEconomy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: hand in items for one of the player's delivery contracts, from the open job board. */
public record JobContractPayload(int containerId, int contract) implements CustomPacketPayload {
    public static final Type<JobContractPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, "job_contract"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JobContractPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, JobContractPayload::containerId,
            ByteBufCodecs.VAR_INT, JobContractPayload::contract,
            JobContractPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
