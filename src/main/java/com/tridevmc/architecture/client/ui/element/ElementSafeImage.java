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
 * Drop-in element that blits an arbitrary texture region through {@link GuiGraphics#blit}, for drawing this
 * mod's own GUI art (background panels, icon atlases) instead of compound's own {@code ElementImage}. See
 * {@link ElementSafeBox} for why GuiGraphics is used instead of compound's own texture-drawing calls.
 */
public class ElementSafeImage extends Element {
    private final ResourceLocation texture;
    private final int u, v, regionWidth, regionHeight, textureWidth, textureHeight;

    public ElementSafeImage(Rect2F dimensions, ILayout layout, ResourceLocation texture,
                             int u, int v, int regionWidth, int regionHeight,
                             int textureWidth, int textureHeight) {
        super(dimensions, layout);
        this.texture = texture;
        this.u = u;
        this.v = v;
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    /**
     * Convenience constructor for drawing a texture region 1:1 (no layout, no stretching) at its native size.
     */
    public ElementSafeImage(Rect2F dimensions, ResourceLocation texture, int textureWidth, int textureHeight) {
        this(dimensions, new LayoutNone(), texture, 0, 0,
                Math.round(dimensions.getWidth()), Math.round(dimensions.getHeight()), textureWidth, textureHeight);
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

        gg.blit(this.texture, x, y, width, height, this.u, this.v, this.regionWidth, this.regionHeight, this.textureWidth, this.textureHeight);
    }
}
