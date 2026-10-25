package com.reiko.economy.item;

import com.reiko.economy.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/**
 * A credit card, issued by an ATM. Purchases and cash advances are added to its owner's credit card bill
 * instead of coming out of their bank account, for anyone who knows its PIN.
 */
public final class CreditCardItem extends Item {
    public CreditCardItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Nullable
    public static BankCard cardOf(ItemStack stack) {
        return stack.getItem() instanceof CreditCardItem ? stack.get(ModDataComponents.CREDIT_CARD.get()) : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BankCard card = cardOf(stack);
        if (card == null) {
            tooltip.add(Component.translatable("item.reikoeconomy.credit_card.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.reikoeconomy.credit_card.owner", card.ownerName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.reikoeconomy.debit_card.number", card.lastFour()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
