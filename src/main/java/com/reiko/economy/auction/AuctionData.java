package com.reiko.economy.auction;

import com.reiko.economy.ReikoEconomy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Everything in the auction house: the listings (with the items they hold) and items waiting for players to
 * collect. Saved with the overworld in {@code data/reikoeconomy_auctions.dat}. Only {@link AuctionService}
 * changes it.
 */
public final class AuctionData extends SavedData {
    public static final String DATA_NAME = ReikoEconomy.MODID + "_auctions";

    private final Map<UUID, Listing> listings = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> claims = new HashMap<>();

    /** One listing. The item is held here until it's sold, won, cancelled or expired. */
    public static final class Listing {
        private final UUID id;
        private final UUID seller;
        private final String sellerName;
        private final ItemStack item;
        private final boolean auction;
        /** Buy-now price, or an auction's starting bid. */
        private final long price;
        private final long createdAt;
        private long endsAt;
        private long bid;
        @Nullable
        private UUID bidder;
        private String bidderName = "";

        Listing(UUID id, UUID seller, String sellerName, ItemStack item, boolean auction, long price, long createdAt, long endsAt) {
            this.id = id;
            this.seller = seller;
            this.sellerName = sellerName;
            this.item = item;
            this.auction = auction;
            this.price = price;
            this.createdAt = createdAt;
            this.endsAt = endsAt;
        }

        public UUID id() {
            return id;
        }

        public UUID seller() {
            return seller;
        }

        public String sellerName() {
            return sellerName;
        }

        /** The item for sale. Don't modify it. */
        public ItemStack item() {
            return item;
        }

        public boolean auction() {
            return auction;
        }

        public long price() {
            return price;
        }

        public long createdAt() {
            return createdAt;
        }

        /** When it ends, in milliseconds since 1970. */
        public long endsAt() {
            return endsAt;
        }

        /** The highest bid so far, or 0. */
        public long bid() {
            return bid;
        }

        @Nullable
        public UUID bidder() {
            return bidder;
        }

        public String bidderName() {
            return bidderName;
        }

        public boolean hasBids() {
            return bidder != null;
        }

        /** What the listing costs now: the buy-now price, the highest bid, or the starting bid. */
        public long currentPrice() {
            return hasBids() ? bid : price;
        }
    }

    public static AuctionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(AuctionData::new, AuctionData::load), DATA_NAME);
    }

    public Collection<Listing> listings() {
        return Collections.unmodifiableCollection(listings.values());
    }

    public Optional<Listing> find(UUID id) {
        return Optional.ofNullable(listings.get(id));
    }

    /** Items waiting for the player to collect. Don't modify the list. */
    public List<ItemStack> claims(UUID player) {
        return Collections.unmodifiableList(claims.getOrDefault(player, List.of()));
    }

    void add(Listing listing) {
        listings.put(listing.id, listing);
        setDirty();
    }

    void remove(Listing listing) {
        listings.remove(listing.id);
        setDirty();
    }

    void setBid(Listing listing, long bid, UUID bidder, String bidderName) {
        listing.bid = bid;
        listing.bidder = bidder;
        listing.bidderName = bidderName;
        setDirty();
    }

    void setEndsAt(Listing listing, long endsAt) {
        listing.endsAt = endsAt;
        setDirty();
    }

    void addClaim(UUID player, ItemStack item) {
        if (!item.isEmpty()) {
            claims.computeIfAbsent(player, id -> new ArrayList<>()).add(item.copy());
            setDirty();
        }
    }

    void setClaims(UUID player, List<ItemStack> items) {
        if (items.isEmpty()) {
            claims.remove(player);
        } else {
            claims.put(player, new ArrayList<>(items));
        }
        setDirty();
    }

    public static AuctionData load(CompoundTag tag, HolderLookup.Provider registries) {
        AuctionData data = new AuctionData();
        ListTag list = tag.getList("listings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag saved = list.getCompound(i);
            ItemStack item = ItemStack.parseOptional(registries, saved.getCompound("item"));
            if (!saved.hasUUID("id") || !saved.hasUUID("seller") || item.isEmpty()) {
                continue;
            }
            Listing listing = new Listing(saved.getUUID("id"), saved.getUUID("seller"), saved.getString("seller_name"), item,
                    saved.getBoolean("auction"), saved.getLong("price"), saved.getLong("created_at"), saved.getLong("ends_at"));
            if (saved.hasUUID("bidder")) {
                listing.bid = saved.getLong("bid");
                listing.bidder = saved.getUUID("bidder");
                listing.bidderName = saved.getString("bidder_name");
            }
            data.listings.put(listing.id, listing);
        }
        ListTag claimList = tag.getList("claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < claimList.size(); i++) {
            CompoundTag entry = claimList.getCompound(i);
            if (!entry.hasUUID("player")) {
                continue;
            }
            List<ItemStack> items = new ArrayList<>();
            ListTag saved = entry.getList("items", Tag.TAG_COMPOUND);
            for (int j = 0; j < saved.size(); j++) {
                ItemStack item = ItemStack.parseOptional(registries, saved.getCompound(j));
                if (!item.isEmpty()) {
                    items.add(item);
                }
            }
            if (!items.isEmpty()) {
                data.claims.put(entry.getUUID("player"), items);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Listing listing : listings.values()) {
            CompoundTag saved = new CompoundTag();
            saved.putUUID("id", listing.id);
            saved.putUUID("seller", listing.seller);
            saved.putString("seller_name", listing.sellerName);
            saved.put("item", listing.item.save(registries));
            saved.putBoolean("auction", listing.auction);
            saved.putLong("price", listing.price);
            saved.putLong("created_at", listing.createdAt);
            saved.putLong("ends_at", listing.endsAt);
            if (listing.bidder != null) {
                saved.putLong("bid", listing.bid);
                saved.putUUID("bidder", listing.bidder);
                saved.putString("bidder_name", listing.bidderName);
            }
            list.add(saved);
        }
        tag.put("listings", list);
        ListTag claimList = new ListTag();
        claims.forEach((player, items) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            ListTag saved = new ListTag();
            for (ItemStack item : items) {
                saved.add(item.save(registries));
            }
            entry.put("items", saved);
            claimList.add(entry);
        });
        tag.put("claims", claimList);
        return tag;
    }
}
