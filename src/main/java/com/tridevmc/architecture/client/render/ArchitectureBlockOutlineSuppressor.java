package com.tridevmc.architecture.client.render;

import com.tridevmc.architecture.common.block.BlockShape;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/**
 * Suppresses the vanilla block-outline highlight for shape blocks. It's technically correct (a real voxelized
 * approximation of the shape's mesh, not a fallback cube - see the sphere family's ~80-box voxelization), but
 * Minecraft can only ever draw that outline as axis-aligned box edges, so up close a round shape's outline reads
 * as "blocky" no matter how fine the voxelization is - a limitation of vanilla's outline renderer, not of the
 * shape data itself. The player asked for the outline to simply not draw on these blocks rather than trying to
 * make a fundamentally boxy outline look rounder.
 */
public class ArchitectureBlockOutlineSuppressor {

    @SubscribeEvent
    public static void onRenderBlockHighlight(RenderHighlightEvent.Block event) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        var pos = event.getTarget().getBlockPos();
        if (level.getBlockState(pos).getBlock() instanceof BlockShape<?>) {
            event.setCanceled(true);
        }
    }

}
