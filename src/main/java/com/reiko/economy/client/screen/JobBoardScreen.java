package com.reiko.economy.client.screen;

import com.reiko.economy.jobs.JobView;
import com.reiko.economy.menu.JobBoardMenu;
import com.reiko.economy.network.JobActionPayload;
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
 * The job board: one row per job with your level, XP toward the next level and pay bonus, and a Join or Quit
 * button. Hover a row to see what the job pays. Jobs you have are shown in green.
 */
public final class JobBoardScreen extends ShopScreenBase<JobBoardMenu> {
    private static final int ROW_X = 6;
    private static final int ROW_Y = 18;
    private static final int ROW_HEIGHT = 26;
    private static final int ROW_WIDTH = 208;
    private static final int VISIBLE_ROWS = 6;
    private static final int BAR_WIDTH = 70;
    private static final int FEEDBACK_Y = ROW_Y + VISIBLE_ROWS * ROW_HEIGHT + 4;

    private final List<Button> buttons = new ArrayList<>();
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
        updateButtons();
    }

    @Nullable
    private JobView viewAt(int row) {
        int index = scroll + row;
        List<JobView> views = menu.views();
        return row >= 0 && row < VISIBLE_ROWS && index < views.size() ? views.get(index) : null;
    }

    private void press(int row) {
        JobView view = viewAt(row);
        if (view != null) {
            PacketDistributor.sendToServer(new JobActionPayload(menu.containerId, view.id(), !view.active()));
        }
    }

    private void updateButtons() {
        boolean full = menu.activeCount() >= menu.maxJobs();
        for (int row = 0; row < buttons.size(); row++) {
            JobView view = viewAt(row);
            Button button = buttons.get(row);
            button.visible = view != null;
            if (view != null) {
                button.setMessage(Component.translatable(view.active() ? "gui.reikoeconomy.jobs.quit" : "gui.reikoeconomy.jobs.join"));
                button.active = view.active() || !full;
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
        return Math.max(0, menu.views().size() - VISIBLE_ROWS);
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
            JobView view = viewAt(row);
            if (view == null) {
                continue;
            }
            int x = leftPos + ROW_X;
            int y = topPos + ROW_Y + row * ROW_HEIGHT;
            graphics.fill(x, y, x + ROW_WIDTH, y + ROW_HEIGHT - 2, view.active() ? 0xFF4E8A3E : 0xFF8B8B8B);
            graphics.fill(x + 1, y + 1, x + ROW_WIDTH - 1, y + ROW_HEIGHT - 3, view.active() ? 0xFFB4D9A8 : 0xFFB8B8B8);
            drawSlot(graphics, x + 4, y + 4);

            // XP toward the next level
            int barX = x + 26;
            int barY = y + 16;
            graphics.fill(barX, barY, barX + BAR_WIDTH, barY + 4, 0xFF3A3A3A);
            int filled = view.xpToNext() <= 0 ? BAR_WIDTH : (int) (BAR_WIDTH * Math.min(1.0, (double) view.xp() / view.xpToNext()));
            graphics.fill(barX, barY, barX + filled, barY + 4, view.xpToNext() <= 0 ? 0xFFE0B030 : 0xFF6CC349);
        }
        if (maxScroll() > 0) {
            int trackX = leftPos + ROW_X + ROW_WIDTH + 3;
            int trackTop = topPos + ROW_Y;
            int trackHeight = VISIBLE_ROWS * ROW_HEIGHT - 2;
            graphics.fill(trackX, trackTop, trackX + 6, trackTop + trackHeight, 0xFF555555);
            int thumbHeight = Math.max(12, trackHeight * VISIBLE_ROWS / menu.views().size());
            int thumbY = trackTop + (trackHeight - thumbHeight) * scroll / maxScroll();
            graphics.fill(trackX, thumbY, trackX + 6, thumbY + thumbHeight, 0xFFC6C6C6);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, LABEL_COLOR, false);
        Component count = Component.translatable("gui.reikoeconomy.jobs.count", menu.activeCount(), menu.maxJobs());
        graphics.drawString(font, count, imageWidth - 8 - font.width(count), 6, LABEL_COLOR, false);
        if (menu.views().isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.reikoeconomy.jobs.none"), 8, ROW_Y + 4, MUTED_COLOR, false);
        }
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            JobView view = viewAt(row);
            if (view == null) {
                continue;
            }
            int x = ROW_X;
            int y = ROW_Y + row * ROW_HEIGHT;
            graphics.renderItem(view.icon(), x + 4, y + 4);
            Component name = Component.translatable("gui.reikoeconomy.jobs.name_level", view.name(), view.level());
            graphics.drawString(font, fit(name, ROW_WIDTH - 82), x + 26, y + 5, LABEL_COLOR, false);
            graphics.drawString(font, Component.translatable("gui.reikoeconomy.jobs.bonus", view.bonus()), x + 26 + BAR_WIDTH + 5,
                    y + 14, MUTED_COLOR, false);
        }
        drawFeedback(graphics, 8, FEEDBACK_Y, imageWidth - 16, 2);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int row = rowAt(mouseX, mouseY);
        JobView view = viewAt(row);
        if (view != null && !buttons.get(row).isMouseOver(mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(view.name().copy().withStyle(ChatFormatting.GOLD));
            if (!view.description().getString().isEmpty()) {
                lines.add(view.description().copy().withStyle(ChatFormatting.GRAY));
            }
            lines.add(view.xpToNext() > 0
                    ? Component.translatable("gui.reikoeconomy.jobs.progress", view.level(), view.xp(), view.xpToNext(), view.bonus())
                    : Component.translatable("gui.reikoeconomy.jobs.progress_max", view.level(), view.bonus()));
            if (!view.examples().isEmpty()) {
                lines.add(Component.translatable("gui.reikoeconomy.jobs.pays").withStyle(ChatFormatting.GRAY));
                for (Component example : view.examples()) {
                    lines.add(example.copy().withStyle(ChatFormatting.DARK_GREEN));
                }
            }
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
