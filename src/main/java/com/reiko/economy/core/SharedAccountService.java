package com.reiko.economy.core;

import com.mojang.authlib.GameProfile;
import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.shop.ShopActionResult;
import com.reiko.economy.shop.ShopNotices;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Shared accounts: making them, managing members, and moving money in and out. The owner adds and removes
 * members, hands the account to another member, or closes it; every member can deposit, withdraw and pay from it.
 * Members can also get their own debit card for it ({@link CardService#issueShared}) and have their shops' money
 * go into it.
 *
 * <p>When someone other than the account itself and the other side of a payment makes it happen (a member paying
 * from the account, or paying at a shop with the shared card), the transaction log records who, so members can
 * see who spent what.
 */
public final class SharedAccountService {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{3,20}");
    private static final EconomyService ECO = EconomyService.INSTANCE;

    /** The player whose action is moving money right now. Server thread only. */
    @Nullable
    private static UUID actor;

    private SharedAccountService() {
    }

    // ---- who did it ----

    /** Runs {@code action} with {@code player} recorded as whoever made any shared-account transactions in it. */
    public static <T> T actingAs(@Nullable UUID player, Supplier<T> action) {
        UUID before = actor;
        actor = player;
        try {
            return action.get();
        } finally {
            actor = before;
        }
    }

    /**
     * Who to record as having made a transaction between {@code from} and {@code to}: the acting player, when the
     * transaction touches a shared account and the actor isn't already one of the two sides. Otherwise null.
     */
    @Nullable
    public static UUID actorFor(MinecraftServer server, @Nullable UUID from, @Nullable UUID to) {
        if (actor == null || actor.equals(from) || actor.equals(to)) {
            return null;
        }
        SharedAccounts data = SharedAccounts.get(server);
        return data.isShared(from) || data.isShared(to) ? actor : null;
    }

    // ---- lookups ----

    /** A shared account and, if the player can't use it, why. */
    private record Found(@Nullable SharedAccounts.Account account, @Nullable ShopActionResult problem) {
        static Found problem(String key, Object... args) {
            return new Found(null, ShopActionResult.fail(key, args));
        }
    }

    /** The account called {@code name}, if {@code player} is in it. */
    private static Found asMember(MinecraftServer server, UUID player, String name) {
        if (!EconomyConfig.sharedAccountsEnabled()) {
            return Found.problem("account.reikoeconomy.disabled");
        }
        Optional<SharedAccounts.Account> found = SharedAccounts.get(server).byName(name);
        if (found.isEmpty() || !found.get().isMember(player)) {
            return Found.problem("account.reikoeconomy.not_found", name);
        }
        return new Found(found.get(), null);
    }

    /** The account called {@code name}, if {@code player} owns it. */
    private static Found asOwner(MinecraftServer server, UUID player, String name) {
        Found found = asMember(server, player, name);
        if (found.account() != null && !found.account().isOwner(player)) {
            return Found.problem("account.reikoeconomy.owner_only", found.account().name());
        }
        return found;
    }

    /** The accounts {@code player} is in, for menus and suggestions. */
    public static List<SharedAccounts.Account> accountsOf(MinecraftServer server, UUID player) {
        return EconomyConfig.sharedAccountsEnabled() ? SharedAccounts.get(server).accountsOf(player) : List.of();
    }

    /** A player's current name: from the ledger, which updates when they join, or what was saved with them. */
    public static String nameOf(MinecraftServer server, UUID player, String fallback) {
        return ECO.findAccount(server, player).map(Ledger.Account::lastKnownName).filter(name -> !name.isEmpty()).orElse(fallback);
    }

    private static Optional<SharedAccounts.Member> memberNamed(MinecraftServer server, SharedAccounts.Account account, String name) {
        for (SharedAccounts.Member member : account.members()) {
            if (nameOf(server, member.player(), member.name()).equalsIgnoreCase(name)) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }

    // ---- making and managing ----

    public static ShopActionResult create(ServerPlayer player, String name) {
        MinecraftServer server = player.server;
        if (!EconomyConfig.sharedAccountsEnabled()) {
            return ShopActionResult.fail("account.reikoeconomy.disabled");
        }
        if (!NAME.matcher(name).matches()) {
            return ShopActionResult.fail("account.reikoeconomy.bad_name");
        }
        SharedAccounts data = SharedAccounts.get(server);
        if (data.byName(name).isPresent()) {
            return ShopActionResult.fail("account.reikoeconomy.name_taken", name);
        }
        int maxOwned = EconomyConfig.sharedAccountMaxOwned();
        if (data.ownedBy(player.getUUID()) >= maxOwned) {
            return ShopActionResult.fail("account.reikoeconomy.too_many", maxOwned);
        }
        Ledger ledger = Ledger.get(server);
        UUID id = UUID.randomUUID();
        while (ledger.find(id).isPresent()) {
            id = UUID.randomUUID();
        }
        ledger.create(id, name, 0);
        data.create(id, name, player.getUUID(), player.getGameProfile().getName());
        return ShopActionResult.ok(Component.translatable("account.reikoeconomy.created", name));
    }

    public static ShopActionResult addMember(ServerPlayer player, String name, GameProfile target) {
        MinecraftServer server = player.server;
        Found found = asOwner(server, player.getUUID(), name);
        if (found.problem() != null) {
            return found.problem();
        }
        SharedAccounts.Account account = found.account();
        if (account.isMember(target.getId())) {
            return ShopActionResult.fail("account.reikoeconomy.already_member", target.getName(), account.name());
        }
        int max = EconomyConfig.sharedAccountMaxMembers();
        if (account.members().size() >= max) {
            return ShopActionResult.fail("account.reikoeconomy.full", account.name(), max);
        }
        if (!ECO.hasAccount(server, target.getId())) {
            return ShopActionResult.fail("account.reikoeconomy.unknown_player", target.getName());
        }
        SharedAccounts.get(server).addMember(account, target.getId(), target.getName());
        ShopNotices.alert(server, target.getId(), Component.translatable("account.reikoeconomy.added_you",
                player.getGameProfile().getName(), account.name()).withStyle(ChatFormatting.GOLD));
        return ShopActionResult.ok(Component.translatable("account.reikoeconomy.added", target.getName(), account.name()));
    }

    public static ShopActionResult removeMember(ServerPlayer player, String name, String memberName) {
        MinecraftServer server = player.server;
        Found found = asOwner(server, player.getUUID(), name);
        if (found.problem() != null) {
            return found.problem();
        }
        SharedAccounts.Account account = found.account();
        Optional<SharedAccounts.Member> member = memberNamed(server, account, memberName);
        if (member.isEmpty()) {
            return ShopActionResult.fail("account.reikoeconomy.not_a_member", memberName, account.name());
        }
        if (account.isOwner(member.get().player())) {
            return ShopActionResult.fail("account.reikoeconomy.cant_remove_owner");
        }
        String shownName = nameOf(server, member.get().player(), member.get().name());
        SharedAccounts.get(server).removeMember(account, member.get().player());
        ShopNotices.alert(server, member.get().player(), Component.translatable("account.reikoeconomy.removed_you",
                player.getGameProfile().getName(), account.name()).withStyle(ChatFormatting.GOLD));
        return ShopActionResult.ok(Component.translatable("account.reikoeconomy.removed", shownName, account.name()));
    }

    public static ShopActionResult leave(ServerPlayer player, String name) {
        MinecraftServer server = player.server;
        Found found = asMember(server, player.getUUID(), name);
        if (found.problem() != null) {
            return found.problem();
        }
        SharedAccounts.Account account = found.account();
        if (account.isOwner(player.getUUID())) {
            return ShopActionResult.fail("account.reikoeconomy.owner_cant_leave", account.name());
        }
        SharedAccounts.get(server).removeMember(account, player.getUUID());
        ShopNotices.alert(server, account.owner(), Component.translatable("account.reikoeconomy.member_left",
                player.getGameProfile().getName(), account.name()).withStyle(ChatFormatting.GOLD));
        return ShopActionResult.ok(Component.translatable("account.reikoeconomy.you_left", account.name()));
    }

    public static ShopActionResult setOwner(ServerPlayer player, String name, String memberName) {
        MinecraftServer server = player.server;
        Found found = asOwner(server, player.getUUID(), name);
        if (found.problem() != null) {
            return found.problem();
        }
        SharedAccounts.Account account = found.account();
        Optional<SharedAccounts.Member> member = memberNamed(server, account, memberName);
        if (member.isEmpty()) {
            return ShopActionResult.fail("account.reikoeconomy.not_a_member", memberName, account.name());
        }
        UUID newOwner = member.get().player();
        if (account.isOwner(newOwner)) {
            return ShopActionResult.fail("account.reikoeconomy.already_owner", account.name());
        }
        String shownName = nameOf(server, newOwner, member.get().name());
        int maxOwned = EconomyConfig.sharedAccountMaxOwned();
        if (SharedAccounts.get(server).ownedBy(newOwner) >= maxOwned) {
            return ShopActionResult.fail("account.reikoeconomy.they_own_too_many", shownName, maxOwned);
        }
        SharedAccounts.get(server).setOwner(account, newOwner);
        ShopNotices.alert(server, newOwner, Component.translatable("account.reikoeconomy.now_owner",
                player.getGameProfile().getName(), account.name()).withStyle(ChatFormatting.GOLD));
        return ShopActionResult.ok(Component.translatable("account.reikoeconomy.owner_set", shownName, account.name()));
    }

    /** The owner closes the account: its money goes to their bank account and every card for it stops working. */
    public static ShopActionResult close(ServerPlayer player, String name) {
        Found found = asOwner(player.server, player.getUUID(), name);
        if (found.problem() != null) {
            return found.problem();
        }
        return close(player.server, found.account(), player.getGameProfile().getName());
    }

    /** Closes an account, moving its money to the owner. {@code closedBy} is who to name to the members. */
    public static ShopActionResult close(MinecraftServer server, SharedAccounts.Account account, String closedBy) {
        long balance = ECO.getBalance(server, account.id());
        if (balance > 0) {
            TransactionResult moved = ECO.transfer(server, account.id(), account.owner(), balance, "shared.close");
            if (!moved.isSuccess()) {
                return ShopActionResult.fail(moved.translationKey());
            }
        }
        Ledger.get(server).remove(account.id());
        SharedAccounts.get(server).remove(account);
        for (SharedAccounts.Member member : account.members()) {
            Component message = account.isOwner(member.player()) && balance > 0
                    ? Component.translatable("account.reikoeconomy.closed_owner", closedBy, account.name(), ECO.format(balance))
                    : Component.translatable("account.reikoeconomy.closed_member", closedBy, account.name());
            ShopNotices.alert(server, member.player(), message.copy().withStyle(ChatFormatting.GOLD));
        }
        return ShopActionResult.ok(Component.translatable("account.reikoeconomy.closed", account.name(), ECO.format(balance)));
    }

    // ---- money ----

    public static ShopActionResult deposit(ServerPlayer player, String name, String amountText) {
        MinecraftServer server = player.server;
        Found found = asMember(server, player.getUUID(), name);
        if (found.problem() != null) {
            return found.problem();
        }
        OptionalLong amount = Money.parsePositive(amountText);
        if (amount.isEmpty()) {
            return ShopActionResult.fail(TransactionResult.INVALID_AMOUNT.translationKey());
        }
        SharedAccounts.Account account = found.account();
        TransactionResult result = ECO.transfer(server, player.getUUID(), account.id(), amount.getAsLong(), "shared.deposit");
        if (!result.isSuccess()) {
            return ShopActionResult.fail(result.translationKey());
        }
        return ShopActionResult.ok(Component.translatable("account.reikoeconomy.deposited", ECO.format(amount.getAsLong()),
                account.name(), ECO.format(ECO.getBalance(server, account.id()))));
    }

    public static ShopActionResult withdraw(ServerPlayer player, String name, String amountText) {
        MinecraftServer server = player.server;
        Found found = asMember(server, player.getUUID(), name);
        if (found.problem() != null) {
            return found.problem();
        }
        OptionalLong amount = Money.parsePositive(amountText);
        if (amount.isEmpty()) {
            return ShopActionResult.fail(TransactionResult.INVALID_AMOUNT.translationKey());
        }
        SharedAccounts.Account account = found.account();
        long balance = ECO.getBalance(server, account.id());
        if (balance < amount.getAsLong()) {
            return ShopActionResult.fail("account.reikoeconomy.not_enough", account.name(), ECO.format(balance));
        }
        TransactionResult result = ECO.transfer(server, account.id(), player.getUUID(), amount.getAsLong(), "shared.withdraw");
        if (!result.isSuccess()) {
            return ShopActionResult.fail(result.translationKey());
        }
        return ShopActionResult.ok(Component.translatable("account.reikoeconomy.withdrew", ECO.format(amount.getAsLong()),
                account.name(), ECO.format(ECO.getBalance(server, account.id()))));
    }

    /** A member pays another player from the account. It pays the /pay fee, like any payment to someone else. */
    public static ShopActionResult pay(ServerPlayer player, String name, GameProfile target, String amountText) {
        MinecraftServer server = player.server;
        Found found = asMember(server, player.getUUID(), name);
        if (found.problem() != null) {
            return found.problem();
        }
        OptionalLong parsed = Money.parsePositive(amountText);
        if (parsed.isEmpty()) {
            return ShopActionResult.fail(TransactionResult.INVALID_AMOUNT.translationKey());
        }
        if (!ECO.hasAccount(server, target.getId())) {
            return ShopActionResult.fail("account.reikoeconomy.unknown_player", target.getName());
        }
        SharedAccounts.Account account = found.account();
        long amount = parsed.getAsLong();
        long fee = target.getId().equals(player.getUUID()) ? 0 : EconomyConfig.taxRates().payFee(amount);
        long balance = ECO.getBalance(server, account.id());
        if (balance < amount + fee) {
            return fee > 0
                    ? ShopActionResult.fail("account.reikoeconomy.not_enough_fee", account.name(), ECO.format(amount + fee),
                            ECO.format(fee), ECO.format(balance))
                    : ShopActionResult.fail("account.reikoeconomy.not_enough", account.name(), ECO.format(balance));
        }
        return actingAs(player.getUUID(), () -> {
            TransactionResult result = ECO.transfer(server, account.id(), target.getId(), amount, "shared.pay");
            if (!result.isSuccess()) {
                return ShopActionResult.fail(result.translationKey());
            }
            boolean feePaid = fee > 0 && TaxService.take(server, account.id(), TaxKind.PAY, fee);
            ServerPlayer recipient = server.getPlayerList().getPlayer(target.getId());
            if (recipient != null && recipient != player) {
                recipient.sendSystemMessage(Component.translatable("account.reikoeconomy.paid_you",
                        player.getGameProfile().getName(), ECO.format(amount), account.name()).withStyle(ChatFormatting.GREEN));
            }
            return ShopActionResult.ok(feePaid
                    ? Component.translatable("account.reikoeconomy.paid_fee", ECO.format(amount), target.getName(), account.name(),
                            ECO.format(fee))
                    : Component.translatable("account.reikoeconomy.paid", ECO.format(amount), target.getName(), account.name()));
        });
    }

    // ---- looking ----

    /** The shared account called {@code name}, if {@code player} is in it, for history and the like. */
    public static Optional<SharedAccounts.Account> memberAccount(MinecraftServer server, UUID player, String name) {
        return Optional.ofNullable(asMember(server, player, name).account());
    }

    /** Why {@code player} can't see the account called {@code name}. */
    public static ShopActionResult whyNot(MinecraftServer server, UUID player, String name) {
        ShopActionResult problem = asMember(server, player, name).problem();
        return problem != null ? problem : ShopActionResult.fail("account.reikoeconomy.not_found", name);
    }

    /** Balance, owner and members of an account. */
    public static List<Component> describe(MinecraftServer server, SharedAccounts.Account account) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("account.reikoeconomy.info.header", account.name(),
                ECO.format(ECO.getBalance(server, account.id()))).withStyle(ChatFormatting.GOLD));
        SharedAccounts.Member owner = account.member(account.owner()).orElse(null);
        lines.add(Component.translatable("account.reikoeconomy.info.owner",
                nameOf(server, account.owner(), owner == null ? "?" : owner.name())));
        MutableComponent members = Component.empty();
        boolean first = true;
        for (SharedAccounts.Member member : account.members()) {
            if (!first) {
                members.append(", ");
            }
            first = false;
            members.append(nameOf(server, member.player(), member.name()));
        }
        lines.add(Component.translatable("account.reikoeconomy.info.members", account.members().size(), members));
        return lines;
    }

    /** One line per account {@code player} is in. */
    public static List<Component> list(MinecraftServer server, UUID player) {
        List<Component> lines = new ArrayList<>();
        List<SharedAccounts.Account> accounts = accountsOf(server, player);
        if (accounts.isEmpty()) {
            lines.add(Component.translatable("account.reikoeconomy.list.none"));
            return lines;
        }
        lines.add(Component.translatable("account.reikoeconomy.list.header").withStyle(ChatFormatting.GOLD));
        for (SharedAccounts.Account account : accounts) {
            String balance = ECO.format(ECO.getBalance(server, account.id()));
            lines.add(account.isOwner(player)
                    ? Component.translatable("account.reikoeconomy.list.owned", account.name(), balance)
                    : Component.translatable("account.reikoeconomy.list.member", account.name(), balance));
        }
        return lines;
    }

    /** Names of the accounts {@code player} is in, for command suggestions. */
    public static List<String> namesOf(MinecraftServer server, UUID player) {
        return accountsOf(server, player).stream().map(SharedAccounts.Account::name).toList();
    }

    /** Names of the other members of {@code player}'s account, for command suggestions. */
    public static List<String> otherMembers(MinecraftServer server, UUID player, String name) {
        List<String> names = new ArrayList<>();
        memberAccount(server, player, name).ifPresent(account -> {
            for (SharedAccounts.Member member : account.members()) {
                if (!member.player().equals(player)) {
                    names.add(nameOf(server, member.player(), member.name()));
                }
            }
        });
        return names;
    }
}
