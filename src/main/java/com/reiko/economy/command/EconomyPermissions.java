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
    public static final PermissionNode<Boolean> ADMIN = new PermissionNode<>(ReikoEconomy.MODID, "command.admin",
            PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && player.hasPermissions(ADMIN_OP_LEVEL));

    private EconomyPermissions() {
    }

    @SubscribeEvent
    static void registerNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(BALANCE, BALANCE_OTHERS, PAY, BALTOP, ADMIN);
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
}
