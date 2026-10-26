package com.reiko.economy.client.screen;

import com.reiko.economy.core.Denomination;
import com.reiko.economy.item.WalletContents;
import com.reiko.economy.menu.WalletMenu;
import com.reiko.economy.network.WalletActionPayload;
import com.reiko.economy.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * An open wallet: one tile per coin and bill showing how many are inside (click to take one, Shift-click
 * for a stack), a slot to drop cash in, and the inventory below. Shift-clicking cash in the inventory also
 * puts it in.
 */
public final class WalletScreen extends ShopScreenBase<WalletMenu> {
    private static final int TILE = 22;
    private static final int COIN_ROW = 18;
    private static final int BILL_ROW = 40;
    private static final int TAKE_ROW = 62;

    public WalletScreen(WalletMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WalletMenu.WIDTH, WalletMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.wallet.take_all"),
                        button -> send(WalletMenu.ALL, 0))
                .bounds(leftPos + 8, topPos + TAKE_ROW, 64, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.reikoeconomy.wallet.take_all_hint")))
                .build());
    }

    private void send(int denomination, int count) {
        PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, denomination, count));
    }

    /** Where a denomination's tile sits: coins on the first row, bills on the second. */
    private static int tileX(Denomination denomination) {
        int index = denomination.isBill() ? denomination.ordinal() - Denomination.ONE.ordinal() : denomination.ordinal();
        return 8 + index * TILE;
    }

    private static int tileY(Denomination denomination) {
        return denomination.isBill() ? BILL_ROW : COIN_ROW;
    }

    @Nullable
    private Denomination tileAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos;
        double y = mouseY - topPos;
        for (Denomination denomination : Denomination.values()) {
            int tileX = tileX(denomination);
            int tileY = tileY(denomination);
            if (x >= tileX && x < tileX + TILE - 2 && y >= tileY && y < tileY + TILE - 2) {
                return denomination;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Denomination tile = tileAt(mouseX, mouseY);
        if (tile != null && button == 0 && menu.getCarried().isEmpty()) {
            if (menu.contents().count(tile) > 0) {
                send(tile.ordinal(), hasShiftDown() ? 64 : 1);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (Denomination denomination : Denomination.values()) {
            drawSlot(graphics, leftPos + tileX(denomination) + 2, topPos + tileY(denomination) + 2);
        }
        drawHighlightSlot(graphics, leftPos + WalletMenu.ADD_X, topPos + WalletMenu.ADD_Y);
        drawPlayerInventory(graphics, leftPos + 8, topPos + WalletMenu.INVENTORY_Y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        WalletContents contents = menu.contents();
        graphics.drawString(font, title, 8, 6, LABEL_COLOR, false);
        Component total = Component.translatable("gui.reikoeconomy.wallet.total", format(contents.value()));
        graphics.drawString(font, total, imageWidth - 8 - font.width(total), 6, LABEL_COLOR, false);
        Component add = Component.translatable("gui.reikoeconomy.wallet.add");
        graphics.drawString(font, add, WalletMenu.ADD_X - 5 - font.width(add), WalletMenu.ADD_Y + 4, MUTED_COLOR, false);

        for (Denomination denomination : Denomination.values()) {
            int count = contents.count(denomination);
            int x = tileX(denomination) + 2;
            int y = tileY(denomination) + 2;
            ItemStack stack = new ItemStack(ModItems.cash(denomination));
            graphics.renderItem(stack, x, y);
            if (count > 0) {
                graphics.renderItemDecorations(font, stack, x, y, shortCount(count));
            } else {
                // Fade out coins and bills the wallet doesn't hold.
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200);
                graphics.fill(x, y, x + 16, y + 16, 0xA08B8B8B);
                graphics.pose().popPose();
            }
        }
        drawFeedback(graphics, 78, TAKE_ROW + 4, imageWidth - 86, 1);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Denomination tile = tileAt(mouseX, mouseY);
        if (tile != null && menu.getCarried().isEmpty()) {
            int count = menu.contents().count(tile);
            List<Component> lines = count > 0
                    ? List.of(Component.translatable("item.reikoeconomy." + tile.id()),
                            Component.translatable("gui.reikoeconomy.wallet.in_wallet", count).withStyle(ChatFormatting.GRAY),
                            Component.translatable("gui.reikoeconomy.wallet.take_hint").withStyle(ChatFormatting.DARK_GRAY))
                    : List.of(Component.translatable("item.reikoeconomy." + tile.id()),
                            Component.translatable("gui.reikoeconomy.wallet.in_wallet", 0).withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    /** "7", "640", "1k", "12k": short enough to fit on an item. */
    private static String shortCount(int count) {
        return count < 1000 ? String.valueOf(count) : count / 1000 + "k";
    }
}
