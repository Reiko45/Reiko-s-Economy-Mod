package com.reiko.economy.block.entity;

import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.Money;
import com.reiko.economy.registry.ModBlockEntities;
import com.reiko.economy.shop.InventorySync;
import com.reiko.economy.shop.Payment;
import com.reiko.economy.shop.ShopActionResult;
import com.reiko.economy.shop.ShopPayments;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A player-owned shop: the owner stocks it with one kind of item and sets a price per bundle; other players
 * buy from it, and the money goes straight into the owner's bank account, even while they are offline.
 *
 * <p>The stock is deliberately not exposed as an item capability, so hoppers and pipes cannot pull items
 * out of someone else's shop.
 */
public final class ShopBlockEntity extends AbstractShopBlockEntity {
    public static final int STOCK_SLOTS = 27;

    @Nullable
    private UUID owner;
    private String ownerName = "";
    /** Whether at least one bundle is in stock. Synced so the floating label can say "Out of stock". */
    private boolean inStock;

    private final ItemStackHandler stock = new ItemStackHandler(STOCK_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            onStockChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return matchesTemplate(stack);
        }
    };

    public ShopBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHOP.get(), pos, state);
    }

    // ---- ownership ----

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name;
        markChangedAndSync();
    }

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    // ---- stock ----

    public ItemStackHandler stock() {
        return stock;
    }

    public boolean matchesTemplate(ItemStack stack) {
        return !template.isEmpty() && ItemStack.isSameItemSameComponents(stack, template);
    }

    /** Number of items (not bundles) in stock. */
    public int stockCount() {
        int count = 0;
        for (int i = 0; i < stock.getSlots(); i++) {
            ItemStack stack = stock.getStackInSlot(i);
            if (matchesTemplate(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public int availableBundles() {
        return template.isEmpty() ? 0 : stockCount() / template.getCount();
    }

    public boolean isStockEmpty() {
        for (int i = 0; i < stock.getSlots(); i++) {
            if (!stock.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean isConfigured() {
        return !template.isEmpty() && price > 0;
    }

    /** Client-side display flag; on the server use {@link #availableBundles()}. */
    public boolean displayInStock() {
        return inStock;
    }

    private void onStockChanged() {
        setChanged();
        refreshInStock();
    }

    private void refreshInStock() {
        boolean now = availableBundles() > 0;
        if (now != inStock) {
            inStock = now;
            markChangedAndSync();
        }
    }

    public void dropStock(Level level, BlockPos pos) {
        for (int i = 0; i < stock.getSlots(); i++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stock.getStackInSlot(i));
            stock.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    // ---- owner setup ----

    /**
     * Sets the item for sale. The stack's count becomes the bundle size. The item can't change while the shop
     * still holds stock of a different item, so stock never ends up stuck in a shop that won't sell it.
     */
    public ShopActionResult trySetTemplate(ItemStack requested) {
        boolean sameItem = !requested.isEmpty() && matchesTemplate(requested);
        if (!sameItem && !isStockEmpty()) {
            return ShopActionResult.fail("shop.reikoeconomy.template.stock_not_empty");
        }
        if (requested.isEmpty()) {
            setTemplateInternal(ItemStack.EMPTY);
            refreshInStock();
            return ShopActionResult.ok(Component.translatable("shop.reikoeconomy.template.cleared"));
        }
        setTemplateInternal(requested.copyWithCount(Math.min(requested.getCount(), requested.getMaxStackSize())));
        refreshInStock();
        return ShopActionResult.ok(Component.translatable("shop.reikoeconomy.template.set",
                template.getCount(), template.getHoverName()));
    }

    public ShopActionResult trySetPrice(String text) {
        OptionalLong parsed = Money.parsePositive(text);
        if (parsed.isEmpty()) {
            return ShopActionResult.fail(TransactionResult.INVALID_AMOUNT.translationKey());
        }
        setPriceInternal(parsed.getAsLong());
        return ShopActionResult.ok(Component.translatable("shop.reikoeconomy.price.set",
                EconomyService.INSTANCE.format(price)));
    }

    // ---- selling ----

    /**
     * Sells {@code bundles} bundles to {@code buyer}. All checks happen before anything changes: the price the
     * buyer saw must still be the price, the stock must cover the whole order and the buyer must be able to pay
     * by card or cash. The money moves first; the items only leave the shop once the payment has gone through.
     */
    public ShopActionResult sellTo(ServerPlayer buyer, int bundles, long expectedPrice, Payment payment) {
        if (!isConfigured()) {
            return ShopActionResult.fail("shop.reikoeconomy.not_set_up");
        }
        if (owner == null) {
            return ShopActionResult.fail("shop.reikoeconomy.no_owner");
        }
        if (isOwner(buyer)) {
            return ShopActionResult.fail("shop.reikoeconomy.own_shop");
        }
        if (expectedPrice != price) {
            return ShopActionResult.fail("shop.reikoeconomy.price_changed");
        }
        if (bundles < 1 || bundles > MAX_BUNDLES_PER_TRADE) {
            return ShopActionResult.fail("shop.reikoeconomy.bad_quantity", MAX_BUNDLES_PER_TRADE);
        }
        int itemsNeeded = template.getCount() * bundles;
        if (stockCount() < itemsNeeded) {
            return ShopActionResult.fail("shop.reikoeconomy.out_of_stock", availableBundles());
        }
        long total = price * bundles; // price <= 1e14 and bundles <= 64, so this cannot overflow
        if (total > Money.MAX_BALANCE) {
            return ShopActionResult.fail(TransactionResult.BALANCE_LIMIT.translationKey());
        }

        MinecraftServer server = buyer.server;
        EconomyService eco = EconomyService.INSTANCE;
        // The owner has an account from when they joined; this only matters if the ledger was reset since.
        eco.ensureAccount(server, owner, ownerName);
        ShopPayments.Charge charge = ShopPayments.charge(buyer, payment, total, owner, "shop.buy");
        if (!charge.success()) {
            return ShopActionResult.fail(charge.message());
        }

        ItemStack sold = template.copyWithCount(itemsNeeded);
        List<ItemStack> before = InventorySync.snapshot(buyer);
        for (ItemStack stack : takeFromStock(itemsNeeded)) {
            ItemHandlerHelper.giveItemToPlayer(buyer, stack);
        }
        InventorySync.sendChanges(buyer, before);

        String formattedTotal = eco.format(total);
        ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(owner);
        if (ownerPlayer != null) {
            ownerPlayer.sendSystemMessage(Component.translatable("shop.reikoeconomy.sold_notice",
                    buyer.getGameProfile().getName(), itemsNeeded, sold.getHoverName(), formattedTotal));
        }
        return ShopActionResult.ok(ShopPayments.receipt(Component.translatable("shop.reikoeconomy.bought",
                itemsNeeded, sold.getHoverName(), formattedTotal), payment, charge));
    }

    private List<ItemStack> takeFromStock(int count) {
        List<ItemStack> taken = new ArrayList<>();
        int remaining = count;
        for (int i = 0; i < stock.getSlots() && remaining > 0; i++) {
            if (!matchesTemplate(stock.getStackInSlot(i))) {
                continue;
            }
            ItemStack extracted = stock.extractItem(i, remaining, false);
            remaining -= extracted.getCount();
            if (!extracted.isEmpty()) {
                taken.add(extracted);
            }
        }
        return taken;
    }

    // ---- display ----

    @Override
    public List<Component> labelLines() {
        List<Component> lines = new ArrayList<>();
        if (template.isEmpty() || price <= 0) {
            lines.add(Component.translatable("shop.reikoeconomy.label.not_set_up").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        lines.add(Component.translatable("shop.reikoeconomy.label.price", template.getCount(),
                EconomyService.INSTANCE.format(price)).withStyle(ChatFormatting.GOLD));
        if (!inStock) {
            lines.add(Component.translatable("shop.reikoeconomy.label.out_of_stock").withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    // ---- saving and syncing ----

    @Override
    protected void writeDisplayData(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeDisplayData(tag, registries);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        tag.putString("owner_name", ownerName);
        tag.putBoolean("in_stock", inStock);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("stock", stock.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        inStock = tag.getBoolean("in_stock");
        if (tag.contains("stock")) { // only present on the server; clients get display data only
            stock.deserializeNBT(registries, tag.getCompound("stock"));
            inStock = availableBundles() > 0;
        }
    }
}
