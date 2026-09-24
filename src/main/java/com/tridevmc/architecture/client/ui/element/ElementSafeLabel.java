package com.tridevmc.architecture.client.ui.element;

import com.tridevmc.compound.ui.ICompoundUI;
import com.tridevmc.compound.ui.IInternalCompoundUI;
import com.tridevmc.compound.ui.Rect2F;
import com.tridevmc.compound.ui.element.Element;
import com.tridevmc.compound.ui.layout.ILayout;
import com.tridevmc.compound.ui.layout.LayoutNone;
import com.tridevmc.compound.ui.screen.IScreenContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Drop-in element that draws a line of text through {@link GuiGraphics#drawString}, for this mod's own GUI
 * labels (title, category tabs, captions) - kept separate from compound's own {@code ElementLabel} to stay
 * consistent with {@link ElementSafeBox}/{@link ElementSafeSlot}'s GuiGraphics-only rendering.
 * <p>
 * The element's dimensions double as its clickable hitbox (via {@link #getDrawnDimensions}) even though the
 * text itself may not fill them - used for the sawbench's category tabs, which are clickable across their
 * whole row rather than just under the visible glyphs.
 */
public class ElementSafeLabel extends Element {
    private Component text;
    private final int color;
    private final boolean shadow;

    public ElementSafeLabel(Rect2F dimensions, ILayout layout, Component text, int color, boolean shadow) {
        super(dimensions, layout);
        this.text = text;
        this.color = color;
        this.shadow = shadow;
    }

    public ElementSafeLabel(Rect2F dimensions, ILayout layout, Component text, int color) {
        this(dimensions, layout, text, color, false);
    }

    public ElementSafeLabel(Rect2F dimensions, Component text, int color) {
        this(dimensions, new LayoutNone(), text, color, false);
    }

    public void setText(Component text) {
        this.text = text;
    }

    @Override
    public void drawForeground(ICompoundUI ui) {
        IScreenContext screen = ui.getScreenContext();
        GuiGraphics gg = ((IInternalCompoundUI) ui).getActiveGuiGraphics();
        Rect2F rect = this.getDrawnDimensions(screen);
        int x = Math.round(rect.getX());
        int y = Math.round(rect.getY());

        gg.drawString(Minecraft.getInstance().font, this.text, x, y, this.color, this.shadow);
    }
}
