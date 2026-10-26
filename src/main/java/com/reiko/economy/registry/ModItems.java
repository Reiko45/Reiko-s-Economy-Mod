package com.reiko.economy.registry;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.core.Denomination;
import com.reiko.economy.item.CashItem;
import com.reiko.economy.item.CreditCardItem;
import com.reiko.economy.item.DebitCardItem;
import com.reiko.economy.item.WalletItem;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ReikoEconomy.MODID);

    public static final DeferredItem<BlockItem> SHOP = ITEMS.registerSimpleBlockItem("shop", ModBlocks.SHOP);
    public static final DeferredItem<BlockItem> ADMIN_SHOP = ITEMS.registerSimpleBlockItem("admin_shop", ModBlocks.ADMIN_SHOP,
            new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<BlockItem> ATM = ITEMS.registerSimpleBlockItem("atm", ModBlocks.ATM);
    public static final DeferredItem<DebitCardItem> DEBIT_CARD = ITEMS.register("debit_card",
            () -> new DebitCardItem(new Item.Properties()));
    public static final DeferredItem<WalletItem> WALLET = ITEMS.register("wallet",
            () -> new WalletItem(new Item.Properties()));
    public static final DeferredItem<CreditCardItem> CREDIT_CARD = ITEMS.register("credit_card",
            () -> new CreditCardItem(new Item.Properties().rarity(Rarity.UNCOMMON)));

    /** One item per coin and bill: coin_1 ... coin_50, bill_1 ... bill_100. */
    public static final Map<Denomination, DeferredItem<CashItem>> CASH;

    static {
        Map<Denomination, DeferredItem<CashItem>> cash = new EnumMap<>(Denomination.class);
        for (Denomination denomination : Denomination.values()) {
            cash.put(denomination, ITEMS.register(denomination.id(), () -> new CashItem(denomination, new Item.Properties())));
        }
        CASH = Collections.unmodifiableMap(cash);
    }

    private ModItems() {
    }

    public static CashItem cash(Denomination denomination) {
        return CASH.get(denomination).get();
    }
}
