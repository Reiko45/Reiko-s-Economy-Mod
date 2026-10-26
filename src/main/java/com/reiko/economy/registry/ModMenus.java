package com.reiko.economy.registry;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.menu.AdminShopSetupMenu;
import com.reiko.economy.menu.AdminShopTradeMenu;
import com.reiko.economy.menu.AtmMenu;
import com.reiko.economy.menu.AuctionMenu;
import com.reiko.economy.menu.JobBoardMenu;
import com.reiko.economy.menu.ShopBuyMenu;
import com.reiko.economy.menu.ShopOwnerMenu;
import com.reiko.economy.menu.WalletMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ReikoEconomy.MODID);

    public static final Supplier<MenuType<ShopOwnerMenu>> SHOP_OWNER =
            MENUS.register("shop_owner", () -> IMenuTypeExtension.create(ShopOwnerMenu::new));
    public static final Supplier<MenuType<ShopBuyMenu>> SHOP_BUY =
            MENUS.register("shop_buy", () -> IMenuTypeExtension.create(ShopBuyMenu::new));
    public static final Supplier<MenuType<AdminShopTradeMenu>> ADMIN_SHOP_TRADE =
            MENUS.register("admin_shop_trade", () -> IMenuTypeExtension.create(AdminShopTradeMenu::new));
    public static final Supplier<MenuType<AdminShopSetupMenu>> ADMIN_SHOP_SETUP =
            MENUS.register("admin_shop_setup", () -> IMenuTypeExtension.create(AdminShopSetupMenu::new));
    /** The ATM needs no extra data when opening, so it uses a plain vanilla menu type. */
    public static final Supplier<MenuType<AtmMenu>> ATM =
            MENUS.register("atm", () -> new MenuType<>(AtmMenu::new, FeatureFlags.VANILLA_SET));
    /** The job board sends the jobs and the player's levels. */
    public static final Supplier<MenuType<JobBoardMenu>> JOB_BOARD =
            MENUS.register("job_board", () -> IMenuTypeExtension.create(JobBoardMenu::new));
    /** The auction house: the listings come separately, so it needs no extra data when opening. */
    public static final Supplier<MenuType<AuctionMenu>> AUCTION =
            MENUS.register("auction", () -> new MenuType<>(AuctionMenu::new, FeatureFlags.VANILLA_SET));
    /** The wallet sends which hand holds it. */
    public static final Supplier<MenuType<WalletMenu>> WALLET =
            MENUS.register("wallet", () -> IMenuTypeExtension.create(WalletMenu::new));

    private ModMenus() {
    }
}
