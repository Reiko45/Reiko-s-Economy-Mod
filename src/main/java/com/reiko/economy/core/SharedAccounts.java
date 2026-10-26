package com.reiko.economy.core;

import com.reiko.economy.ReikoEconomy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Shared accounts: bank accounts that belong to a group of players, like a town or a team. The money itself is
 * in the {@link Ledger}, under the shared account's id, so transfers, the transaction log and taxes work the same
 * as for a player's account. This keeps who owns each one, who's in it, and each member's debit card.
 * Saved with the overworld in {@code data/reikoeconomy_shared_accounts.dat}.
 */
public final class SharedAccounts extends SavedData {
    public static final String DATA_NAME = ReikoEconomy.MODID + "_shared_accounts";

    private final Map<UUID, Account> accounts = new LinkedHashMap<>();

    /** One shared account. Read-only outside this package. */
    public static final class Account {
        private final UUID id;
        private String name;
        private UUID owner;
        /** Everyone in the account, the owner included, in the order they joined. */
        private final Map<UUID, Member> members = new LinkedHashMap<>();

        private Account(UUID id, String name, UUID owner) {
            this.id = id;
            this.name = name;
            this.owner = owner;
        }

        /** The ledger account the money is in. */
        public UUID id() {
            return id;
        }

        public String name() {
            return name;
        }

        public UUID owner() {
            return owner;
        }

        public boolean isOwner(UUID player) {
            return owner.equals(player);
        }

        public boolean isMember(UUID player) {
            return members.containsKey(player);
        }

        public Collection<Member> members() {
            return Collections.unmodifiableCollection(members.values());
        }

        public Optional<Member> member(UUID player) {
            return Optional.ofNullable(members.get(player));
        }

        /** The member whose working debit card this is, if any. */
        public Optional<Member> cardHolder(UUID cardId) {
            return members.values().stream().filter(member -> cardId.equals(member.cardId)).findFirst();
        }
    }

    /** A player in a shared account, with their own debit card for it (if they got one). */
    public static final class Member {
        private final UUID player;
        private String name;
        @Nullable
        private UUID cardId;
        /** Salted SHA-256 of the card's PIN, as for personal cards. */
        private String pinHash = "";
        private int pinFailures;
        private int cardsIssued;

        private Member(UUID player, String name) {
            this.player = player;
            this.name = name;
        }

        public UUID player() {
            return player;
        }

        /** Their name when they were added, or when they last got a card. */
        public String name() {
            return name;
        }

        @Nullable
        public UUID cardId() {
            return cardId;
        }

        public String pinHash() {
            return pinHash;
        }

        public int pinFailures() {
            return pinFailures;
        }

        public int cardsIssued() {
            return cardsIssued;
        }
    }

    public static SharedAccounts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SharedAccounts::new, SharedAccounts::load), DATA_NAME);
    }

    public Optional<Account> find(UUID id) {
        return Optional.ofNullable(accounts.get(id));
    }

    /** Finds an account by name, ignoring case. */
    public Optional<Account> byName(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        return accounts.values().stream().filter(account -> account.name.toLowerCase(Locale.ROOT).equals(key)).findFirst();
    }

    public boolean isShared(@Nullable UUID id) {
        return id != null && accounts.containsKey(id);
    }

    /** Every shared account, by name. */
    public List<Account> all() {
        List<Account> list = new ArrayList<>(accounts.values());
        list.sort(Comparator.comparing(Account::name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** The accounts {@code player} is in, by name. */
    public List<Account> accountsOf(UUID player) {
        List<Account> list = new ArrayList<>();
        for (Account account : all()) {
            if (account.isMember(player)) {
                list.add(account);
            }
        }
        return list;
    }

    /** How many accounts {@code player} owns. */
    public int ownedBy(UUID player) {
        int count = 0;
        for (Account account : accounts.values()) {
            if (account.isOwner(player)) {
                count++;
            }
        }
        return count;
    }

    // ---- changes, through SharedAccountService and CardService ----

    Account create(UUID id, String name, UUID owner, String ownerName) {
        Account account = new Account(id, name, owner);
        account.members.put(owner, new Member(owner, ownerName));
        accounts.put(id, account);
        setDirty();
        return account;
    }

    void remove(Account account) {
        accounts.remove(account.id);
        setDirty();
    }

    void addMember(Account account, UUID player, String name) {
        account.members.put(player, new Member(player, name));
        setDirty();
    }

    void removeMember(Account account, UUID player) {
        account.members.remove(player);
        setDirty();
    }

    void setOwner(Account account, UUID owner) {
        account.owner = owner;
        setDirty();
    }

    void issueCard(Member member, String name, UUID cardId, String pinHash) {
        member.name = name;
        member.cardId = cardId;
        member.pinHash = pinHash;
        member.pinFailures = 0;
        member.cardsIssued++;
        setDirty();
    }

    void setPinFailures(Member member, int failures) {
        member.pinFailures = failures;
        setDirty();
    }

    // ---- saving ----

    public static SharedAccounts load(CompoundTag tag, HolderLookup.Provider registries) {
        SharedAccounts data = new SharedAccounts();
        ListTag list = tag.getList("accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag saved = list.getCompound(i);
            if (!saved.hasUUID("id") || !saved.hasUUID("owner")) {
                continue;
            }
            Account account = new Account(saved.getUUID("id"), saved.getString("name"), saved.getUUID("owner"));
            ListTag members = saved.getList("members", Tag.TAG_COMPOUND);
            for (int j = 0; j < members.size(); j++) {
                CompoundTag entry = members.getCompound(j);
                if (!entry.hasUUID("player")) {
                    continue;
                }
                Member member = new Member(entry.getUUID("player"), entry.getString("name"));
                member.cardId = entry.hasUUID("card_id") ? entry.getUUID("card_id") : null;
                member.pinHash = entry.getString("pin_hash");
                member.pinFailures = entry.getInt("pin_failures");
                member.cardsIssued = entry.getInt("cards_issued");
                account.members.put(member.player, member);
            }
            if (!account.members.containsKey(account.owner)) {
                account.members.put(account.owner, new Member(account.owner, ""));
            }
            data.accounts.put(account.id, account);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Account account : accounts.values()) {
            CompoundTag saved = new CompoundTag();
            saved.putUUID("id", account.id);
            saved.putString("name", account.name);
            saved.putUUID("owner", account.owner);
            ListTag members = new ListTag();
            for (Member member : account.members.values()) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("player", member.player);
                entry.putString("name", member.name);
                if (member.cardId != null) {
                    entry.putUUID("card_id", member.cardId);
                    entry.putString("pin_hash", member.pinHash);
                    entry.putInt("pin_failures", member.pinFailures);
                }
                entry.putInt("cards_issued", member.cardsIssued);
                members.add(entry);
            }
            saved.put("members", members);
            list.add(saved);
        }
        tag.put("accounts", list);
        return tag;
    }
}
