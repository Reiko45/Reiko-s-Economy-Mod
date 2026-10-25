package com.reiko.economy.block.entity;

import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.Money;
import com.reiko.economy.registry.ModBlockEntities;
import com.reiko.economy.shop.InventorySync;
import com.reiko.economy.shop.ShopActionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * The server's shop, with infinite stock. It can sell an item to players (money leaves the economy) and/or
 * buy that item from players (money enters the economy), which is how admins seed a new world's economy.
 * A price of 0 turns that direction off.
 */
public final class AdminShopBlockEntity extends AbstractShopBlockEntity {
    /** Price the shop pays players per bundle they sell to it. 0 means it doesn't buy. */
    private long sellPrice;

    public AdminShopBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ADMIN_SHOP.get(), pos, state);
    }

    public long sellPrice() {
        return sellPrice;
    }

    /** True if players can buy the item here. */
    public boolean sellsToPlayers() {
        return !template.isEmpty() && price > 0;
    }

    /** True if players can sell the item here. */
    public boolean buysFromPlayers() {
        return !template.isEmpty() && sellPrice > 0;
    }

    public boolean matchesTemplate(ItemStack stack) {
        return !template.isEmpty() && ItemStack.isSameItemSameComponents(stack, template);
    }

    // ---- admin setup ----

    public ShopActionResult trySetTemplate(ItemStack requested) {
        if (requested.isEmpty()) {
            setTemplateInternal(ItemStack.EMPTY);
            return ShopActionResult.ok(Component.translatable("shop.reikoeconomy.template.cleared"));
        }
        setTemplateInternal(requested.copyWithCount(Math.min(requested.getCount(), requested.getMaxStackSize())));
        return ShopActionResult.ok(Component.translatable("shop.reikoeconomy.template.set",
                template.getCount(), template.getHoverName()));
    }

    /** Sets both prices from text. Blank means 0, which turns that direction off. */
    public ShopActionResult trySetPrices(String buyText, String sellText) {
        OptionalLong buy = buyText.isBlank() ? OptionalLong.of(0) : Money.parse(buyText);
        OptionalLong sell = sellText.isBlank() ? OptionalLong.of(0) : Money.parse(sellText);
        if (buy.isEmpty() || sell.isEmpty() || !validPrice(buy.getAsLong()) || !validPrice(sell.getAsLong())) {
            return ShopActionResult.fail(TransactionResult.INVALID_AMOUNT.translationKey());
        }
        price = buy.getAsLong();
        sellPrice = sell.getAsLong();
        markChangedAndSync();

        EconomyService eco = EconomyService.INSTANCE;
        MutableComponent message = Component.translatable("shop.reikoeconomy.admin.prices_set",
                price > 0 ? eco.format(price) : Component.translatable("shop.reikoeconomy.admin.off"),
                sellPrice > 0 ? eco.format(sellPrice) : Component.translatable("shop.reikoeconomy.admin.off"));
        if (price > 0 && sellPrice > price) {
            // Players could buy here and sell straight back for a profit, creating money from nothing.
            message.append(" ").append(Component.translatable("shop.reikoeconomy.admin.profit_warning")
                    .withStyle(ChatFormatting.RED));
        }
        return ShopActionResult.ok(message);
    }

    // ---- trading ----

    /** The player buys {@code bundles} bundles from the server. */
    public ShopActionResult sellTo(ServerPlayer player, int bundles, long expectedPrice) {
        if (!sellsToPlayers()) {
            return ShopActionResult.fail("shop.reikoeconomy.admin.not_selling");
        }
        if (expectedPrice != price) {
            return ShopActionResult.fail("shop.reikoeconomy.price_changed");
        }
        if (bundles < 1 || bundles > MAX_BUNDLES_PER_TRADE) {
            return ShopActionResult.fail("shop.reikoeconomy.bad_quantity", MAX_BUNDLES_PER_TRADE);
        }
        long total = price * bundles;
        if (total > Money.MAX_BALANCE) {
            return ShopActionResult.fail(TransactionResult.BALANCE_LIMIT.translationKey());
        }

        MinecraftServer server = player.server;
        EconomyService eco = EconomyService.INSTANCE;
        eco.ensureAccount(server, player.getUUID(), player.getGameProfile().getName());
        TransactionResult payment = eco.withdraw(server, player.getUUID(), total, "admin_shop.buy");
        if (!payment.isSuccess()) {
            return ShopActionResult.fail(payment.translationKey());
        }

        int items = template.getCount() * bundles;
        int maxStack = template.getMaxStackSize();
        List<ItemStack> before = InventorySync.snapshot(player);
        for (int left = items; left > 0; left -= maxStack) {
            ItemHandlerHelper.giveItemToPlayer(player, template.copyWithCount(Math.min(left, maxStack)));
        }
        InventorySync.sendChanges(player, before);
        return ShopActionResult.ok(Component.translatable("shop.reikoeconomy.bought",
                items, template.getHoverName(), eco.format(total)));
    }

    /**
     * The player sells up to {@code bundles} bundles to the server, from their main inventory and hotbar
     * (never their armor). Pass {@link Integer#MAX_VALUE} to sell everything they have. Only exact matches
     * count, so damaged or enchanted copies of the item are not accepted.
     */
    public ShopActionResult buyFrom(ServerPlayer player, int bundles, long expectedSellPrice) {
        if (!buysFromPlayers()) {
            return ShopActionResult.fail("shop.reikoeconomy.admin.not_buying");
        }
        if (expectedSellPrice != sellPrice) {
            return ShopActionResult.fail("shop.reikoeconomy.price_changed");
        }
        if (bundles < 1) {
            return ShopActionResult.fail("shop.reikoeconomy.bad_quantity", MAX_BUNDLES_PER_TRADE);
        }
        int held = countMatching(player.getInventory());
        int sellable = Math.min(held / template.getCount(), Math.min(bundles, MAX_BUNDLES_PER_TRADE * 36));
        if (sellable < 1) {
            return ShopActionResult.fail("shop.reikoeconomy.admin.nothing_to_sell", template.getCount(), template.getHoverName());
        }
        long total = sellPrice * sellable;

        MinecraftServer server = player.server;
        EconomyService eco = EconomyService.INSTANCE;
        eco.ensureAccount(server, player.getUUID(), player.getGameProfile().getName());
        TransactionResult payment = eco.deposit(server, player.getUUID(), total, "admin_shop.sell");
        if (!payment.isSuccess()) {
            return ShopActionResult.fail(payment.translationKey());
        }
        int items = sellable * template.getCount();
        List<ItemStack> before = InventorySync.snapshot(player);
        removeMatching(player.getInventory(), items);
        InventorySync.sendChanges(player, before);
        return ShopActionResult.ok(Component.translatable("shop.reikoeconomy.admin.sold",
                items, template.getHoverName(), eco.format(total)));
    }

    /** Matching items in the player's main inventory and hotbar. */
    public int countMatching(Inventory inventory) {
        int count = 0;
        for (ItemStack stack : inventory.items) {
            if (matchesTemplate(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void removeMatching(Inventory inventory, int count) {
        int remaining = count;
        for (ItemStack stack : inventory.items) {
            if (remaining <= 0) {
                break;
            }
            if (matchesTemplate(stack)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        inventory.setChanged();
    }

    // ---- display ----

    @Override
    public List<Component> labelLines() {
        List<Component> lines = new ArrayList<>();
        if (!sellsToPlayers() && !buysFromPlayers()) {
            lines.add(Component.translatable("shop.reikoeconomy.label.not_set_up").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        EconomyService eco = EconomyService.INSTANCE;
        if (sellsToPlayers()) {
            lines.add(Component.translatable("shop.reikoeconomy.label.buy", template.getCount(), eco.format(price))
                    .withStyle(ChatFormatting.GOLD));
        }
        if (buysFromPlayers()) {
            lines.add(Component.translatable("shop.reikoeconomy.label.sell", template.getCount(), eco.format(sellPrice))
                    .withStyle(ChatFormatting.GREEN));
        }
        return lines;
    }

    // ---- saving and syncing ----

    @Override
    protected void writeDisplayData(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeDisplayData(tag, registries);
        tag.putLong("sell_price", sellPrice);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sellPrice = tag.getLong("sell_price");
    }
}
