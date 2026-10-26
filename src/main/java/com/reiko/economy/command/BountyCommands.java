package com.reiko.economy.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.bounty.BountyData;
import com.reiko.economy.bounty.BountyService;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.Money;
import com.reiko.economy.shop.ShopActionResult;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /bounty place <player> <amount>}: put money on a player. {@code /bounty} or {@code /bounties}: the board.
 * {@code /bounty mine}: your bounties, and what's on you. Admins: {@code /eco bounty clear <player>}.
 */
final class BountyCommands {
    private static final SimpleCommandExceptionType ONE_PLAYER = new SimpleCommandExceptionType(
            Component.translatable("commands.reikoeconomy.one_player"));
    private static final SimpleCommandExceptionType INVALID_AMOUNT = new SimpleCommandExceptionType(
            Component.translatable(TransactionResult.INVALID_AMOUNT.translationKey()));

    private BountyCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> bounty() {
        return Commands.literal("bounty")
                .requires(EconomyPermissions.check(EconomyPermissions.BOUNTY, 0))
                .executes(ctx -> board(ctx.getSource(), 1))
                .then(Commands.literal("list")
                        .executes(ctx -> board(ctx.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> board(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("place")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(BountyCommands::place))))
                .then(Commands.literal("mine").executes(BountyCommands::mine));
    }

    /** {@code /bounties [page]}: the board. */
    static LiteralArgumentBuilder<CommandSourceStack> bounties() {
        return Commands.literal("bounties")
                .requires(EconomyPermissions.check(EconomyPermissions.BOUNTY, 0))
                .executes(ctx -> board(ctx.getSource(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> board(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page"))));
    }

    private static int place(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer poster = ctx.getSource().getPlayerOrException();
        OptionalLong amount = Money.parsePositive(StringArgumentType.getString(ctx, "amount"));
        if (amount.isEmpty()) {
            throw INVALID_AMOUNT.create();
        }
        ShopActionResult result = BountyService.post(poster, profile(ctx), amount.getAsLong());
        if (result.success()) {
            ctx.getSource().sendSuccess(result::message, false);
            return 1;
        }
        ctx.getSource().sendFailure(result.message());
        return 0;
    }

    private static int board(CommandSourceStack source, int page) {
        MinecraftServer server = source.getServer();
        List<BountyService.Target> targets = BountyService.board(server);
        if (targets.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("bounty.reikoeconomy.board.empty"), false);
            return 0;
        }
        int pages = (targets.size() + BountyService.PAGE_SIZE - 1) / BountyService.PAGE_SIZE;
        int shown = Math.min(page, pages);
        int start = (shown - 1) * BountyService.PAGE_SIZE;
        int end = Math.min(start + BountyService.PAGE_SIZE, targets.size());
        source.sendSuccess(() -> Component.translatable("bounty.reikoeconomy.board.header", shown, pages)
                .withStyle(ChatFormatting.GOLD), false);
        EconomyService eco = EconomyService.INSTANCE;
        for (int i = start; i < end; i++) {
            BountyService.Target target = targets.get(i);
            boolean online = server.getPlayerList().getPlayer(target.player()) != null;
            Component name = Component.literal(target.name()).withStyle(online ? ChatFormatting.GREEN : ChatFormatting.GRAY);
            Component line = Component.translatable(target.count() == 1 ? "bounty.reikoeconomy.board.line_one"
                            : "bounty.reikoeconomy.board.line", i + 1, name, eco.format(target.total()), target.count(),
                    BountyService.timeLeft(target.firstExpiry()));
            source.sendSuccess(() -> line, false);
        }
        source.sendSuccess(() -> Component.translatable("bounty.reikoeconomy.board.footer").withStyle(ChatFormatting.GRAY), false);
        return end - start;
    }

    private static int mine(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BountyData data = BountyData.get(player.server);
        EconomyService eco = EconomyService.INSTANCE;
        long onMe = data.totalOn(player.getUUID());
        ctx.getSource().sendSuccess(() -> onMe > 0
                ? Component.translatable("bounty.reikoeconomy.mine.on_you", eco.format(onMe)).withStyle(ChatFormatting.RED)
                : Component.translatable("bounty.reikoeconomy.mine.none_on_you"), false);
        List<BountyData.Bounty> posted = data.postedBy(player.getUUID());
        if (posted.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("bounty.reikoeconomy.mine.none_posted"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("bounty.reikoeconomy.mine.posted_header").withStyle(ChatFormatting.GOLD), false);
        for (BountyData.Bounty bounty : posted) {
            Component line = Component.translatable("bounty.reikoeconomy.mine.posted_line", eco.format(bounty.amount()),
                    bounty.targetName(), BountyService.timeLeft(bounty.expiresAt()));
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return posted.size();
    }

    private static GameProfile profile(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        if (profiles.size() != 1) {
            throw ONE_PLAYER.create();
        }
        return profiles.iterator().next();
    }

    /** {@code /eco bounty clear <player>}: takes down every bounty on a player and refunds the posters. */
    static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("bounty")
                .then(Commands.literal("clear")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> {
                                    GameProfile target = profile(ctx);
                                    int cleared = BountyService.clear(ctx.getSource().getServer(), target.getId());
                                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.reikoeconomy.bounty.cleared",
                                            cleared, target.getName()), true);
                                    return cleared;
                                })));
    }
}
