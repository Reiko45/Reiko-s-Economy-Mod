package com.reiko.economy.registry;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.core.Denomination;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ReikoEconomy.MODID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.reikoeconomy"))
            .icon(() -> ModItems.SHOP.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.SHOP.get());
                output.accept(ModItems.ADMIN_SHOP.get());
                output.accept(ModItems.ATM.get());
                output.accept(ModItems.JOB_BOARD.get());
                output.accept(ModItems.WALLET.get());
                for (Denomination denomination : Denomination.values()) {
                    output.accept(ModItems.cash(denomination));
                }
            })
            .build());

    private ModCreativeTabs() {
    }
}
