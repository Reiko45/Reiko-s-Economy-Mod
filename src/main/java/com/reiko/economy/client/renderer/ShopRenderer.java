package com.reiko.economy.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reiko.economy.block.entity.AbstractShopBlockEntity;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

/**
 * Draws the item for sale spinning above a shop, and its price as floating text when a player is close.
 */
public final class ShopRenderer<T extends AbstractShopBlockEntity> implements BlockEntityRenderer<T> {
    /** Labels show within this many blocks, so a street of shops doesn't become a wall of text. */
    private static final double LABEL_DISTANCE = 8.0;
    /** The see-through copy of the text: white at an eighth opacity, as vanilla does for name tags. */
    private static final int FAINT_TEXT = 0x20FFFFFF;
    private static final float LINE_HEIGHT = 0.25F; // 10 font pixels at a 0.025 scale

    private final ItemRenderer itemRenderer;
    private final Font font;

    public ShopRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
        this.font = context.getFont();
    }

    @Override
    public void render(T shop, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = shop.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = shop.getBlockPos();
        int light = LevelRenderer.getLightColor(level, pos.above());

        ItemStack stack = shop.template();
        if (!stack.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5, 1.3, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(((level.getGameTime() % 360) + partialTick) * 2.0F));
            poseStack.scale(0.5F, 0.5F, 0.5F);
            itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, poseStack, buffers, level,
                    (int) pos.asLong());
            poseStack.popPose();
        }

        Player viewer = Minecraft.getInstance().player;
        if (viewer == null || viewer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > LABEL_DISTANCE * LABEL_DISTANCE) {
            return;
        }
        List<Component> lines = shop.labelLines();
        poseStack.pushPose();
        poseStack.translate(0.5, 1.7 + LINE_HEIGHT * (lines.size() - 1), 0.5);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();
        int background = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            float x = -font.width(line) / 2.0F;
            // Like vanilla name tags: the background (with faint, plain text) first, without depth testing, then the
            // real text on top. Drawn in one pass, the background hid part of the text when seen at an angle.
            font.drawInBatch(Component.literal(line.getString()), x, i * 10, FAINT_TEXT, false, matrix, buffers,
                    Font.DisplayMode.SEE_THROUGH, background, light);
            font.drawInBatch(line, x, i * 10, -1, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, light);
        }
        poseStack.popPose();
    }
}
