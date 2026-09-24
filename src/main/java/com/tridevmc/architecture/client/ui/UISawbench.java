package com.tridevmc.architecture.client.ui;

import com.google.common.collect.ImmutableList;
import com.tridevmc.architecture.client.ui.element.ElementSafeFill;
import com.tridevmc.architecture.client.ui.element.ElementSafeImage;
import com.tridevmc.architecture.client.ui.element.ElementSafeLabel;
import com.tridevmc.architecture.client.ui.element.ElementSafeSlot;
import com.tridevmc.architecture.common.ArchitectureMod;
import com.tridevmc.architecture.common.block.container.ContainerSawbench;
import com.tridevmc.architecture.common.item.ItemShape;
import com.tridevmc.architecture.common.shape.EnumShape;
import com.tridevmc.compound.core.reflect.WrappedField;
import com.tridevmc.compound.ui.Rect2F;
import com.tridevmc.compound.ui.container.CompoundUIContainer;
import com.tridevmc.compound.ui.element.container.ElementSlot;
import com.tridevmc.compound.ui.layout.*;
import com.tridevmc.compound.ui.listeners.IMousePressListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The sawbench's screen, laid out to match the original 1.12 GUI (same background art, category tabs down the
 * shape grid, selected-shape caption) instead of the plain auto-generated grid this had during the initial port.
 */
public class UISawbench extends CompoundUIContainer<ContainerSawbench> {

    // Several rendering mods in the wild (Veil in particular) crash on compound's raw Tesselator-based
    // sprite drawing, so slots/panels are drawn through ElementSafeSlot/ElementSafeBox/ElementSafeImage/etc
    // instead, which use GuiGraphics directly. addSlotElement() is overridden below to use ElementSafeSlot, but
    // it still needs to register into CompoundUIContainer's private slotElements map for hover/tooltip/quick-craft
    // tracking.
    private static final WrappedField<Map<Slot, ElementSlot>> SLOT_ELEMENTS_FIELD =
            WrappedField.create(CompoundUIContainer.class, "slotElements");

