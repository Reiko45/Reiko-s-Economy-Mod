package com.reiko.economy.jobs;

import com.reiko.economy.ReikoEconomy;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Data the mod attaches to chunks. */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ReikoEconomy.MODID);

    /** Job-paying blocks players placed in a chunk. */
    public static final Supplier<AttachmentType<PlacedBlocks>> PLACED_BLOCKS = ATTACHMENTS.register("placed_blocks",
            () -> AttachmentType.builder(PlacedBlocks::new).serialize(PlacedBlocks.CODEC).build());

    private ModAttachments() {
    }
}
