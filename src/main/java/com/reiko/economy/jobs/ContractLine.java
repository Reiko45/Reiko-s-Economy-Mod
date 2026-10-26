package com.reiko.economy.jobs;

import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * One possible contract from a job file's {@code "contracts"} list: what to deliver or do, how many (a random
 * number between {@code minCount} and {@code maxCount} each time it's given out), and the reward per unit.
 *
 * @param matches    tests a block state (break), entity (kill, breed) or item stack (deliver, fish)
 * @param label      what to call the target ("Iron Ore"), worked out when shown
 * @param icon       the item shown next to the contract
 * @param payCents   reward per unit, in cents, before the server's job pay multiplier
 * @param xp         job XP per unit
 * @param matureOnly for crops: only fully grown ones count, even if the player planted them
 */
public record ContractLine(ContractKind kind, Predicate<Object> matches, Supplier<Component> label, Supplier<ItemStack> icon,
                           int minCount, int maxCount, double payCents, int xp, boolean matureOnly) {
}
