package com.reiko.economy.trade;

import com.reiko.economy.config.EconomyConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * One trade between two players. Each side has item slots and an amount of money from their bank account.
 * The items sit here, out of the players' inventories, until the trade ends: on a successful trade they go to
 * the other player, otherwise back to whoever put them in. Money isn't held; it's checked and moved at the end.
 *
 * <p>Any change to either offer clears both accepts, bumps {@link #revision()} and starts a short lock, so
 * nobody can swap something at the last second and have it accepted unseen.
 */
public final class TradeSession {
    private final Side first;
    private final Side second;
    /** Bumps whenever either offer changes. An accept names the revision the player saw. */
    private int revision;
    /** Bumps whenever anything the screens show changes, so each screen knows when to refresh. */
    private int version;
    private long lockedUntil;
    private boolean finished;

    /** One player's half of the trade. */
    public static final class Side {
        private final ServerPlayer player;
        private final SimpleContainer items = new SimpleContainer(TradeRules.OFFER_SLOTS);
        private long money;
        private boolean accepted;

        private Side(ServerPlayer player) {
            this.player = player;
        }

        public ServerPlayer player() {
            return player;
        }

        public SimpleContainer items() {
            return items;
        }

        public long money() {
            return money;
        }

        public boolean accepted() {
            return accepted;
        }

        public boolean isEmpty() {
            return money == 0 && items.isEmpty();
        }

        /** Copies of the items offered, without the empty slots. */
        public List<ItemStack> stacks() {
            List<ItemStack> stacks = new ArrayList<>();
            for (int i = 0; i < items.getContainerSize(); i++) {
                if (!items.getItem(i).isEmpty()) {
                    stacks.add(items.getItem(i).copy());
                }
            }
            return stacks;
        }
    }

    TradeSession(ServerPlayer first, ServerPlayer second) {
        this.first = new Side(first);
        this.second = new Side(second);
        this.first.items.addListener(container -> offerChanged());
        this.second.items.addListener(container -> offerChanged());
    }

    public Side first() {
        return first;
    }

    public Side second() {
        return second;
    }

    /** The side {@code player} is on. They must be one of the two traders. */
    public Side side(Player player) {
        return player.getUUID().equals(first.player.getUUID()) ? first : second;
    }

    public Side other(Side side) {
        return side == first ? second : first;
    }

    public boolean involves(UUID player) {
        return first.player.getUUID().equals(player) || second.player.getUUID().equals(player);
    }

    public int revision() {
        return revision;
    }

    public int version() {
        return version;
    }

    public long lockedUntil() {
        return lockedUntil;
    }

    public boolean finished() {
        return finished;
    }

    void finish() {
        finished = true;
        version++;
    }

    void setMoney(Side side, long money) {
        side.money = money;
        offerChanged();
    }

    void setAccepted(Side side, boolean accepted) {
        side.accepted = accepted;
        version++;
    }

    /** Clears both accepts without changing the offers, after a trade couldn't go through. */
    void clearAccepts() {
        first.accepted = false;
        second.accepted = false;
        version++;
    }

    private void offerChanged() {
        if (finished) {
            return; // items moving out at the end
        }
        revision++;
        lockedUntil = TradeService.now() + EconomyConfig.tradeRules().lockMillis();
        clearAccepts();
    }
}
