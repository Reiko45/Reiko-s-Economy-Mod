package com.reiko.economy.menu;

import com.reiko.economy.block.entity.ShopBlockEntity;
import com.reiko.economy.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A customer's view of a player shop: buy from it, and sell to it if the owner set a "shop pays" price. Has no
 * slots; trades go through {@code ShopTradePayload}.
 */
public final class ShopBuyMenu extends ShopMenuBase {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 166;
    /** Taller, to fit the sell section, when the shop buys too. */
    public static final int HEIGHT_WITH_SELLING = 206;

    @Nullable
    private final ShopBlockEntity shop; // null on the client
    private final ItemStack template;
    private final long price;
    private final long sellPrice;
    private final String ownerName;
    /** Slot 0: bundles in stock. Slot 1: bundles the shop can still buy. Kept live by the server every tick. */
    private final ContainerData data;

    public ShopBuyMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, null, ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readVarLong(),
                buf.readUtf(64), new SimpleContainerData(2));
    }

    private ShopBuyMenu(int containerId, @Nullable ShopBlockEntity shop, ItemStack template, long price, long sellPrice,
            String ownerName, ContainerData data) {
        super(ModMenus.SHOP_BUY.get(), containerId);
        this.shop = shop;
        this.template = template;
        this.price = price;
        this.sellPrice = sellPrice;
        this.ownerName = ownerName;
        this.data = data;
        addDataSlots(data);
    }

    public static void open(ServerPlayer player, ShopBlockEntity shop) {
        Component title = Component.translatable("container.reikoeconomy.shop_buy", shop.ownerName());
        ContainerData liveStock = new ContainerData() {
            @Override
            public int get(int index) {
                int value = index == 0 ? shop.availableBundles() : shop.bundlesWanted(player.server);
                return Math.min(value, Short.MAX_VALUE); // data slots are sent as shorts
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
        ItemStack template = shop.template().copy();
        long price = shop.price();
        long sellPrice = shop.sellPrice();
        String ownerName = shop.ownerName();
        player.openMenu(new SimpleMenuProvider((id, inventory, p) ->
                new ShopBuyMenu(id, shop, template, price, sellPrice, ownerName, liveStock), title), buf -> {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, template);
                    buf.writeVarLong(price);
                    buf.writeVarLong(sellPrice);
                    buf.writeUtf(ownerName, 64);
                });
    }

    @Nullable
    public ShopBlockEntity shop() {
        return shop;
    }

    public ItemStack template() {
        return template;
    }

    /** The price shown when the screen opened. Sent back with every purchase so a price change is caught. */
    public long price() {
        return price;
    }

    /** What the shop pays per bundle, as shown when the screen opened. 0 if it doesn't buy. */
    public long sellPrice() {
        return sellPrice;
    }

    public String ownerName() {
        return ownerName;
    }

    public int availableBundles() {
        return data.get(0);
    }

    /** Bundles the shop can still buy: limited by its free stock space and the owner's money. */
    public int bundlesWanted() {
        return data.get(1);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return shop == null || Container.stillValidBlockEntity(shop, player);
    }
}
