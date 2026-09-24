package com.tridevmc.architecture.common.shape.placement;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.tridevmc.architecture.common.block.BlockArchitecture;
import com.tridevmc.architecture.common.shape.orientation.EnumSpin;
import com.tridevmc.architecture.common.shape.orientation.ShapeOrientation;
import com.tridevmc.architecture.common.shape.orientation.ShapeOrientationProperty;
import com.tridevmc.architecture.common.shape.orientation.ShapeOrientationPropertyAxis;
import com.tridevmc.architecture.common.shape.orientation.ShapeOrientationPropertySpin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * Implementation of {@link IShapePlacementLogic} that sets the axis of the shape based on the side of the block
 * clicked, like {@link ShapePlacementLogicOnAxis}, but also exposes a {@link ShapeOrientationPropertySpin} (fixed
 * at {@link EnumSpin#NONE} on initial placement) so the hammer can rotate the shape around its own axis afterwards -
 * for shapes whose texture/geometry isn't rotationally symmetric around that axis, e.g. pillar.
 */
public class ShapePlacementLogicOnAxisWithSpin implements IShapePlacementLogic<BlockArchitecture> {
    public static final ShapePlacementLogicOnAxisWithSpin INSTANCE = new ShapePlacementLogicOnAxisWithSpin();
    private final ImmutableCollection<ShapeOrientationProperty<?>> properties = ImmutableList.of(
            ShapeOrientationPropertyAxis.INSTANCE,
            ShapeOrientationPropertySpin.INSTANCE
    );

    @Override
    @NotNull
    public ShapeOrientation getShapeOrientationForPlacement(
            @NotNull BlockArchitecture beingPlaced,
            @NotNull Level level,
            @NotNull BlockPos placementPos,
            @NotNull Player placer,
            @NotNull BlockHitResult hitResult) {
        // The axis is just the same as the axis of the side clicked.
        var axis = hitResult.getDirection().getAxis();
        return ShapeOrientation.forProperties(
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
