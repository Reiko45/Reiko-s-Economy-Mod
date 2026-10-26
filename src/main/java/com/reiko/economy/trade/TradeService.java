package com.reiko.economy.trade;

import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.bank.InventorySpace;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.Money;
import com.reiko.economy.core.TaxKind;
import com.reiko.economy.core.TaxService;
import com.reiko.economy.menu.TradeMenu;
import com.reiko.economy.network.ShopFeedback;
import com.reiko.economy.network.TradeStatePayload;
import com.reiko.economy.shop.ShopActionResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Trade requests and trades. A player asks another with {@code /trade <player>} or by sneaking and
 * right-clicking them; the other accepts from the chat buttons or {@code /trade accept}. Both then get a trade
 * window. Nothing changes hands until both accept the same offers, and the trade ends if either closes the
 * window, leaves, dies or walks too far away.
 *
 * <p>All of this lives in memory: requests and trades don't survive a restart, and items in a trade go back to
 * their owners when the server shuts down (players are logged out first).
 */
public final class TradeService {
    private static final EconomyService ECO = EconomyService.INSTANCE;
    private static final ShopActionResult SILENT = ShopActionResult.ok(Component.empty());
    private static final int MAX_LISTED_STACKS = 3;

    /** Outgoing requests, by who sent them. A player has at most one out at a time. */
    private static final Map<UUID, Request> REQUESTS = new LinkedHashMap<>();
    /** Running trades, under both players. */
    private static final Map<UUID, TradeSession> SESSIONS = new HashMap<>();
    private static LongSupplier clock = System::currentTimeMillis;

    private record Request(UUID from, String fromName, UUID to, String toName, long expiresAt) {
    }

    /** Why a trade ended. */
    public enum End {
        DONE, CLOSED, LEFT, DIED, TOO_FAR, DISABLED, FAILED
    }

    private TradeService() {
    }

    /** Replaces the clock for tests, or restores the real one with {@code null}. */
    public static void setClockForTests(@Nullable LongSupplier testClock) {
        clock = testClock == null ? System::currentTimeMillis : testClock;
    }

    public static long now() {
        return clock.getAsLong();
    }

    /** Whether a result has nothing to tell the player. */
    public static boolean isSilent(ShopActionResult result) {
        return result.message().getContents() == PlainTextContents.EMPTY && result.message().getSiblings().isEmpty();
    }

