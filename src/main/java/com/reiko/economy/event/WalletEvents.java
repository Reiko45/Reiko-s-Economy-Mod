package com.reiko.economy.event;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.bank.Purse;
import com.reiko.economy.item.CashItem;
import java.util.List;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/** Coins and bills picked up off the ground go straight into the player's wallet, if they carry one. */
@EventBusSubscriber(modid = ReikoEconomy.MODID)
public final class WalletEvents {
    private WalletEvents() {
    }

    @SubscribeEvent
    static void onPickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        ItemEntity entity = event.getItemEntity();
        ItemStack stack = entity.getItem();
        if (player.level().isClientSide() || !(stack.getItem() instanceof CashItem cash) || entity.hasPickUpDelay()
                || (entity.getTarget() != null && !entity.getTarget().equals(player.getUUID()))) {
            return;
        }
        List<ItemStack> wallets = Purse.wallets(player);
        if (wallets.isEmpty()) {
            return;
        }
        int count = stack.getCount();
        ItemStack wallet = wallets.get(0);
        Purse.setContents(wallet, Purse.contents(wallet).plus(cash.denomination(), count));
        player.take(entity, count);
        player.awardStat(Stats.ITEM_PICKED_UP.get(stack.getItem()), count);
        entity.discard();
        event.setCanPickup(TriState.FALSE);
    }
}
