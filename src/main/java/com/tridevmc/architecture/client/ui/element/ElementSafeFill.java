package com.tridevmc.architecture.client.ui.element;

import com.tridevmc.compound.ui.ICompoundUI;
import com.tridevmc.compound.ui.IInternalCompoundUI;
import com.tridevmc.compound.ui.Rect2F;
import com.tridevmc.compound.ui.element.Element;
import com.tridevmc.compound.ui.layout.ILayout;
import com.tridevmc.compound.ui.screen.IScreenContext;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Drop-in element that draws a solid-colour rectangle through {@link GuiGraphics#fill}, for this mod's own GUI
 * highlights (the sawbench's selected-page/selected-shape indicators) - kept consistent with
 * {@link ElementSafeBox}/{@link ElementSafeSlot}'s GuiGraphics-only rendering. A zero-size rect (the default,
 * see {@link #hide()}) draws nothing, used to toggle the highlight off without removing the element.
 */
public class ElementSafeFill extends Element {
    private final int color;

    public ElementSafeFill(Rect2F dimensions, ILayout layout, int color) {
        super(dimensions, layout);
        this.color = color;
    }

    public void hide() {
        this.setDimensions(new Rect2F(0, 0, 0, 0));
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
        if (width <= 0 || height <= 0) {
            return;
        }

        gg.fill(x, y, x + width, y + height, this.color);
    }
}