    @Nullable
    public static TradeSession session(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    // ---- requests ----

    /** {@code from} asks {@code to} to trade. If {@code to} already asked {@code from}, the trade starts. */
    public static ShopActionResult request(ServerPlayer from, ServerPlayer to) {
        TradeRules rules = EconomyConfig.tradeRules();
        ShopActionResult blocked = blocked(from, to, rules);
        if (blocked != null) {
            return blocked;
        }
        Request theirs = REQUESTS.get(to.getUUID());
        if (theirs != null && theirs.to().equals(from.getUUID()) && theirs.expiresAt() > now()) {
            return start(to, from);
        }
        Request mine = REQUESTS.get(from.getUUID());
        if (mine != null && mine.to().equals(to.getUUID()) && mine.expiresAt() > now()) {
            return ShopActionResult.fail("trade.reikoeconomy.request.already", to.getDisplayName(), secondsLeft(mine));
        }
        if (mine != null) {
            REQUESTS.remove(from.getUUID()); // asking someone new replaces the old request
        }
        long seconds = rules.requestMillis() / 1000;
        REQUESTS.put(from.getUUID(), new Request(from.getUUID(), from.getGameProfile().getName(), to.getUUID(),
                to.getGameProfile().getName(), now() + rules.requestMillis()));
        to.sendSystemMessage(Component.translatable("trade.reikoeconomy.request.received", from.getDisplayName())
                .append(" ").append(button("accept", ChatFormatting.GREEN, "/trade accept " + from.getGameProfile().getName()))
                .append(" ").append(button("deny", ChatFormatting.RED, "/trade deny " + from.getGameProfile().getName())));
        to.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
        return ShopActionResult.ok(Component.translatable("trade.reikoeconomy.request.sent", to.getDisplayName(), seconds));
    }

    /**
     * {@code player} accepts a request: the one from {@code from}, or the latest one if {@code from} is null.
     */
    public static ShopActionResult accept(ServerPlayer player, @Nullable ServerPlayer from) {
        Request request = findRequest(player, from);
        if (request == null) {
            return from == null ? ShopActionResult.fail("trade.reikoeconomy.request.none")
                    : ShopActionResult.fail("trade.reikoeconomy.request.none_from", from.getDisplayName());
        }
        ServerPlayer sender = player.server.getPlayerList().getPlayer(request.from());
        if (sender == null) {
            REQUESTS.remove(request.from());
            return ShopActionResult.fail("trade.reikoeconomy.request.none_from", request.fromName());
        }
        ShopActionResult blocked = blocked(player, sender, EconomyConfig.tradeRules());
        if (blocked != null) {
            return blocked;
        }
        return start(sender, player);
    }

    /** {@code player} turns down a request: the one from {@code from}, or the latest one. */
    public static ShopActionResult deny(ServerPlayer player, @Nullable ServerPlayer from) {
        Request request = findRequest(player, from);
        if (request == null) {
            return from == null ? ShopActionResult.fail("trade.reikoeconomy.request.none")
                    : ShopActionResult.fail("trade.reikoeconomy.request.none_from", from.getDisplayName());
        }
        REQUESTS.remove(request.from());
        ServerPlayer sender = player.server.getPlayerList().getPlayer(request.from());
        if (sender != null) {
            sender.sendSystemMessage(Component.translatable("trade.reikoeconomy.request.declined", player.getDisplayName()));
        }
        return ShopActionResult.ok(Component.translatable("trade.reikoeconomy.request.you_declined", request.fromName()));
    }

    /** {@code player} takes back the request they sent. */
    public static ShopActionResult withdraw(ServerPlayer player) {
        Request request = REQUESTS.remove(player.getUUID());
        if (request == null || request.expiresAt() <= now()) {
            return ShopActionResult.fail("trade.reikoeconomy.request.none_sent");
        }
        ServerPlayer target = player.server.getPlayerList().getPlayer(request.to());
        if (target != null) {
            target.sendSystemMessage(Component.translatable("trade.reikoeconomy.request.withdrawn_them", player.getDisplayName())
                    .withStyle(ChatFormatting.GRAY));
        }
        return ShopActionResult.ok(Component.translatable("trade.reikoeconomy.request.withdrawn", request.toName()));
    }

    /** Names of players with a live request to {@code player}, newest first, for command suggestions. */
    public static List<String> requestersOf(ServerPlayer player) {
        List<String> names = new ArrayList<>();
        for (Request request : REQUESTS.values()) {
            if (request.to().equals(player.getUUID()) && request.expiresAt() > now()) {
                names.add(0, request.fromName());
            }
        }
        return names;
    }

    @Nullable
    private static Request findRequest(ServerPlayer player, @Nullable ServerPlayer from) {
        Request found = null;
        for (Request request : REQUESTS.values()) {
            if (request.to().equals(player.getUUID()) && request.expiresAt() > now()
                    && (from == null || request.from().equals(from.getUUID()))) {
                found = request; // later entries are newer
            }
        }
        return found;
    }

    /** Why these two can't start a trade right now, or null if they can. */
    @Nullable
    private static ShopActionResult blocked(ServerPlayer player, ServerPlayer other, TradeRules rules) {
        if (!rules.enabled()) {
            return ShopActionResult.fail("trade.reikoeconomy.disabled");
        }
        if (player.getUUID().equals(other.getUUID())) {
            return ShopActionResult.fail("trade.reikoeconomy.self");
        }
        if (SESSIONS.containsKey(player.getUUID())) {
            return ShopActionResult.fail("trade.reikoeconomy.busy_you");
        }
        if (SESSIONS.containsKey(other.getUUID())) {
            return ShopActionResult.fail("trade.reikoeconomy.busy", other.getDisplayName());
        }
        if (other.isSpectator() || player.isSpectator()) {
            return ShopActionResult.fail("trade.reikoeconomy.spectator");
        }
        if (!rules.inRange(player, other)) {
            return ShopActionResult.fail("trade.reikoeconomy.too_far", other.getDisplayName(), rules.maxDistance());
        }
        return null;
    }

    private static long secondsLeft(Request request) {
        return Math.max(1, (request.expiresAt() - now() + 999) / 1000);
    }

    private static Component button(String kind, ChatFormatting color, String command) {
        return Component.translatable("trade.reikoeconomy.request." + kind + "_button").withStyle(style -> style
                .withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("trade.reikoeconomy.request." + kind + "_hover"))));
    }

    // ---- starting and ending ----

    private static ShopActionResult start(ServerPlayer first, ServerPlayer second) {
        REQUESTS.remove(first.getUUID());
        REQUESTS.remove(second.getUUID());
        TradeSession session = new TradeSession(first, second);
        SESSIONS.put(first.getUUID(), session);
        SESSIONS.put(second.getUUID(), session);
        if (!open(first, second, session) || !open(second, first, session)) {
            end(session, End.FAILED, null); // another mod stopped a window from opening
        }
        return SILENT;
    }

    private static boolean open(ServerPlayer player, ServerPlayer partner, TradeSession session) {
        OptionalInt id = player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> new TradeMenu(containerId, inventory, session),
                Component.translatable("container.reikoeconomy.trade", partner.getDisplayName())));
        return id.isPresent() && !session.finished();
    }

    /** Called when a trade window closes, for whatever reason. Ends the trade if it's still going. */
    public static void closed(ServerPlayer player, TradeSession session) {
        end(session, End.CLOSED, player);
    }

    /** A player logged out: drop their requests and end their trade, giving items back before they're saved. */
    public static void playerLeft(ServerPlayer player) {
        REQUESTS.remove(player.getUUID());
        REQUESTS.values().removeIf(request -> request.to().equals(player.getUUID()));
        TradeSession session = SESSIONS.get(player.getUUID());
        if (session != null) {
            end(session, End.LEFT, player);
        }
    }

    /** A player died: end their trade first, so their offer goes back to their inventory and drops with it. */
    public static void playerDied(ServerPlayer player) {
        TradeSession session = SESSIONS.get(player.getUUID());
        if (session != null) {
            end(session, End.DIED, player);
        }
    }

    /**
     * Ends a trade without a swap: items go back to whoever offered them, both windows close and both players
     * are told why. {@code cause} is the player who closed, left or died, if any.
     */
    public static void end(TradeSession session, End reason, @Nullable ServerPlayer cause) {
        if (session.finished()) {
            return;
        }
        session.finish();
        forget(session);
        for (TradeSession.Side side : List.of(session.first(), session.second())) {
            boolean hadItems = !side.items().isEmpty();
            giveBack(side.player(), side.items());
            closeWindow(side.player(), session);
            Component message = cancelMessage(reason, side.player(), session.other(side).player(), cause);
            if (message != null) {
                MutableComponent text = message.copy();
                if (hadItems) {
                    text.append(" ").append(Component.translatable("trade.reikoeconomy.given_back"));
                }
                side.player().sendSystemMessage(text.withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    @Nullable
    private static Component cancelMessage(End reason, ServerPlayer player, ServerPlayer partner, @Nullable ServerPlayer cause) {
        boolean byMe = cause != null && cause.getUUID().equals(player.getUUID());
        return switch (reason) {
            case CLOSED -> byMe ? Component.translatable("trade.reikoeconomy.cancelled.you", partner.getDisplayName())
                    : Component.translatable("trade.reikoeconomy.cancelled.them", partner.getDisplayName());
            case LEFT -> byMe ? null : Component.translatable("trade.reikoeconomy.cancelled.left", partner.getDisplayName());
            case DIED -> byMe ? Component.translatable("trade.reikoeconomy.cancelled.you_died")
                    : Component.translatable("trade.reikoeconomy.cancelled.died", partner.getDisplayName());
            case TOO_FAR -> Component.translatable("trade.reikoeconomy.cancelled.too_far", partner.getDisplayName());
            case DISABLED -> Component.translatable("trade.reikoeconomy.cancelled.disabled");
            case FAILED -> Component.translatable("trade.reikoeconomy.cancelled.failed");
            case DONE -> null;
        };
    }

    private static void forget(TradeSession session) {
        SESSIONS.remove(session.first().player().getUUID(), session);
        SESSIONS.remove(session.second().player().getUUID(), session);
    }

    /** Empties an offer back into its owner's inventory, dropping what doesn't fit. */
    private static void giveBack(ServerPlayer player, SimpleContainer items) {
        for (int i = 0; i < items.getContainerSize(); i++) {
            ItemStack stack = items.removeItemNoUpdate(i);
            if (!stack.isEmpty()) {
                giveBack(player, stack);
            }
        }
    }

    private static void giveBack(ServerPlayer player, ItemStack stack) {
        if (player.isRemoved()) {
            player.drop(stack, false);
        } else {
            player.getInventory().placeItemBackInInventory(stack);
        }
    }

    /**
     * Closes the player's trade window, unless it's the one closing right now. The item on their cursor goes
     * into their inventory first, so it's saved even if they're logging out.
     */
    private static void closeWindow(ServerPlayer player, TradeSession session) {
        if (player.containerMenu instanceof TradeMenu menu && menu.session() == session && !menu.isClosing()) {
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty()) {
                menu.setCarried(ItemStack.EMPTY);
                giveBack(player, carried);
            }
            player.closeContainer();
        }
    }

    // ---- inside the trade window ----

    /** Sets how much money {@code player} offers. Blank means none. */
    public static ShopActionResult offerMoney(ServerPlayer player, TradeSession session, String text) {
        if (session.finished()) {
            return SILENT;
        }
        TradeSession.Side me = session.side(player);
        OptionalLong parsed = text.isBlank() ? OptionalLong.of(0) : Money.parse(text);
        if (parsed.isEmpty()) {
            return ShopActionResult.fail(TransactionResult.INVALID_AMOUNT.translationKey());
        }
        long amount = parsed.getAsLong();
        if (amount == me.money()) {
            return SILENT;
        }
        if (amount > 0) {
            ShopActionResult afford = canAfford(player, amount);
            if (afford != null) {
                return afford;
            }
        }
        session.setMoney(me, amount);
        if (amount == 0) {
            return ShopActionResult.ok(Component.translatable("trade.reikoeconomy.money_cleared"));
        }
        long fee = fee(amount);
        return ShopActionResult.ok(fee > 0
                ? Component.translatable("trade.reikoeconomy.money_set_fee", ECO.format(amount), ECO.format(fee))
                : Component.translatable("trade.reikoeconomy.money_set", ECO.format(amount)));
    }

    /**
     * {@code player} accepts the offers as of {@code revision}. When both have accepted, the trade happens.
     */
    public static ShopActionResult accept(ServerPlayer player, TradeSession session, int revision) {
        if (session.finished()) {
            return SILENT;
        }
        TradeSession.Side me = session.side(player);
        TradeSession.Side them = session.other(me);
        if (me.accepted()) {
            return SILENT;
        }
        if (revision != session.revision()) {
            return ShopActionResult.fail("trade.reikoeconomy.changed");
        }
        if (now() < session.lockedUntil()) {
            return ShopActionResult.fail("trade.reikoeconomy.locked");
        }
        if (me.isEmpty() && them.isEmpty()) {
            return ShopActionResult.fail("trade.reikoeconomy.empty");
        }
        if (me.money() > 0) {
            ShopActionResult afford = canAfford(player, me.money());
            if (afford != null) {
                return afford;
            }
        }
        session.setAccepted(me, true);
        if (!them.accepted()) {
            return ShopActionResult.ok(Component.translatable("trade.reikoeconomy.accepted", them.player().getDisplayName()));
        }
        return complete(session, player);
    }

    /** {@code player} takes back their accept. */
    public static ShopActionResult unaccept(ServerPlayer player, TradeSession session) {
        if (!session.finished()) {
            session.setAccepted(session.side(player), false);
        }
        return SILENT;
    }

    /**
     * Both accepted: check everything once more, then move the money and items in one go. {@code accepting} is
     * the player whose accept finished it; what this returns is shown to them.
     */
    private static ShopActionResult complete(TradeSession session, ServerPlayer accepting) {
        TradeSession.Side a = session.first();
        TradeSession.Side b = session.second();
        MinecraftServer server = a.player().server;

        for (TradeSession.Side side : List.of(a, b)) {
            if (side.money() > 0 && canAfford(side.player(), side.money()) != null) {
                return refuse(session, side, "no_money", accepting);
            }
        }
        List<ItemStack> toA = b.stacks();
        List<ItemStack> toB = a.stacks();
        if (!InventorySpace.addAll(InventorySpace.copyOf(a.player().getInventory().items), toA)) {
            return refuse(session, a, "no_room", accepting);
        }
        if (!InventorySpace.addAll(InventorySpace.copyOf(b.player().getInventory().items), toB)) {
            return refuse(session, b, "no_room", accepting);
        }

        if (a.money() > 0 && ECO.transfer(server, a.player().getUUID(), b.player().getUUID(), a.money(), "trade") != TransactionResult.SUCCESS) {
            return refuse(session, a, "money_failed", accepting);
        }
        if (b.money() > 0 && ECO.transfer(server, b.player().getUUID(), a.player().getUUID(), b.money(), "trade") != TransactionResult.SUCCESS) {
            if (a.money() > 0) {
                ECO.transfer(server, b.player().getUUID(), a.player().getUUID(), a.money(), "trade.refund");
            }
            return refuse(session, b, "money_failed", accepting);
        }
        // Checked above, so only another mod cancelling the withdrawal could stop a fee.
        for (TradeSession.Side side : List.of(a, b)) {
            long fee = fee(side.money());
            if (fee > 0) {
                TaxService.take(server, side.player().getUUID(), TaxKind.TRADE, fee);
            }
        }

        session.finish();
        forget(session);
        a.items().clearContent();
        b.items().clearContent();
        give(a.player(), toA);
        give(b.player(), toB);
        for (TradeSession.Side side : List.of(a, b)) {
            TradeSession.Side other = session.other(side);
            closeWindow(side.player(), session);
            side.player().sendSystemMessage(Component.translatable("trade.reikoeconomy.done", other.player().getDisplayName(),
                    describe(side == a ? toB : toA, side.money()), describe(side == a ? toA : toB, other.money()))
                    .withStyle(ChatFormatting.GREEN));
            side.player().playNotifySound(SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.5F, 1.0F);
        }
        return SILENT;
    }

    /**
     * The trade can't go through: clear both accepts and tell both players why. The player who just accepted
     * gets the returned message; the other is sent theirs.
     */
    private static ShopActionResult refuse(TradeSession session, TradeSession.Side problem, String why, ServerPlayer accepting) {
        session.clearAccepts();
        ShopActionResult toProblem = ShopActionResult.fail("trade.reikoeconomy." + why + ".you");
        ShopActionResult toOther = ShopActionResult.fail("trade.reikoeconomy." + why + ".them", problem.player().getDisplayName());
        if (problem.player() == accepting) {
            ShopFeedback.send(session.other(problem).player(), toOther);
            return toProblem;
        }
        ShopFeedback.send(problem.player(), toProblem);
        return toOther;
    }

    private static void give(ServerPlayer player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            ItemStack left = stack.copy();
            player.getInventory().add(left);
            if (!left.isEmpty()) {
                player.drop(left, false); // can't happen: room was checked
            }
        }
        player.getInventory().setChanged();
    }

    /** "2 Diamond, 16 Oak Log and $50.00", or "nothing". */
    static Component describe(List<ItemStack> stacks, long money) {
        List<Component> parts = new ArrayList<>();
        for (int i = 0; i < stacks.size() && i < MAX_LISTED_STACKS; i++) {
            parts.add(Component.translatable("trade.reikoeconomy.stack", stacks.get(i).getCount(), stacks.get(i).getHoverName()));
        }
        if (stacks.size() > MAX_LISTED_STACKS) {
            parts.add(Component.translatable("trade.reikoeconomy.more_stacks", stacks.size() - MAX_LISTED_STACKS));
        }
        if (money > 0) {
            parts.add(Component.literal(ECO.format(money)));
        }
        if (parts.isEmpty()) {
            return Component.translatable("trade.reikoeconomy.nothing");
        }
        MutableComponent text = Component.empty().append(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            text.append(i == parts.size() - 1 ? Component.translatable("trade.reikoeconomy.and") : Component.literal(", "));
            text.append(parts.get(i));
        }
        return text;
    }

    /** The /pay fee, which money in a trade pays too. */
    public static long fee(long amount) {
        return amount > 0 ? EconomyConfig.taxRates().payFee(amount) : 0;
    }

    /** Why {@code player} can't pay {@code amount} plus its fee, or null if they can. */
    @Nullable
    private static ShopActionResult canAfford(ServerPlayer player, long amount) {
        long fee = fee(amount);
        long balance = ECO.getBalance(player.server, player.getUUID());
        if (balance >= amount + fee) {
            return null;
        }
        return fee > 0
                ? ShopActionResult.fail("trade.reikoeconomy.cant_afford_fee", ECO.format(amount + fee), ECO.format(amount),
                        ECO.format(fee), ECO.format(balance))
                : ShopActionResult.fail("trade.reikoeconomy.cant_afford", ECO.format(balance));
    }

    // ---- keeping things tidy ----

    /** Expires old requests and ends trades whose players left, moved apart, or where trading was turned off. */
    public static void tick(MinecraftServer server) {
        long now = now();
        for (Iterator<Request> it = REQUESTS.values().iterator(); it.hasNext(); ) {
            Request request = it.next();
            if (request.expiresAt() <= now) {
                it.remove();
                ServerPlayer sender = server.getPlayerList().getPlayer(request.from());
                if (sender != null) {
                    sender.sendSystemMessage(Component.translatable("trade.reikoeconomy.request.expired", request.toName())
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }
        TradeRules rules = EconomyConfig.tradeRules();
        Set<TradeSession> sessions = new LinkedHashSet<>(SESSIONS.values());
        for (TradeSession session : sessions) {
            ServerPlayer a = session.first().player();
            ServerPlayer b = session.second().player();
            if (!rules.enabled()) {
                end(session, End.DISABLED, null);
            } else if (gone(server, a)) {
                end(session, End.LEFT, a);
            } else if (gone(server, b)) {
                end(session, End.LEFT, b);
            } else if (!rules.inRange(a, b)) {
                end(session, End.TOO_FAR, null);
            }
        }
    }

    private static boolean gone(MinecraftServer server, ServerPlayer player) {
        return player.isRemoved() || server.getPlayerList().getPlayer(player.getUUID()) != player;
    }

    /** Sends {@code player}'s screen the money, accepts and lock as they stand. */
    public static void sendState(ServerPlayer player, TradeMenu menu, TradeSession session) {
        if (!player.connection.hasChannel(TradeStatePayload.TYPE)) {
            return;
        }
        TradeSession.Side me = session.side(player);
        TradeSession.Side them = session.other(me);
        long lockLeft = Math.max(0, session.lockedUntil() - now());
        PacketDistributor.sendToPlayer(player, new TradeStatePayload(menu.containerId, me.money(), them.money(), fee(me.money()),
                me.accepted(), them.accepted(), session.revision(), (int) Math.min(lockLeft, Integer.MAX_VALUE)));
    }

    /** Forgets everything, when the server stops. */
    public static void clear() {
        REQUESTS.clear();
        SESSIONS.clear();
    }
}
