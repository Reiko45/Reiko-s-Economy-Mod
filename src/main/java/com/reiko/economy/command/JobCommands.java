package com.reiko.economy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reiko.economy.jobs.JobDefinition;
import com.reiko.economy.jobs.JobDefinitions;
import com.reiko.economy.jobs.JobService;
import com.reiko.economy.jobs.JobView;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** {@code /jobs}: your jobs and levels, and {@code /jobs info <job>}: what a job pays at your level. */
final class JobCommands {
    private JobCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> jobs() {
        return Commands.literal("jobs")
                .requires(EconomyPermissions.check(EconomyPermissions.JOBS, 0))
                .executes(ctx -> mine(ctx.getSource()))
                .then(Commands.literal("info")
                        .then(Commands.argument("job", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                        JobDefinitions.all().stream().map(JobDefinition::id), builder))
                                .executes(ctx -> info(ctx.getSource(), ResourceLocationArgument.getId(ctx, "job")))));
    }

    private static int mine(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<JobView> mine = JobService.views(player).stream().filter(JobView::active).toList();
        if (mine.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.reikoeconomy.jobs.none"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.reikoeconomy.jobs.header").withStyle(ChatFormatting.GOLD), false);
        for (JobView view : mine) {
            source.sendSuccess(() -> view.xpToNext() > 0
                    ? Component.translatable("commands.reikoeconomy.jobs.line", view.name(), view.level(), view.xp(), view.xpToNext(), view.bonus())
                    : Component.translatable("commands.reikoeconomy.jobs.line_max", view.name(), view.level(), view.bonus()), false);
        }
        return mine.size();
    }

    private static int info(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<JobView> view = JobService.views(player).stream().filter(job -> job.id().equals(id)).findFirst();
        if (view.isEmpty()) {
            source.sendFailure(Component.translatable("job.reikoeconomy.unknown"));
            return 0;
        }
        JobView job = view.get();
        source.sendSuccess(() -> job.name().copy().withStyle(ChatFormatting.GOLD), false);
        if (!job.description().getString().isEmpty()) {
            source.sendSuccess(() -> job.description().copy().withStyle(ChatFormatting.GRAY), false);
        }
        source.sendSuccess(() -> Component.translatable("gui.reikoeconomy.jobs.pays").withStyle(ChatFormatting.GRAY), false);
        for (Component example : job.examples()) {
            source.sendSuccess(() -> example, false);
        }
        return 1;
    }
}
