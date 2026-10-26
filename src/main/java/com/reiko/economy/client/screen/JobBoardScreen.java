package com.reiko.economy.client.screen;

import com.reiko.economy.jobs.ContractView;
import com.reiko.economy.jobs.JobView;
import com.reiko.economy.menu.JobBoardMenu;
import com.reiko.economy.network.JobActionPayload;
import com.reiko.economy.network.JobContractPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The job board, with two tabs. Jobs: one row per job with your level, XP toward the next level and pay bonus,
 * and a Join or Quit button; hover a row to see what the job pays. Contracts: today's contracts from your
 * jobs, with progress, the reward and a Deliver button for deliveries.
 */
public final class JobBoardScreen extends ShopScreenBase<JobBoardMenu> {
    private static final int ROW_X = 6;
    private static final int ROW_Y = 18;
    private static final int ROW_HEIGHT = 26;
    private static final int ROW_WIDTH = 208;
    private static final int VISIBLE_ROWS = 6;
    private static final int BAR_WIDTH = 70;
    private static final int FEEDBACK_Y = ROW_Y + VISIBLE_ROWS * ROW_HEIGHT + 4;
    private static final int JOBS = 0;
    private static final int CONTRACTS = 1;

    private final List<Button> buttons = new ArrayList<>();
    private Button[] tabs;
    private int page = JOBS;
    private int scroll;

