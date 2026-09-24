package com.tridevmc.architecture.client.ui.element;

import com.tridevmc.compound.ui.ICompoundUI;
import com.tridevmc.compound.ui.IInternalCompoundUI;
import com.tridevmc.compound.ui.Rect2F;
import com.tridevmc.compound.ui.element.Element;
import com.tridevmc.compound.ui.layout.ILayout;
import com.tridevmc.compound.ui.layout.LayoutNone;
import com.tridevmc.compound.ui.screen.IScreenContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Drop-in replacement for compound's {@code ElementBox} that draws the vanilla inventory.png 9-slice panel
 * through {@link GuiGraphics#blit}, the same call vanilla container screens use, instead of compound's own
 * {@code IScreenContext.drawTexturedRect}. Some rendering mods in the wild break that raw Tesselator call
 * (observed as a NoClassDefFoundError for net.minecraft.client.renderer.CoreShaders), so this reuses the
 * already-proven-working vanilla code path instead.
 */
public class ElementSafeBox extends Element {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");

    public ElementSafeBox(Rect2F dimensions) {
        this(dimensions, new LayoutNone());
    }

    public ElementSafeBox(Rect2F dimensions, ILayout layout) {
        super(dimensions, layout);
    }

    @Override
    public void drawBackground(ICompoundUI ui) {
        IScreenContext screen = ui.getScreenContext();
        GuiGraphics gg = ((IInternalCompoundUI) ui).getActiveGuiGraphics();
        Rect2F rect = this.getDrawnDimensions(screen);
        int x = Math.round(rect.getX());
        int y = Math.round(rect.getY());
        int width = Math.round(rect.getWidth());
        int height = Math.round(rect.getHeight());

        // Corners
        gg.blit(TEXTURE, x, y, 0, 0, 4, 4, 256, 256);
        gg.blit(TEXTURE, x + width - 4, y, 172, 0, 4, 4, 256, 256);
        gg.blit(TEXTURE, x, y + height - 4, 0, 162, 4, 4, 256, 256);
        gg.blit(TEXTURE, x + width - 4, y + height - 4, 172, 162, 4, 4, 256, 256);

        // Edges, stretched from a 1px strip
        gg.blit(TEXTURE, x, y + 4, 4, height - 8, 0, 4, 4, 1, 256, 256);
        gg.blit(TEXTURE, x + width - 4, y + 4, 4, height - 8, 172, 4, 4, 1, 256, 256);
        gg.blit(TEXTURE, x + 4, y, width - 8, 4, 4, 0, 1, 4, 256, 256);
        gg.blit(TEXTURE, x + 4, y + height - 4, width - 8, 4, 4, 162, 1, 4, 256, 256);

        // Middle, stretched from a 1x1px sample
        gg.blit(TEXTURE, x + 4, y + 4, width - 8, height - 8, 4, 4, 1, 1, 256, 256);
    }
}
