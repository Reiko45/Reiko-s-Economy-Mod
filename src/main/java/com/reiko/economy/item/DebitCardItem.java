package com.reiko.economy.item;

import com.reiko.economy.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/**
 * A debit card, issued by an ATM. It pays from its owner's bank account at shops and opens that account at
 * ATMs, for anyone who knows its PIN.
 */
public final class DebitCardItem extends Item {
    public DebitCardItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Nullable
    public static BankCard cardOf(ItemStack stack) {
        return stack.getItem() instanceof DebitCardItem ? stack.get(ModDataComponents.DEBIT_CARD.get()) : null;
    }

    /**
     * The card a player pays with: the one in their main hand, then their off hand, then the first one in
     * their inventory. Returns {@link ItemStack#EMPTY} if they carry none.
     */
    public static ItemStack findCard(Player player) {
        if (cardOf(player.getMainHandItem()) != null) {
            return player.getMainHandItem();
        }
        if (cardOf(player.getOffhandItem()) != null) {
            return player.getOffhandItem();
        }
        Inventory inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            if (cardOf(stack) != null) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BankCard card = cardOf(stack);
        if (card == null) {
            tooltip.add(Component.translatable("item.reikoeconomy.debit_card.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable(card.shared() ? "item.reikoeconomy.debit_card.shared" : "item.reikoeconomy.debit_card.owner",
                card.ownerName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.reikoeconomy.debit_card.number", card.lastFour()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
