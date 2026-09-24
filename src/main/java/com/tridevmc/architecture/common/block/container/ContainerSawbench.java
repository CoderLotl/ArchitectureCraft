package com.tridevmc.architecture.common.block.container;

import com.google.common.collect.ImmutableList;
import com.tridevmc.architecture.common.ArchitectureMod;
import com.tridevmc.architecture.common.item.ItemShape;
import com.tridevmc.architecture.common.shape.EnumShape;
import com.tridevmc.architecture.common.shape.ShapeMeshes;
import com.tridevmc.compound.ui.container.CompoundContainerMenu;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * The sawbench's menu: a material slot, a result slot, and a shape selector.
 * <p>
 * Selecting a shape works the same way vanilla's stonecutter/loom/enchanting table do - the client asks the
 * server to run {@link #clickMenuButton(Player, int)} with the index of the shape it wants, no custom
 * networking required.
 */
public class ContainerSawbench extends CompoundContainerMenu {

    public static final int MATERIAL_SLOT = 0;
    public static final int RESULT_SLOT = 1;
    private static final int INV_SLOT_START = 2;
    private static final int INV_SLOT_END = 29;
    private static final int USE_ROW_SLOT_START = 29;
    private static final int USE_ROW_SLOT_END = 38;

    /**
     * Only shapes that actually have a mesh to render are offered here - shapes that are still missing their
     * geometry would otherwise craft into an item that renders as nothing.
     */
    private static final ImmutableList<EnumShape> CRAFTABLE_SHAPES = ImmutableList.copyOf(
            Arrays.stream(EnumShape.values()).filter(s -> ShapeMeshes.getMesh(s) != null).toList()
    );

    public final Inventory playerInventory;
    private final DataSlot selectedShapeIndex = DataSlot.standalone();
    private final Slot materialSlot;
    private final Slot resultSlot;
    private final Container container = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            ContainerSawbench.this.setupResultSlot();
        }
    };
    private final ResultContainer resultContainer = new ResultContainer();

    public ContainerSawbench(Inventory playerInv, int id) {
        super(ArchitectureMod.CONTENT.universalMenuType, id);
        this.playerInventory = playerInv;

        // CompoundContainerMenu#addSlot() stashes Integer.MIN_VALUE + y into the vanilla Slot.y field to
        // disable vanilla's own hit-testing (compound reimplements it screen-side, keyed by matching x/y back
        // to an ElementSlot). That means every slot here needs a distinct placeholder x/y - leaving them all
        // at (0, 0) makes every slot indistinguishable to that lookup, so only one slot in the whole menu
        // ever registers a click and the rest go dead.
        int nextSlotOrdinal = 0;

        this.materialSlot = this.addSlot(new Slot(this.container, 0, nextSlotOrdinal++, 0) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return isAcceptableMaterial(stack);
            }
        });
        this.resultSlot = this.addSlot(new Slot(this.resultContainer, 0, nextSlotOrdinal++, 0) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(@NotNull Player player, @NotNull ItemStack stack) {
                stack.onCraftedBy(player.level(), player, stack.getCount());
                ContainerSawbench.this.container.removeItem(MATERIAL_SLOT, ContainerSawbench.this.getMaterialUsed());
                ContainerSawbench.this.setupResultSlot();
                super.onTake(player, stack);
            }
        });

        // Player inventory
        for (int i1 = 0; i1 < 3; ++i1) {
            for (int k1 = 0; k1 < 9; ++k1) {
                this.addSlot(new Slot(playerInv, k1 + i1 * 9 + 9, nextSlotOrdinal++, 0));
            }
        }
        // Hotbar
        for (int j1 = 0; j1 < 9; ++j1) {
            this.addSlot(new Slot(playerInv, j1, nextSlotOrdinal++, 0));
        }

        this.selectedShapeIndex.set(-1);
        this.addDataSlot(this.selectedShapeIndex);
    }

    public static ImmutableList<EnumShape> getCraftableShapes() {
        return CRAFTABLE_SHAPES;
    }

    public static boolean isAcceptableMaterial(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem;
    }

    @Nullable
    public EnumShape getSelectedShape() {
        int index = this.selectedShapeIndex.get();
        return index >= 0 && index < CRAFTABLE_SHAPES.size() ? CRAFTABLE_SHAPES.get(index) : null;
    }

    public int getSelectedShapeIndex() {
        return this.selectedShapeIndex.get();
    }

    /**
     * How many material items a single craft consumes. Kept flat for every shape for now - 1.12 tuned this per
     * shape (small trim pieces produced more per material item), which isn't replicated here yet.
     */
    public int getMaterialUsed() {
        return 1;
    }

    /**
     * How many shape items a single craft produces. See {@link #getMaterialUsed()}.
     */
    public int getShapesProduced() {
        return 1;
    }

    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        if (id >= 0 && id < CRAFTABLE_SHAPES.size()) {
            this.selectedShapeIndex.set(id);
            this.setupResultSlot();
            return true;
        }
        return false;
    }

    private void setupResultSlot() {
        var shape = this.getSelectedShape();
        var materialStack = this.materialSlot.getItem();
        if (shape != null && isAcceptableMaterial(materialStack) && materialStack.getCount() >= this.getMaterialUsed()) {
            // A crafted shape fed back in as material doesn't have a meaningful block state of its own (they're
            // all BlockShape) - its real material is stored in its own ComponentMaterial, so unwrap that instead.
            BlockState materialState = materialStack.getItem() instanceof ItemShape
                    ? ItemShape.getStateFromStack(materialStack)
                    : ((BlockItem) materialStack.getItem()).getBlock().defaultBlockState();
            this.resultSlot.set(ItemShape.createStack(shape, materialState, this.getShapesProduced()));
        } else {
            this.resultSlot.set(ItemStack.EMPTY);
        }
        this.broadcastChanges();
    }

    @Override
    public void slotsChanged(@NotNull Container inventory) {
        super.slotsChanged(inventory);
        this.setupResultSlot();
    }

    @Override
    @NotNull
    public ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            Item item = slotStack.getItem();
            result = slotStack.copy();
            if (index == RESULT_SLOT) {
                item.onCraftedBy(slotStack, player.level(), player);
                if (!this.moveItemStackTo(slotStack, INV_SLOT_START, USE_ROW_SLOT_END, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(slotStack, result);
            } else if (index == MATERIAL_SLOT) {
                if (!this.moveItemStackTo(slotStack, INV_SLOT_START, USE_ROW_SLOT_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (isAcceptableMaterial(slotStack)) {
                if (!this.moveItemStackTo(slotStack, MATERIAL_SLOT, MATERIAL_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= INV_SLOT_START && index < INV_SLOT_END) {
                if (!this.moveItemStackTo(slotStack, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= USE_ROW_SLOT_START && index < USE_ROW_SLOT_END
                    && !this.moveItemStackTo(slotStack, INV_SLOT_START, USE_ROW_SLOT_END, false)) {
                return ItemStack.EMPTY;
            }

            if (slotStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (slotStack.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, slotStack);
        }
        return result;
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        this.resultContainer.removeItemNoUpdate(0);
        if (!player.level().isClientSide()) {
            player.drop(this.container.removeItemNoUpdate(0), false);
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }

}
