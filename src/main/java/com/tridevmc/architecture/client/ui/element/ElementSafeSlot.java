package com.tridevmc.architecture.client.ui.element;

import com.tridevmc.compound.ui.ICompoundUI;
import com.tridevmc.compound.ui.IInternalCompoundUI;
import com.tridevmc.compound.ui.Rect2F;
import com.tridevmc.compound.ui.element.container.ElementSlot;
import com.tridevmc.compound.ui.layout.ILayout;
import com.tridevmc.compound.ui.layout.LayoutNone;
import com.tridevmc.compound.ui.screen.IScreenContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;

/**
 * Drop-in replacement for compound's {@code ElementSlot} that draws the slot's border sprite through
 * {@link GuiGraphics#blitSprite}, instead of compound's own {@code IScreenContext.drawSprite}. See
 * {@link ElementSafeBox} for why: that call chain crashes with several rendering mods present.
 */
public class ElementSafeSlot extends ElementSlot {
    private static final ResourceLocation SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/slot");

    public ElementSafeSlot(Rect2F dimensions, Slot slot) {
        this(dimensions, new LayoutNone(), slot);
    }

    public ElementSafeSlot(Rect2F dimensions, ILayout layout, Slot slot) {
        super(dimensions, layout, slot);
    }

    @Override
    public void drawBackground(ICompoundUI ui) {
        IScreenContext screen = ui.getScreenContext();
        GuiGraphics gg = ((IInternalCompoundUI) ui).getActiveGuiGraphics();
        Rect2F rect = this.getDrawnDimensions(screen);
        gg.blitSprite(SLOT_SPRITE, Math.round(rect.getX()), Math.round(rect.getY()), Math.round(rect.getWidth()), Math.round(rect.getHeight()));
    }
}
