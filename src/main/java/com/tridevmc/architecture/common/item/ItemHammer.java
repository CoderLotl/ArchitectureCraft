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

import com.tridevmc.architecture.common.block.entity.BlockEntityShape;
import com.tridevmc.architecture.common.shape.orientation.ShapeOrientationPropertyAxis;
import com.tridevmc.architecture.common.shape.orientation.ShapeOrientationPropertyFacing;
import com.tridevmc.architecture.common.shape.orientation.ShapeOrientationPropertyFlip;
import com.tridevmc.architecture.common.shape.orientation.ShapeOrientationPropertySpin;
import com.tridevmc.architecture.common.shape.orientation.ShapeOrientationPropertyTurn;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Re-orients a placed shape without breaking it. Sneak-clicking cycles which way the shape faces
 * (its facing/axis property, if it has one); a plain click cycles its in-place rotation (turn/spin),
 * falling back to toggling a flip if the shape doesn't expose either.
 */
public class ItemHammer extends Item {

    public ItemHammer(ResourceLocation id) {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var world = context.getLevel();
        var pos = context.getClickedPos();
        var player = context.getPlayer();
        var te = world.getBlockEntity(pos);
        if (!(te instanceof BlockEntityShape)) {
            return InteractionResult.FAIL;
        }
        if (!world.isClientSide()) {
            var state = world.getBlockState(pos);
            var newState = player != null && player.isShiftKeyDown()
                    ? cycleFirstMatchingProperty(state, ShapeOrientationPropertyFacing.class, ShapeOrientationPropertyAxis.class)
                    : cycleFirstMatchingProperty(state, ShapeOrientationPropertyTurn.class, ShapeOrientationPropertySpin.class, ShapeOrientationPropertyFlip.class);
            if (newState != null) {
                world.setBlock(pos, newState, Block.UPDATE_CLIENTS);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Finds the first property on the state whose type matches one of the given types (in order of preference)
     * and cycles it to its next value.
     *
     * @param state         the state to cycle a property on.
     * @param propertyTypes the property types to look for, in order of preference.
     * @return the new state with the property cycled, or null if the state has none of the given property types.
     */
    @Nullable
    private static BlockState cycleFirstMatchingProperty(BlockState state, Class<?>... propertyTypes) {
        for (var type : propertyTypes) {
            for (var property : state.getProperties()) {
                if (type.isInstance(property)) {
                    return state.cycle(property);
                }
            }
        }
        return null;
    }

}
