package com.reiko.economy.api;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Fired on {@code NeoForge.EVENT_BUS} around every balance change made through {@link EconomyApi}.
 *
 * <ul>
 *   <li>{@link Pre} fires after the change has been validated but before it is applied. Cancel it to
 *       block the change; the caller then gets {@link TransactionResult#CANCELLED}.</li>
 *   <li>{@link Post} fires after the change has been applied.</li>
 * </ul>
 *
 * <p>Amounts are in stored units (hundredths). For {@link TransactionType#SET} the amount is the new
 * balance. {@code from} is null for deposits and sets; {@code to} is null for withdrawals.
 */
public abstract class TransactionEvent extends Event {
    private final MinecraftServer server;
    private final TransactionType type;
    @Nullable
    private final UUID from;
    @Nullable
    private final UUID to;
    private final long amount;
    private final String reason;

    protected TransactionEvent(MinecraftServer server, TransactionType type, @Nullable UUID from, @Nullable UUID to,
            long amount, String reason) {
        this.server = server;
        this.type = type;
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.reason = reason;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public TransactionType getType() {
        return type;
    }

    /** Account money leaves, or null for deposits and sets. */
    @Nullable
    public UUID getFrom() {
        return from;
    }

    /** Account money arrives in, or null for withdrawals. */
    @Nullable
    public UUID getTo() {
        return to;
    }

    public long getAmount() {
        return amount;
    }

    /** Short machine-readable reason supplied by the caller, e.g. {@code command.pay}. */
    public String getReason() {
        return reason;
    }

    /** Fired before the change is applied. Cancel to block it. */
    public static final class Pre extends TransactionEvent implements ICancellableEvent {
        public Pre(MinecraftServer server, TransactionType type, @Nullable UUID from, @Nullable UUID to, long amount,
                String reason) {
            super(server, type, from, to, amount, reason);
        }
    }

    /** Fired after the change has been applied. */
    public static final class Post extends TransactionEvent {
        public Post(MinecraftServer server, TransactionType type, @Nullable UUID from, @Nullable UUID to, long amount,
                String reason) {
            super(server, type, from, to, amount, reason);
        }
    }
}
