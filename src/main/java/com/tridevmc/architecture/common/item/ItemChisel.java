/*
 * MIT License
 *
 * Copyright (c) 2017 Benjamin K
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tridevmc.architecture.common.item;

import com.tridevmc.architecture.common.ArchitectureMod;
import com.tridevmc.architecture.common.block.entity.BlockEntityShape;
import com.tridevmc.architecture.common.shape.orientation.EnumConnectionState;
import com.tridevmc.architecture.common.shape.orientation.ShapeOrientationPropertyConnection;
import com.tridevmc.architecture.legacy.common.block.LegacyBlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class ItemChisel extends Item {

    /**
     * Half-width of the "centre" zone of a clicked face, in block-local units (-0.5..0.5).
     * Hits further from the centre than this, along an axis other than the clicked face's own axis,
     * are treated as a click on that neighbouring side rather than the centre.
     */
    private static final double SIDE_ZONE_SIZE = 1 / 4D;

    public ItemChisel(ResourceLocation id) {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var world = context.getLevel();
        var pos = context.getClickedPos();
        var player = context.getPlayer();
        var clickedFace = context.getClickedFace();
        var clickLoc = context.getClickLocation();
        var hitX = clickLoc.x() - pos.getX() - 0.5;
        var hitY = clickLoc.y() - pos.getY() - 0.5;
        var hitZ = clickLoc.z() - pos.getZ() - 0.5;
        var te = world.getBlockEntity(pos);
        if (te instanceof BlockEntityShape shapeEntity) {
            if (!world.isClientSide()) {
                var side = zoneHit(clickedFace, hitX, hitY, hitZ);
                if (side != null) {
                    this.toggleConnection(world, pos, side);
                } else {
                    this.removeSecondaryMaterial(world, pos, player, shapeEntity);
                }
            }
            return InteractionResult.SUCCESS;
        }
        var state = world.getBlockState(pos);
        var block = state.getBlock();
        if ((block == Blocks.GLASS) || (block == Blocks.GLASS_PANE)
                || (block == Blocks.GLOWSTONE) || (block == Blocks.ICE)) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 0x3);
            if (!world.isClientSide()) {
                this.dropBlockAsItem(world, pos, state);
                world.levelEvent(2001, pos, Block.getId(Blocks.STONE.defaultBlockState())); // block breaking sound and particles
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    /**
     * Determines which side (if any) of the block was hit, based on the clicked face and the hit position
     * relative to the centre of the block. Hits inside the central zone return null.
     *
     * @param face the face that was clicked.
     * @param hitX the x hit position, relative to the block centre (-0.5..0.5).
     * @param hitY the y hit position, relative to the block centre (-0.5..0.5).
     * @param hitZ the z hit position, relative to the block centre (-0.5..0.5).
     * @return the side that was hit, or null if the centre was hit.
     */
    @Nullable
    private static Direction zoneHit(Direction face, double hitX, double hitY, double hitZ) {
        double r = 0.5 - SIDE_ZONE_SIZE;
        if (hitX <= -r && face != Direction.WEST) return Direction.WEST;
        if (hitX >= r && face != Direction.EAST) return Direction.EAST;
        if (hitY <= -r && face != Direction.DOWN) return Direction.DOWN;
        if (hitY >= r && face != Direction.UP) return Direction.UP;
        if (hitZ <= -r && face != Direction.NORTH) return Direction.NORTH;
        if (hitZ >= r && face != Direction.SOUTH) return Direction.SOUTH;
        return null;
    }

    /**
     * Toggles the connection state on the given side of the block, if it has one. Shapes that don't expose a
     * connection property on that side (most of them) simply ignore the click.
     */
    private void toggleConnection(Level world, BlockPos pos, Direction side) {
        var state = world.getBlockState(pos);
        var property = ShapeOrientationPropertyConnection.forDirection(side);
        if (!state.getProperties().contains(property)) {
            return;
        }
        var connected = state.getValue(property) == EnumConnectionState.CONNECTED;
        world.setBlock(pos, state.setValue(property, connected ? EnumConnectionState.DISCONNECTED : EnumConnectionState.CONNECTED), Block.UPDATE_CLIENTS);
    }

    /**
     * Removes the secondary material from the given shape, if it has one, and gives the player back a
     * cladding item for the material that was removed.
     */
    private void removeSecondaryMaterial(Level world, BlockPos pos, @Nullable Player player, BlockEntityShape shapeEntity) {
        var material = shapeEntity.getSecondaryMaterialState().orElse(null);
        if (material == null) {
            return;
        }
        shapeEntity.clearSecondaryMaterialState();
        shapeEntity.setChanged();
        var state = world.getBlockState(pos);
        world.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        var returnedStack = ArchitectureMod.CONTENT.itemCladding.newStack(material, 1);
        if (player == null || !player.getInventory().add(returnedStack)) {
            Block.popResource(world, pos, returnedStack);
        }
    }

    private void dropBlockAsItem(Level world, BlockPos pos, BlockState state) {
        ItemStack stack = LegacyBlockHelper.blockStackWithState(state, 1);
        Block.popResource(world, pos, stack);
    }

}
