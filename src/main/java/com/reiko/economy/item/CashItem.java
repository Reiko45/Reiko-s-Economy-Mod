package com.reiko.economy.item;

import com.reiko.economy.core.Denomination;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A coin or bill. Its value comes from its {@link Denomination}, so a stack of 64 $20 bills is worth $1,280.
 * Cash is only made and destroyed by the ATM, which moves it to and from bank balances.
 */
public final class CashItem extends Item {
    private final Denomination denomination;

    public CashItem(Denomination denomination, Properties properties) {
        super(properties);
        this.denomination = denomination;
    }

    public Denomination denomination() {
        return denomination;
    }

    /** Value of a stack in stored units, or 0 if it isn't cash. */
    public static long valueOf(ItemStack stack) {
        return stack.getItem() instanceof CashItem cash ? cash.denomination.cents() * stack.getCount() : 0;
    }

    public static boolean isCash(ItemStack stack) {
        return stack.getItem() instanceof CashItem;
    }

    /** The fewest coins and bills worth exactly {@code amount}, in stacks of at most 64, largest first. */
    public static List<ItemStack> makeChange(long amount) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<Denomination, Long> piece : Denomination.breakDown(amount).entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getKey().cents(), a.getKey().cents())).toList()) {
            Item item = ModItems.cash(piece.getKey());
            long left = piece.getValue();
            while (left > 0) {
                int count = (int) Math.min(left, item.getDefaultMaxStackSize());
                stacks.add(new ItemStack(item, count));
                left -= count;
            }
        }
        return stacks;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        EconomyService eco = EconomyService.INSTANCE;
        tooltip.add(Component.translatable("item.reikoeconomy.cash.worth", eco.format(denomination.cents()))
                .withStyle(ChatFormatting.GRAY));
        if (stack.getCount() > 1) {
            tooltip.add(Component.translatable("item.reikoeconomy.cash.stack_worth", eco.format(valueOf(stack)))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
