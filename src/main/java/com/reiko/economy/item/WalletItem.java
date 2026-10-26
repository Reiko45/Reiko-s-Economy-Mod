package com.reiko.economy.item;

import com.reiko.economy.bank.Purse;
import com.reiko.economy.core.Denomination;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.menu.WalletMenu;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A wallet: holds any number of coins and bills in one inventory slot. Shops, villagers and ATMs pay from it
 * and put change into it, and cash picked up off the ground goes straight in.
 *
 * <p>Put cash in by clicking it onto the wallet in your inventory, or right-clicking cash with the wallet held
 * on the cursor (like a bundle). Right-click the wallet in your hand to open it and take cash out.
 */
public final class WalletItem extends Item {
    private static final int TOOLTIP_LINES = 6;

    public WalletItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack wallet = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new WalletMenu(id, inventory, hand), wallet.getHoverName()),
                    buffer -> buffer.writeEnum(hand));
        }
        return InteractionResultHolder.sidedSuccess(wallet, level.isClientSide());
    }

    /** Cash on the cursor clicked onto the wallet goes in. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack wallet, ItemStack other, Slot slot, ClickAction action, Player player,
                                            SlotAccess access) {
        if (wallet.getCount() != 1 || !(other.getItem() instanceof CashItem cash) || !slot.allowModification(player)) {
            return false;
        }
        Purse.setContents(wallet, Purse.contents(wallet).plus(cash.denomination(), other.getCount()));
        other.setCount(0);
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
        return true;
    }

    /** Right-clicking cash with the wallet on the cursor puts that cash in. */
    @Override
    public boolean overrideStackedOnOther(ItemStack wallet, Slot slot, ClickAction action, Player player) {
        if (wallet.getCount() != 1 || action != ClickAction.SECONDARY || !(slot.getItem().getItem() instanceof CashItem cash)
                || !slot.allowModification(player)) {
            return false;
        }
        ItemStack taken = slot.safeTake(slot.getItem().getCount(), slot.getItem().getCount(), player);
        Purse.setContents(wallet, Purse.contents(wallet).plus(cash.denomination(), taken.getCount()));
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        WalletContents contents = Purse.contents(stack);
        if (contents.isEmpty()) {
            tooltip.add(Component.translatable("item.reikoeconomy.wallet.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.reikoeconomy.wallet.holds", EconomyService.INSTANCE.format(contents.value()))
                .withStyle(ChatFormatting.GOLD));
        int lines = 0;
        Denomination[] all = Denomination.values();
        for (int i = all.length - 1; i >= 0; i--) {
            int count = contents.count(all[i]);
            if (count == 0) {
                continue;
            }
            if (lines++ == TOOLTIP_LINES) {
                tooltip.add(Component.literal("...").withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            tooltip.add(Component.translatable("item.reikoeconomy.wallet.line", count,
                    Component.translatable("item.reikoeconomy." + all[i].id())).withStyle(ChatFormatting.GRAY));
        }
    }
}
