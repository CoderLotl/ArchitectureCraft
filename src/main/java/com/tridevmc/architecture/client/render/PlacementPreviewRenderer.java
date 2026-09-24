package com.tridevmc.architecture.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tridevmc.architecture.client.render.model.baked.IArchitectureBakedModel;
import com.tridevmc.architecture.common.block.BlockShape;
import com.tridevmc.architecture.common.block.state.BlockStateArchitecture;
import com.tridevmc.architecture.common.item.ItemShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/**
 * Renders a translucent preview of the shape a player is about to place, in the orientation it would actually be
 * placed in, whenever they're aiming at a valid placement target with an {@link ItemShape} in hand. Ported from
 * the 1.12 branch's PreviewRenderer, adapted to NeoForge's RenderHighlightEvent instead of raw GL matrix pushes.
 */
public class PlacementPreviewRenderer {

    @SubscribeEvent
    public static void onRenderBlockHighlight(RenderHighlightEvent.Block event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || event.getTarget().getType() != HitResult.Type.BLOCK) {
            return;
        }

        ItemStack stack;
        InteractionHand hand;
        if (player.getMainHandItem().getItem() instanceof ItemShape) {
            stack = player.getMainHandItem();
            hand = InteractionHand.MAIN_HAND;
        } else if (player.getOffhandItem().getItem() instanceof ItemShape) {
            stack = player.getOffhandItem();
            hand = InteractionHand.OFF_HAND;
        } else {
            return;
        }

        var itemShape = (ItemShape) stack.getItem();
        if (!(itemShape.getBlock() instanceof BlockShape<?> block)) {
            return;
        }

        var context = new BlockPlaceContext(player, hand, stack, event.getTarget());
        if (!context.canPlace()) {
            return;
        }

        var placedState = block.getStateForPlacement(context);
        if (!(placedState instanceof BlockStateArchitecture archState)) {
            return;
        }

        var itemModel = mc.getItemRenderer().getItemModelShaper().getItemModel(itemShape);
        if (!(itemModel instanceof IArchitectureBakedModel archModel)) {
            return;
        }

        var quads = archModel.getQuadsForPreview(archState, stack);
        if (quads.isEmpty()) {
            return;
        }

        var pos = context.getClickedPos();
        var camera = event.getCamera().getPosition();

        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1F, 1F, 1F, 0.4F);
        var buffer = event.getMultiBufferSource().getBuffer(RenderType.translucent());
        mc.getItemRenderer().renderQuadList(poseStack, buffer, quads, stack, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();

        poseStack.popPose();
    }

}
