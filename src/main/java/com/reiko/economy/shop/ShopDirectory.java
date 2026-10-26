package com.reiko.economy.shop;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.block.entity.AbstractShopBlockEntity;
import com.reiko.economy.block.entity.AdminShopBlockEntity;
import com.reiko.economy.block.entity.ShopBlockEntity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * A list of every shop in the world, for {@code /shops}. Shops can't be read while their chunk isn't loaded,
 * so each shop reports its item, prices and stock here whenever they change, and when its chunk loads. Saved
 * with the overworld in {@code data/reikoeconomy_shop_directory.dat}.
 *
 * <p>Shops placed before this list existed join it the first time their area is loaded.
 */
public final class ShopDirectory extends SavedData {
    public static final String DATA_NAME = ReikoEconomy.MODID + "_shop_directory";

    private final Map<GlobalPos, Entry> shops = new HashMap<>();

    /**
     * What's known about one shop.
     *
     * @param serverShop    a server shop: infinite stock, always buys, no owner
     * @param owner         the owner, or null for a server shop
     * @param template      one bundle of the item
     * @param price         what players pay per bundle; 0 if it doesn't sell
     * @param sellPrice     what the shop pays per bundle; 0 if it doesn't buy
     * @param stockBundles  bundles in stock (player shops)
     * @param roomBundles   bundles of free space in the stock (player shops)
     */
    public record Entry(GlobalPos pos, boolean serverShop, @Nullable UUID owner, String ownerName, ItemStack template,
                        long price, long sellPrice, int stockBundles, int roomBundles) {
        public boolean sells() {
            return !template.isEmpty() && price > 0;
        }

        public boolean buys() {
            return !template.isEmpty() && sellPrice > 0;
        }

        public boolean inStock() {
            return serverShop || stockBundles > 0;
        }

        /** Price per single item, for comparing shops with different bundle sizes. */
        public double pricePerItem() {
            return (double) price / template.getCount();
        }

        public double sellPricePerItem() {
            return (double) sellPrice / template.getCount();
        }

        private boolean sameAs(Entry other) {
            return serverShop == other.serverShop && Objects.equals(owner, other.owner) && ownerName.equals(other.ownerName)
                    && ItemStack.isSameItemSameComponents(template, other.template) && template.getCount() == other.template.getCount()
                    && price == other.price && sellPrice == other.sellPrice && stockBundles == other.stockBundles
                    && roomBundles == other.roomBundles;
        }
    }

    public static ShopDirectory get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ShopDirectory::new, ShopDirectory::load), DATA_NAME);
    }

    /**
     * Records a shop's current state, or forgets it if it isn't set up. Called on the server whenever the shop
     * changes or its chunk loads.
     */
    public static void update(AbstractShopBlockEntity shop) {
        if (shop.getLevel() instanceof ServerLevel level) {
            get(level.getServer()).refresh(GlobalPos.of(level.dimension(), shop.getBlockPos()), shop);
        }
    }

    private void refresh(GlobalPos pos, AbstractShopBlockEntity shop) {
        Entry entry = null;
        if (shop instanceof ShopBlockEntity player) {
            entry = new Entry(pos, false, player.owner(), player.ownerName(), player.template().copy(), player.price(),
                    player.sellPrice(), player.availableBundles(), player.roomInBundles());
        } else if (shop instanceof AdminShopBlockEntity admin) {
            entry = new Entry(pos, true, null, "", admin.template().copy(), admin.price(), admin.sellPrice(), 0, 0);
        }
        if (entry == null || !(entry.sells() || entry.buys())) {
            if (shops.remove(pos) != null) {
                setDirty();
            }
            return;
        }
        Entry old = shops.get(pos);
        if (old == null || !old.sameAs(entry)) {
            shops.put(pos, entry);
            setDirty();
        }
    }

    /** Forgets the shop at {@code pos}, when it's broken or replaced. */
    public static void remove(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            ShopDirectory directory = get(serverLevel.getServer());
            if (directory.shops.remove(GlobalPos.of(level.dimension(), pos.immutable())) != null) {
                directory.setDirty();
            }
        }
    }

    /**
     * Every shop that's set up. Shops in loaded chunks are read fresh first, and dropped if their block is gone;
     * shops in unloaded chunks are listed as they were last seen.
     */
    public List<Entry> all(MinecraftServer server) {
        for (GlobalPos pos : List.copyOf(shops.keySet())) {
            ServerLevel level = server.getLevel(pos.dimension());
            if (level == null) {
                shops.remove(pos);
                setDirty();
            } else if (level.isLoaded(pos.pos())) {
                refresh(pos, level.getBlockEntity(pos.pos()) instanceof AbstractShopBlockEntity shop ? shop : null);
            }
        }
        return new ArrayList<>(shops.values());
    }

    /** Forgets every shop. For the game-test server, whose test areas are rebuilt each run. */
    public void clear() {
        shops.clear();
        setDirty();
    }

    public Collection<Entry> entries() {
        return shops.values();
    }

    public static ShopDirectory load(CompoundTag tag, HolderLookup.Provider registries) {
        ShopDirectory directory = new ShopDirectory();
        ListTag list = tag.getList("shops", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag saved = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(saved.getString("dimension"));
            BlockPos blockPos = NbtUtils.readBlockPos(saved, "pos").orElse(null);
            ItemStack template = ItemStack.parseOptional(registries, saved.getCompound("template"));
            if (dimension == null || blockPos == null || template.isEmpty()) {
                continue;
            }
            GlobalPos pos = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension), blockPos);
            directory.shops.put(pos, new Entry(pos, saved.getBoolean("server_shop"),
                    saved.hasUUID("owner") ? saved.getUUID("owner") : null, saved.getString("owner_name"), template,
                    saved.getLong("price"), saved.getLong("sell_price"), saved.getInt("stock"), saved.getInt("room")));
        }
        return directory;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : shops.values()) {
            if (entry.template().isEmpty()) {
                continue; // nothing to find; it's listed again once it has an item
            }
            CompoundTag saved = new CompoundTag();
            saved.putString("dimension", entry.pos().dimension().location().toString());
            saved.put("pos", NbtUtils.writeBlockPos(entry.pos().pos()));
            saved.putBoolean("server_shop", entry.serverShop());
            if (entry.owner() != null) {
                saved.putUUID("owner", entry.owner());
            }
            saved.putString("owner_name", entry.ownerName());
            saved.put("template", entry.template().save(registries));
            saved.putLong("price", entry.price());
            saved.putLong("sell_price", entry.sellPrice());
            saved.putInt("stock", entry.stockBundles());
            saved.putInt("room", entry.roomBundles());
            list.add(saved);
        }
        tag.put("shops", list);
        return tag;
    }
}
