package com.tridevmc.architecture.common.block.entity;

import com.tridevmc.architecture.common.ArchitectureMod;
import com.tridevmc.architecture.common.item.ItemCladding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class BlockEntityShape extends BlockEntityArchitecture {

    private BlockState baseMaterialState, secondaryMaterialState;

    public BlockEntityShape(BlockPos pos, BlockState state) {
        super(ArchitectureMod.CONTENT.blockEntityTypeShape, pos, state);
    }

    /**
     * Gets the BlockEntityShape at the given position, if it exists. Returns an empty optional if it does not.
     *
     * @param world The world to get the BlockEntityShape from.
     * @param pos   The position to get the BlockEntityShape from.
     * @return An optional containing the BlockEntityShape, or an empty optional if it does not exist.
     */
    public static Optional<BlockEntityShape> getAtOptionally(LevelAccessor world, BlockPos pos) {
        return Optional.ofNullable(world.getBlockEntity(pos)).map(te -> te instanceof BlockEntityShape ? (BlockEntityShape) te : null);
    }

    /**
     * Gets the BlockEntityShape at the given position, if it exists. Returns null if it does not.
     *
     * @param world The world to get the BlockEntityShape from.
     * @param pos   The position to get the BlockEntityShape from.
     * @return The BlockEntityShape, or null if it does not exist.
     */
    @Nullable
    public static BlockEntityShape getAt(LevelAccessor world, BlockPos pos) {
        var te = world.getBlockEntity(pos);
        if (te instanceof BlockEntityShape) {
            return (BlockEntityShape) te;
        }
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        tag.putInt("BaseMaterialState", Block.getId(this.getBaseMaterialState().orElse(Blocks.OAK_PLANKS.defaultBlockState())));
        tag.putInt("SecondaryMaterialState", this.getSecondaryMaterialState().map(Block::getId).orElse(-1));
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        this.baseMaterialState = Block.stateById(tag.getInt("BaseMaterialState"));
        this.secondaryMaterialState = Optional.of(tag.getInt("SecondaryMaterialState")).filter(id -> id != -1).map(Block::stateById).orElse(null);
    }

    /**
     * Gets the base material state of this shape wrapped in an optional for additional safety.
     *
     * @return The base material state of this shape.
     */
    public Optional<BlockState> getBaseMaterialState() {
        return Optional.ofNullable(this.baseMaterialState);
    }

    /**
     * Sets the base material state of this shape.
     *
     * @param baseMaterialState The base material state to set.
     * @return This BlockEntityShape instance.
     */
    public BlockEntityShape setBaseMaterialState(BlockState baseMaterialState) {
        this.baseMaterialState = baseMaterialState;
        return this;
    }

    /**
     * Gets the secondary material state of this shape wrapped in an optional for additional safety, if the secondary material state is not set then the base material should be used.
     *
     * @return The secondary material state of this shape.
     */
    public Optional<BlockState> getSecondaryMaterialState() {
        return Optional.ofNullable(this.secondaryMaterialState);
    }

    /**
     * Sets the secondary material state of this shape.
     *
     * @param secondaryMaterialState The secondary material state to set.
     * @return This BlockEntityShape instance.
     */
    public BlockEntityShape setSecondaryMaterialState(BlockState secondaryMaterialState) {
        this.secondaryMaterialState = secondaryMaterialState;
        return this;
    }

    /**
     * Gets the effective base material state of this shape, if the base material state is not set then oak planks should be used to prevent crashes.
     *
     * @return The effective base material state of this shape.
     */
    public BlockState getEffectiveBaseMaterialState() {
        return this.baseMaterialState == null ? Blocks.OAK_PLANKS.defaultBlockState() : this.baseMaterialState;
    }

    /**
     * Gets the effective secondary material state of this shape, if the secondary material state is not set then the effective base material state should be used.
     *
     * @return The effective secondary material state of this shape.
     */
    public BlockState getEffectiveSecondaryMaterialState() {
        return this.secondaryMaterialState == null ? this.getEffectiveBaseMaterialState() : this.secondaryMaterialState;
    }

    /**
     * Gets the material state for the given index, if the index is 0 then the base material state should be used, otherwise the secondary material state should be used.
     *
     * @param i The index to get the material state for.
     * @return The material state for the given index.
     */
    public BlockState getMaterialStateForIndex(int i) {
        return i == 0 ? this.getEffectiveBaseMaterialState() : this.getEffectiveSecondaryMaterialState();
    }

    /**
     * Clears the secondary material state of this shape, reverting it to using the base material.
     *
     * @return This BlockEntityShape instance.
     */
    public BlockEntityShape clearSecondaryMaterialState() {
        this.secondaryMaterialState = null;
        return this;
    }

    /**
     * Determines the material a stack would apply as a secondary material, without mutating anything.
     * Only accepts {@link ItemCladding} stacks - it used to accept any placeable block item too, but that meant
     * right-clicking an unclad shape with an ordinary block (to place it alongside, not to clad the shape) would
     * silently consume the click as cladding instead of placing anything.
     *
     * @param stack The stack to inspect.
     * @return The material state the stack would apply, or null if the stack cannot be used as a secondary material.
     */
    @Nullable
    public static BlockState getSecondaryMaterialStateFromStack(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemCladding cladding)) {
            return null;
        }
        var state = cladding.blockStateFromStack(stack);
        return state.isAir() ? null : state;
    }
}