    public JobBoardScreen(JobBoardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, JobBoardMenu.WIDTH, JobBoardMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = row;
            buttons.add(addRenderableWidget(Button.builder(Component.empty(), button -> press(index))
                    .bounds(leftPos + ROW_X + ROW_WIDTH - 52, topPos + ROW_Y + row * ROW_HEIGHT + 3, 48, 20).build()));
        }
        tabs = addTabs(62, 4, new String[] {"gui.reikoeconomy.jobs.tab.jobs", "gui.reikoeconomy.jobs.tab.contracts"},
                new int[] {28, 56}, tab -> {
                    page = tab;
                    scroll = 0;
                    updateButtons();
                });
        updateButtons();
    }

    private int rowCount() {
        return page == JOBS ? menu.views().size() : menu.contracts().size();
    }

    @Nullable
    private JobView jobAt(int row) {
        int index = scroll + row;
        return page == JOBS && row >= 0 && row < VISIBLE_ROWS && index < menu.views().size() ? menu.views().get(index) : null;
    }

    @Nullable
    private ContractView contractAt(int row) {
        int index = scroll + row;
        return page == CONTRACTS && row >= 0 && row < VISIBLE_ROWS && index < menu.contracts().size() ? menu.contracts().get(index) : null;
    }

    private void press(int row) {
        JobView job = jobAt(row);
        if (job != null) {
            PacketDistributor.sendToServer(new JobActionPayload(menu.containerId, job.id(), !job.active()));
        }
        if (contractAt(row) != null) {
            PacketDistributor.sendToServer(new JobContractPayload(menu.containerId, scroll + row));
        }
    }

    private void updateButtons() {
        boolean full = menu.activeCount() >= menu.maxJobs();
        for (int row = 0; row < buttons.size(); row++) {
            Button button = buttons.get(row);
            JobView job = jobAt(row);
            ContractView contract = contractAt(row);
            if (job != null) {
                button.visible = true;
                button.setMessage(Component.translatable(job.active() ? "gui.reikoeconomy.jobs.quit" : "gui.reikoeconomy.jobs.join"));
                button.active = job.active() || !full;
            } else if (contract != null && contract.deliver() && !contract.done()) {
                button.visible = true;
                button.setMessage(Component.translatable("gui.reikoeconomy.contracts.deliver"));
                button.active = contract.held() > 0;
            } else {
                button.visible = false;
            }
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        scroll = Mth.clamp(scroll, 0, maxScroll());
        updateButtons();
    }

    private int maxScroll() {
        return Math.max(0, rowCount() - VISIBLE_ROWS);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0 && scrollY != 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
            updateButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            JobView job = jobAt(row);
            ContractView contract = contractAt(row);
            if (job == null && contract == null) {
                continue;
            }
            boolean green = job != null ? job.active() : contract.done();
            int x = leftPos + ROW_X;
            int y = topPos + ROW_Y + row * ROW_HEIGHT;
            graphics.fill(x, y, x + ROW_WIDTH, y + ROW_HEIGHT - 2, green ? 0xFF4E8A3E : 0xFF8B8B8B);
            graphics.fill(x + 1, y + 1, x + ROW_WIDTH - 1, y + ROW_HEIGHT - 3, green ? 0xFFB4D9A8 : 0xFFB8B8B8);
            drawSlot(graphics, x + 4, y + 4);

            // XP toward the next level, or progress on the contract
            int barX = x + 26;
            int barY = y + 16;
            graphics.fill(barX, barY, barX + BAR_WIDTH, barY + 4, 0xFF3A3A3A);
            if (job != null) {
                int filled = job.xpToNext() <= 0 ? BAR_WIDTH : (int) (BAR_WIDTH * Math.min(1.0, (double) job.xp() / job.xpToNext()));
                graphics.fill(barX, barY, barX + filled, barY + 4, job.xpToNext() <= 0 ? 0xFFE0B030 : 0xFF6CC349);
            } else {
                int filled = (int) (BAR_WIDTH * Math.min(1.0, (double) contract.progress() / Math.max(1, contract.count())));
                graphics.fill(barX, barY, barX + filled, barY + 4, contract.done() ? 0xFFE0B030 : 0xFF4FA3E0);
            }
        }
        if (maxScroll() > 0) {
            int trackX = leftPos + ROW_X + ROW_WIDTH + 3;
            int trackTop = topPos + ROW_Y;
            int trackHeight = VISIBLE_ROWS * ROW_HEIGHT - 2;
            graphics.fill(trackX, trackTop, trackX + 6, trackTop + trackHeight, 0xFF555555);
            int thumbHeight = Math.max(12, trackHeight * VISIBLE_ROWS / rowCount());
            int thumbY = trackTop + (trackHeight - thumbHeight) * scroll / maxScroll();
            graphics.fill(trackX, thumbY, trackX + 6, thumbY + thumbHeight, 0xFFC6C6C6);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, LABEL_COLOR, false);
        drawSelectedTab(graphics, tabs[page]);
        Component corner = page == JOBS
                ? Component.translatable("gui.reikoeconomy.jobs.count", menu.activeCount(), menu.maxJobs())
                : Component.translatable("gui.reikoeconomy.contracts.new_in", timeLeft(menu.secondsToNew()));
        graphics.drawString(font, corner, imageWidth - 8 - font.width(corner), 6, LABEL_COLOR, false);

        if (page == JOBS && menu.views().isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.reikoeconomy.jobs.none"), 8, ROW_Y + 4, MUTED_COLOR, false);
        }
        if (page == CONTRACTS && menu.contracts().isEmpty()) {
            Component empty = Component.translatable(menu.hasJobs() ? "gui.reikoeconomy.contracts.none" : "gui.reikoeconomy.contracts.no_jobs");
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(empty, ROW_WIDTH);
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawString(font, lines.get(i), 8, ROW_Y + 4 + i * 10, MUTED_COLOR, false);
            }
        }
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int x = ROW_X;
            int y = ROW_Y + row * ROW_HEIGHT;
            JobView job = jobAt(row);
            ContractView contract = contractAt(row);
            if (job != null) {
                graphics.renderItem(job.icon(), x + 4, y + 4);
                Component name = Component.translatable("gui.reikoeconomy.jobs.name_level", job.name(), job.level());
                graphics.drawString(font, fit(name, ROW_WIDTH - 82), x + 26, y + 5, LABEL_COLOR, false);
                graphics.drawString(font, Component.translatable("gui.reikoeconomy.jobs.bonus", job.bonus()), x + 26 + BAR_WIDTH + 5,
                        y + 14, MUTED_COLOR, false);
            } else if (contract != null) {
                graphics.renderItem(contract.icon(), x + 4, y + 4);
                boolean button = contract.deliver() && !contract.done();
                graphics.drawString(font, fit(contract.text(), ROW_WIDTH - (button ? 82 : 32)), x + 26, y + 5, LABEL_COLOR, false);
                Component status = contract.done()
                        ? Component.translatable("gui.reikoeconomy.contracts.done").withStyle(ChatFormatting.DARK_GREEN)
                        : Component.translatable("gui.reikoeconomy.contracts.progress", contract.progress(), contract.count());
                graphics.drawString(font, fit(status, button ? ROW_WIDTH - 26 - BAR_WIDTH - 5 - 56 : 100), x + 26 + BAR_WIDTH + 5, y + 14,
                        MUTED_COLOR, false);
                if (!button) {
                    Component reward = Component.literal(format(contract.reward()));
                    graphics.drawString(font, reward, x + ROW_WIDTH - 6 - font.width(reward), y + 14,
                            contract.done() ? 0x1B7A2B : LABEL_COLOR, false);
                }
            }
        }
        drawFeedback(graphics, 8, FEEDBACK_Y, imageWidth - 16, 2);
    }

    /** "5h 12m", or "12m" in the last hour. */
    private static Component timeLeft(long seconds) {
        long hours = seconds / 3600;
        long minutes = Math.max(1, (seconds % 3600 + 59) / 60);
        if (minutes == 60) {
            hours++;
            minutes = 0;
        }
        return hours > 0
                ? Component.translatable("gui.reikoeconomy.contracts.hours_minutes", hours, minutes)
                : Component.translatable("gui.reikoeconomy.contracts.minutes", minutes);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int row = rowAt(mouseX, mouseY);
        if (row < 0 || buttons.get(row).isMouseOver(mouseX, mouseY)) {
            return;
        }
        JobView job = jobAt(row);
        ContractView contract = contractAt(row);
        List<Component> lines = new ArrayList<>();
        if (job != null) {
            lines.add(job.name().copy().withStyle(ChatFormatting.GOLD));
            if (!job.description().getString().isEmpty()) {
                lines.add(job.description().copy().withStyle(ChatFormatting.GRAY));
            }
            lines.add(job.xpToNext() > 0
                    ? Component.translatable("gui.reikoeconomy.jobs.progress", job.level(), job.xp(), job.xpToNext(), job.bonus())
                    : Component.translatable("gui.reikoeconomy.jobs.progress_max", job.level(), job.bonus()));
            if (!job.examples().isEmpty()) {
                lines.add(Component.translatable("gui.reikoeconomy.jobs.pays").withStyle(ChatFormatting.GRAY));
                for (Component example : job.examples()) {
                    lines.add(example.copy().withStyle(ChatFormatting.DARK_GREEN));
                }
            }
        } else if (contract != null) {
            lines.add(contract.text().copy().withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.reikoeconomy.contracts.from_job", contract.job()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.reikoeconomy.contracts.reward", format(contract.reward()), contract.xp(), contract.job())
                    .withStyle(ChatFormatting.DARK_GREEN));
            if (contract.done()) {
                lines.add(Component.translatable("gui.reikoeconomy.contracts.done_hint").withStyle(ChatFormatting.GRAY));
            } else if (contract.deliver()) {
                lines.add(Component.translatable("gui.reikoeconomy.contracts.deliver_hint", contract.held()).withStyle(ChatFormatting.GRAY));
            } else {
                lines.add(Component.translatable("gui.reikoeconomy.contracts.work_hint").withStyle(ChatFormatting.GRAY));
            }
        }
        if (!lines.isEmpty()) {
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private int rowAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - ROW_X;
        double y = mouseY - topPos - ROW_Y;
        if (x < 0 || x >= ROW_WIDTH || y < 0 || y >= VISIBLE_ROWS * ROW_HEIGHT) {
            return -1;
        }
        return (int) (y / ROW_HEIGHT);
    }
}