    private static final ResourceLocation BACKGROUND_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ArchitectureMod.MOD_ID, "textures/gui/gui_sawbench.png");

    private static final int PANEL_WIDTH = 242;
    private static final int PANEL_HEIGHT = 224;
    private static final int TITLE_COLOR = 0x404040;

    private static final int MATERIAL_SLOT_X = 12, MATERIAL_SLOT_Y = 19;
    private static final int RESULT_SLOT_X = 12, RESULT_SLOT_Y = 57;

    private static final int GRID_X = 44, GRID_Y = 23;
    private static final int GRID_COLS = 5, GRID_ROWS = 4;
    private static final int GRID_CELLS = GRID_COLS * GRID_ROWS;
    private static final int CELL_SIZE = 24, CELL_ICON_SIZE = 18;
    private static final int CELL_ICON_INSET = (CELL_SIZE - CELL_ICON_SIZE) / 2;

    private static final int TAB_X = 176, TAB_Y = 19, TAB_WIDTH = 58, TAB_ROW_HEIGHT = 10;
    private static final int TAB_HIGHLIGHT_COLOR = 0xFF66CCFF;
    private static final int GRID_HIGHLIGHT_COLOR = 0x8066CCFF;

    private static final int CAPTION_NAME_X = 40, CAPTION_NAME_Y = 128;
    private static final int CAPTION_RATIO_X = 7, CAPTION_RATIO_Y = 82;

    private static final ImmutableList<ShapeCategory> CATEGORIES = buildCategories();

    private final Player player;

    // AbstractContainerScreen#render() already calls renderBackground() itself, but
    // CompoundUIContainer#render() *also* calls it explicitly before delegating to super - so the world-darkening
    // overlay gets composited twice per frame, which is what turns "mostly see-through" into "fully black".
    // renderBg() (drawing our own elements) is harmless to run twice, so only skip the dimming on every other call.
    private boolean skipNextBackgroundDim = false;

    private int selectedPage = 0;
    // Deliberately NOT initialised inline (`= new X[GRID_CELLS]`): CompoundUIContainer's constructor calls the
    // overridden initElements() from inside super(container), which runs before this subclass's own field
    // initialisers do - an inline initialiser here would still be null when initShapeGrid() first writes to it,
    // NPEing on the very first array store. Allocated in initShapeGrid() instead, like cellElements already was.
    private EnumShape[] cellShapes;
    private SimpleContainer[] cellContainers;
    private ElementSafeSlot[] cellElements;
    private ElementSafeFill gridHighlight;
    private ElementSafeFill tabHighlight;
    private ElementSafeLabel[] tabLabels;
    private ElementSafeLabel shapeNameLabel;
    private ElementSafeLabel ratioLabel;
    private int lastDisplayedSelection = Integer.MIN_VALUE;

    public UISawbench(ContainerSawbench container, Player player) {
        super(container);
        this.player = player;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.skipNextBackgroundDim) {
            this.renderTransparentBackground(guiGraphics);
        }
        this.skipNextBackgroundDim = !this.skipNextBackgroundDim;
        this.refreshSelectionDisplay();
        this.renderBg(guiGraphics, partialTick, mouseX, mouseY);
    }

    @Override
    public ElementSlot addSlotElement(Rect2F dimensions, ILayout layout, int slotIndex) {
        Slot slot = this.getMenu().getSlot(slotIndex);
        ElementSlot element = new ElementSafeSlot(dimensions, layout, slot);
        this.addElement(element);
        SLOT_ELEMENTS_FIELD.get(this).put(slot, element);
        return element;
    }

    @Override
    public void initElements() {
        LayoutCentered boxLayout = new LayoutCentered(true, true);
        ElementSafeImage bg = new ElementSafeImage(new Rect2F(0, 0, PANEL_WIDTH, PANEL_HEIGHT), boxLayout,
                BACKGROUND_TEXTURE, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, 256, 256);
        this.addElement(bg);
        ILayout relativeToBg = new LayoutRelative(bg);

        this.addElement(new ElementSafeLabel(new Rect2F(7, 7, PANEL_WIDTH - 14, 10), relativeToBg,
                Component.translatable("architecturecraft.gui.sawbench.title"), TITLE_COLOR));

        // Material slot on the left, result slot below it - same positions as the original 1.12 layout.
        this.addSlotElement(new Rect2F(MATERIAL_SLOT_X, MATERIAL_SLOT_Y, 18, 18), relativeToBg, ContainerSawbench.MATERIAL_SLOT);
        this.addSlotElement(new Rect2F(RESULT_SLOT_X, RESULT_SLOT_Y, 18, 18), relativeToBg, ContainerSawbench.RESULT_SLOT);

        this.initShapeGrid(relativeToBg);
        this.initCategoryTabs(relativeToBg);
        this.initSelectionCaptions(relativeToBg);

        int playerInvY = PANEL_HEIGHT - 81;
        int hotbarY = playerInvY + 18 * 3 + 4;
        LayoutGrid playerGrid = new LayoutGrid(new Rect2F(8, playerInvY, 18 * 9, 18 * 3));
        ILayout playerLayout = new LayoutMulti(playerGrid, relativeToBg);

        LayoutGrid hotbarGrid = new LayoutGrid(new Rect2F(8, hotbarY, 18 * 9, 18));
        ILayout hotbarLayout = new LayoutMulti(hotbarGrid, relativeToBg);

        for (int i = 0; i < this.getMenu().slots.size(); i++) {
            if (i == ContainerSawbench.MATERIAL_SLOT || i == ContainerSawbench.RESULT_SLOT) {
                continue;
            }
            int playerSlotIndex = i - 2;
            if (playerSlotIndex < 27) {
                playerGrid.registerElement(this.addSlotElement(playerLayout, i));
            } else {
                hotbarGrid.registerElement(this.addSlotElement(hotbarLayout, i));
            }
        }

        this.switchToPage(this.selectedPage);
    }

    /**
     * Builds the (reused across page switches) shape preview grid: fixed slot elements whose backing item and
     * target shape get swapped out by {@link #switchToPage(int)} rather than being rebuilt, since compound has
     * no cheap "clear and rebuild" path for an already-initialised screen.
     */
    private void initShapeGrid(ILayout relativeToBg) {
        this.cellShapes = new EnumShape[GRID_CELLS];
        this.cellContainers = new SimpleContainer[GRID_CELLS];
        this.cellElements = new ElementSafeSlot[GRID_CELLS];
        for (int i = 0; i < GRID_CELLS; i++) {
            int row = i / GRID_COLS;
            int col = i % GRID_COLS;
            var container = new SimpleContainer(1);
            this.cellContainers[i] = container;
            var previewSlot = new Slot(container, 0, 0, 0) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            };
            var rect = new Rect2F(GRID_X + col * CELL_SIZE + CELL_ICON_INSET, GRID_Y + row * CELL_SIZE + CELL_ICON_INSET,
                    CELL_ICON_SIZE, CELL_ICON_SIZE);
            var element = new ElementSafeSlot(rect, relativeToBg, previewSlot);
            // Not registered in CompoundUIContainer's slotElements map (not a real menu slot), so nothing ever
            // calls setDisplayStack() on it before the first frame - the field defaults to null and
            // drawForeground() NPEs trying to render it. Seed it eagerly.
            element.setDisplayStack(ItemStack.EMPTY);
            this.cellElements[i] = element;
            this.addElement(element);

            int cellIndex = i;
            this.addListener((IMousePressListener) (screen, x, y, button) -> {
                if (button == 0 && this.cellShapes[cellIndex] != null && element.isMouseOverSlot(screen)) {
                    int shapeIndex = ContainerSawbench.getCraftableShapes().indexOf(this.cellShapes[cellIndex]);
                    if (shapeIndex >= 0 && this.getMenu().clickMenuButton(this.player, shapeIndex)) {
                        this.minecraft.gameMode.handleInventoryButtonClick(this.getMenu().containerId, shapeIndex);
                    }
                }
            });
        }

        this.gridHighlight = new ElementSafeFill(new Rect2F(0, 0, 0, 0), relativeToBg, GRID_HIGHLIGHT_COLOR);
        this.addElement(this.gridHighlight);
    }

    private void initCategoryTabs(ILayout relativeToBg) {
        this.tabHighlight = new ElementSafeFill(new Rect2F(TAB_X, TAB_Y, TAB_WIDTH, TAB_ROW_HEIGHT), relativeToBg, TAB_HIGHLIGHT_COLOR);
        this.addElement(this.tabHighlight);

        this.tabLabels = new ElementSafeLabel[CATEGORIES.size()];
        for (int p = 0; p < CATEGORIES.size(); p++) {
            var rect = new Rect2F(TAB_X + 2, TAB_Y + p * TAB_ROW_HEIGHT + 1, TAB_WIDTH - 2, TAB_ROW_HEIGHT);
            var label = new ElementSafeLabel(rect, relativeToBg,
                    Component.translatable("shape_page.architecturecraft." + CATEGORIES.get(p).langKey()), TITLE_COLOR);
            this.tabLabels[p] = label;
            this.addElement(label);
        }

        this.addListener((IMousePressListener) (screen, x, y, button) -> {
            if (button != 0) {
                return;
            }
            for (int p = 0; p < CATEGORIES.size(); p++) {
                // getDrawnDimensions() is local/untranslated for managed-matrix elements (the default - the
                // framework pre-translates the PoseStack before drawBackground/drawForeground runs, so draw
                // calls can use it directly) - it's only correct here, against raw absolute mouse coordinates,
                // via getScreenspaceDimensions(), which always resolves the full layout chain to a screen-space
                // rect regardless of useManagedMatrix(). Same method ElementSlot.isMouseOverSlot() uses.
                if (this.tabLabels[p].getScreenspaceDimensions(screen).isPointInRect(x, y)) {
                    this.switchToPage(p);
                    break;
                }
            }
        });
    }

    private void initSelectionCaptions(ILayout relativeToBg) {
        this.shapeNameLabel = new ElementSafeLabel(new Rect2F(CAPTION_NAME_X, CAPTION_NAME_Y, 128, 10), relativeToBg,
                Component.empty(), TITLE_COLOR);
        this.addElement(this.shapeNameLabel);

        this.ratioLabel = new ElementSafeLabel(new Rect2F(CAPTION_RATIO_X, CAPTION_RATIO_Y, 128, 10), relativeToBg,
                Component.empty(), TITLE_COLOR);
        this.addElement(this.ratioLabel);
    }

    /**
     * Swaps which shapes the (fixed, reused) preview grid displays, and moves the tab highlight - the actual
     * crafting selection is untouched, it's tracked globally by {@link ContainerSawbench} regardless of which
     * page is currently being viewed.
     */
    private void switchToPage(int page) {
        this.selectedPage = page;
        var shapes = CATEGORIES.get(page).shapes();
        for (int i = 0; i < GRID_CELLS; i++) {
            EnumShape shape = i < shapes.size() ? shapes.get(i) : null;
            this.cellShapes[i] = shape;
            ItemStack stack = shape != null ? ItemShape.createStack(shape, Blocks.OAK_PLANKS.defaultBlockState()) : ItemStack.EMPTY;
            this.cellContainers[i].setItem(0, stack);
            this.cellElements[i].setDisplayStack(stack);
        }
        this.tabHighlight.setDimensions(new Rect2F(TAB_X, TAB_Y + page * TAB_ROW_HEIGHT, TAB_WIDTH, TAB_ROW_HEIGHT));
        this.lastDisplayedSelection = Integer.MIN_VALUE; // Force the grid highlight/captions to re-evaluate below.
        this.refreshSelectionDisplay();
    }

    /**
     * Keeps the grid highlight and shape-name/ratio captions in sync with the menu's currently selected shape,
     * which changes server-side (and syncs back via a vanilla DataSlot) whenever a grid icon is clicked - so
     * this can't just be computed once at click time, it needs to reflect the confirmed server state.
     */
    private void refreshSelectionDisplay() {
        int selectedIndex = this.getMenu().getSelectedShapeIndex();
        if (selectedIndex == this.lastDisplayedSelection) {
            return;
        }
        this.lastDisplayedSelection = selectedIndex;

        var selected = this.getMenu().getSelectedShape();
        if (selected == null) {
            this.shapeNameLabel.setText(Component.empty());
            this.ratioLabel.setText(Component.empty());
            this.gridHighlight.hide();
            return;
        }

        this.shapeNameLabel.setText(Component.translatable(selected.getLocalizationKey()));
        this.ratioLabel.setText(Component.translatable("architecturecraft.gui.sawbench.ratio",
                this.getMenu().getMaterialUsed(), this.getMenu().getShapesProduced()));

        int highlightCell = -1;
        for (int i = 0; i < GRID_CELLS; i++) {
            if (this.cellShapes[i] == selected) {
                highlightCell = i;
                break;
            }
        }
        if (highlightCell < 0) {
            this.gridHighlight.hide();
        } else {
            int row = highlightCell / GRID_COLS;
            int col = highlightCell % GRID_COLS;
            this.gridHighlight.setDimensions(new Rect2F(GRID_X + col * CELL_SIZE, GRID_Y + row * CELL_SIZE, CELL_SIZE, CELL_SIZE));
        }
    }

    private record ShapeCategory(String langKey, ImmutableList<EnumShape> shapes) {
    }

    /**
     * Groups shapes for the sawbench's category tabs, mirroring the 1.12 GUI's page layout (same names, same
     * groupings). Only craftable shapes (those with a mesh) are included, and any craftable shape not covered
     * by an explicit grouping falls back into "other" automatically, so nothing new ever goes missing from the
     * menu even if it isn't categorised here.
     */
    private static ImmutableList<ShapeCategory> buildCategories() {
        var craftable = ContainerSawbench.getCraftableShapes();
        var categorised = new LinkedHashSet<EnumShape>();

        var builder = ImmutableList.<ShapeCategory>builder();
        builder.add(category("roofing", categorised, craftable,
                EnumShape.ROOF_TILE, EnumShape.ROOF_OUTER_CORNER, EnumShape.ROOF_INNER_CORNER,
                EnumShape.ROOF_RIDGE, EnumShape.ROOF_SMART_RIDGE, EnumShape.ROOF_VALLEY, EnumShape.ROOF_SMART_VALLEY,
                EnumShape.ROOF_OVERHANG, EnumShape.ROOF_OVERHANG_OUTER_CORNER, EnumShape.ROOF_OVERHANG_INNER_CORNER,
                EnumShape.ROOF_OVERHANG_GABLE_LH, EnumShape.ROOF_OVERHANG_GABLE_RH,
                EnumShape.ROOF_OVERHANG_GABLE_END_LH, EnumShape.ROOF_OVERHANG_GABLE_END_RH,
                EnumShape.ROOF_OVERHANG_RIDGE, EnumShape.ROOF_OVERHANG_VALLEY,
                EnumShape.BEVELLED_OUTER_CORNER, EnumShape.BEVELLED_INNER_CORNER));
        builder.add(category("rounded", categorised, craftable,
                EnumShape.CYLINDER, EnumShape.CYLINDER_HALF, EnumShape.CYLINDER_QUARTER,
                EnumShape.CYLINDER_LARGE_QUARTER, EnumShape.ANTICYLINDER_LARGE_QUARTER,
                EnumShape.PILLAR, EnumShape.POST, EnumShape.POLE,
                EnumShape.SPHERE_FULL, EnumShape.SPHERE_HALF, EnumShape.SPHERE_QUARTER,
                EnumShape.SPHERE_EIGHTH, EnumShape.SPHERE_EIGHTH_LARGE, EnumShape.SPHERE_EIGHTH_LARGE_REV));
        builder.add(category("classical", categorised, craftable,
                EnumShape.PILLAR_BASE, EnumShape.PILLAR, EnumShape.DORIC_CAPITAL, EnumShape.DORIC_TRIGLYPH,
                EnumShape.DORIC_TRIGLYPH_CORNER, EnumShape.DORIC_METOPE, EnumShape.IONIC_CAPITAL,
                EnumShape.CORINTHIAN_CAPITAL, EnumShape.ARCHITRAVE, EnumShape.ARCHITRAVE_CORNER,
                EnumShape.CORNICE_LH, EnumShape.CORNICE_RH, EnumShape.CORNICE_END_LH, EnumShape.CORNICE_END_RH,
                EnumShape.CORNICE_RIDGE, EnumShape.CORNICE_VALLEY, EnumShape.CORNICE_BOTTOM));
        builder.add(category("window", categorised, craftable,
                EnumShape.WINDOW_FRAME, EnumShape.WINDOW_CORNER, EnumShape.WINDOW_MULLION,
                EnumShape.WINDOW_SMART, EnumShape.WINDOW_MULLION_SMART));
        builder.add(category("arches", categorised, craftable,
                EnumShape.ARCH_D1, EnumShape.ARCH_D2, EnumShape.ARCH_D3A, EnumShape.ARCH_D3B, EnumShape.ARCH_D3C,
                EnumShape.ARCH_D4A, EnumShape.ARCH_D4B, EnumShape.ARCH_D4C));
        builder.add(category("railings", categorised, craftable,
                EnumShape.BALUSTRADE_PLAIN, EnumShape.BALUSTRADE_PLAIN_OUTER_CORNER, EnumShape.BALUSTRADE_PLAIN_INNER_CORNER,
                EnumShape.BALUSTRADE_PLAIN_WITH_NEWEL, EnumShape.BALUSTRADE_PLAIN_END,
                EnumShape.BANISTER_PLAIN_TOP, EnumShape.BANISTER_PLAIN, EnumShape.BANISTER_PLAIN_BOTTOM,
                EnumShape.BANISTER_PLAIN_END, EnumShape.BANISTER_PLAIN_INNER_CORNER,
                EnumShape.BALUSTRADE_FANCY, EnumShape.BALUSTRADE_FANCY_CORNER, EnumShape.BALUSTRADE_FANCY_WITH_NEWEL,
                EnumShape.BALUSTRADE_FANCY_NEWEL,
                EnumShape.BANISTER_FANCY_TOP, EnumShape.BANISTER_FANCY, EnumShape.BANISTER_FANCY_BOTTOM,
                EnumShape.BANISTER_FANCY_END, EnumShape.BANISTER_FANCY_NEWEL_TALL));

        // Anything craftable but not covered above (including shapes added after this list was written) lands
        // here instead of silently disappearing from the menu.
        var other = ImmutableList.<EnumShape>builder();
        for (var shape : new EnumShape[]{EnumShape.CLADDING_SHEET, EnumShape.SLAB, EnumShape.STAIRS,
                EnumShape.STAIRS_OUTER_CORNER, EnumShape.STAIRS_INNER_CORNER, EnumShape.STAIRS_SMART}) {
            if (craftable.contains(shape) && categorised.add(shape)) {
                other.add(shape);
            }
        }
        for (var shape : craftable) {
            if (categorised.add(shape)) {
                other.add(shape);
            }
        }
        var otherShapes = other.build();
        if (!otherShapes.isEmpty()) {
            builder.add(new ShapeCategory("other", otherShapes));
        }

        return builder.build();
    }

    private static ShapeCategory category(String langKey, Set<EnumShape> categorised,
                                           ImmutableList<EnumShape> craftable, EnumShape... shapes) {
        var builder = ImmutableList.<EnumShape>builder();
        for (var shape : shapes) {
            if (craftable.contains(shape)) {
                builder.add(shape);
                categorised.add(shape);
            }
        }
        return new ShapeCategory(langKey, builder.build());
    }
}
