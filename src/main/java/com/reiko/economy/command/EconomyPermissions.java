package com.reiko.economy.command;

import com.reiko.economy.ReikoEconomy;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * Permission nodes for the mod's commands. Permission mods can override any of these; without one, players
 * get the player commands and ops (level 2, or cheats on in singleplayer) get the admin commands.
 */
@EventBusSubscriber(modid = ReikoEconomy.MODID)
public final class EconomyPermissions {
    /** Op level needed for admin commands when no permission mod is installed. */
    public static final int ADMIN_OP_LEVEL = 2;

    public static final PermissionNode<Boolean> BALANCE = everyone("command.balance");
    public static final PermissionNode<Boolean> BALANCE_OTHERS = everyone("command.balance.others");
    public static final PermissionNode<Boolean> PAY = everyone("command.pay");
    public static final PermissionNode<Boolean> BALTOP = everyone("command.baltop");
    public static final PermissionNode<Boolean> HISTORY = everyone("command.history");
    public static final PermissionNode<Boolean> CREDIT = everyone("command.credit");
    public static final PermissionNode<Boolean> JOBS = everyone("command.jobs");
    public static final PermissionNode<Boolean> TAXES = everyone("command.taxes");
    public static final PermissionNode<Boolean> SHOPS = everyone("command.shops");
    public static final PermissionNode<Boolean> BANK = everyone("command.bank");
    public static final PermissionNode<Boolean> ADMIN = opOnly("command.admin");
    /** Place player shops. */
    public static final PermissionNode<Boolean> SHOP_CREATE = everyone("shop.create");
    /** Place, set up and break admin shops, and open or break any player's shop. */
    public static final PermissionNode<Boolean> SHOP_ADMIN = opOnly("shop.admin");

    private EconomyPermissions() {
    }

    @SubscribeEvent
    static void registerNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(BALANCE, BALANCE_OTHERS, PAY, BALTOP, HISTORY, CREDIT, JOBS, TAXES, SHOPS, BANK, ADMIN, SHOP_CREATE, SHOP_ADMIN);
    }

    public static boolean has(ServerPlayer player, PermissionNode<Boolean> node) {
        return PermissionAPI.getPermission(player, node);
    }

    /**
     * A command {@code requires} check: players are checked against the node; the server console and
     * command blocks fall back to the given op level.
     */
    static Predicate<CommandSourceStack> check(PermissionNode<Boolean> node, int fallbackOpLevel) {
        return source -> {
            ServerPlayer player = source.getPlayer();
            return player != null ? PermissionAPI.getPermission(player, node) : source.hasPermission(fallbackOpLevel);
        };
    }

    private static PermissionNode<Boolean> everyone(String name) {
        return new PermissionNode<>(ReikoEconomy.MODID, name, PermissionTypes.BOOLEAN,
                (player, uuid, context) -> true);
    }

    private static PermissionNode<Boolean> opOnly(String name) {
        return new PermissionNode<>(ReikoEconomy.MODID, name, PermissionTypes.BOOLEAN,
                (player, uuid, context) -> player != null && player.hasPermissions(ADMIN_OP_LEVEL));
    }
}
