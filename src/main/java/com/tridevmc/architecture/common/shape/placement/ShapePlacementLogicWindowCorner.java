package com.tridevmc.architecture.common.shape.placement;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.tridevmc.architecture.common.block.BlockArchitecture;
import com.tridevmc.architecture.common.shape.orientation.EnumSpin;
import com.tridevmc.architecture.common.shape.orientation.ShapeOrientation;
import com.tridevmc.architecture.common.shape.orientation.ShapeOrientationPropertyAxis;
import com.tridevmc.architecture.common.shape.orientation.ShapeOrientationPropertySpin;
import com.tridevmc.architecture.common.shape.orientation.ShapeOrientationProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * Placement logic for window_corner: picks an axis the same way {@link ShapePlacementLogicWindowFrame} does
 * (the player's primary look axis), but leaves the in-plane rotation (spin) at a fixed default rather than
 * trying to infer which of the 4 corner quadrants was intended from the click position. The hammer (which
 * cycles any {@link ShapeOrientationPropertySpin} on right-click) is the intended way to dial in the correct
 * rotation after placement - the same tool already used for every other spin-having shape.
 */
public class ShapePlacementLogicWindowCorner<T extends BlockArchitecture> implements IShapePlacementLogic<T> {

    private final ImmutableList<ShapeOrientationProperty<?>> properties = ImmutableList.of(
            ShapeOrientationPropertyAxis.INSTANCE,
            ShapeOrientationPropertySpin.INSTANCE
    );

    @Override
    public @NotNull ShapeOrientation getShapeOrientationForPlacement(
            @NotNull T beingPlaced,
            @NotNull Level level,
            @NotNull BlockPos placementPos,
            @NotNull Player placer,
            @NotNull BlockHitResult hitResult) {
        // Same axis-selection as ShapePlacementLogicWindowFrame: the player's primary look axis, preferring a
        // horizontal axis unless they're crouching (crouching lets you place a Y-axis/skylight-style corner).
        var nearestDirections = Direction.orderedByNearest(placer);
        var axis = nearestDirections[0].getOpposite().getAxis();
        if (!placer.isCrouching() && axis == Direction.Axis.Y) {
            for (var direction : nearestDirections) {
                if (direction.getAxis() != Direction.Axis.Y) {
                    axis = direction.getAxis();
                    break;
                }
            }
        }

        return new ShapeOrientation(
                ShapeOrientationPropertyAxis.of(axis),
                ShapeOrientationPropertySpin.of(EnumSpin.NONE)
        );
    }

    @Override
    @NotNull
    public ImmutableCollection<ShapeOrientationProperty<?>> getProperties() {
        return this.properties;
    }
}
