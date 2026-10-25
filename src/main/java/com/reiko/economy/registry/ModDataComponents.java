package com.reiko.economy.registry;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.item.BankCard;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ReikoEconomy.MODID);

    /** Which account and card a debit card item belongs to. Saved with the item and synced to clients. */
    public static final Supplier<DataComponentType<BankCard>> DEBIT_CARD = COMPONENTS.registerComponentType("debit_card",
            builder -> builder.persistent(BankCard.CODEC).networkSynchronized(BankCard.STREAM_CODEC));
    /** Which credit account and card a credit card item belongs to. */
    public static final Supplier<DataComponentType<BankCard>> CREDIT_CARD = COMPONENTS.registerComponentType("credit_card",
            builder -> builder.persistent(BankCard.CODEC).networkSynchronized(BankCard.STREAM_CODEC));

    private ModDataComponents() {
    }
}
