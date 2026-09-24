package com.tridevmc.architecture.common.shape.transformation;

import com.tridevmc.architecture.common.shape.orientation.EnumSpin;
import com.tridevmc.architecture.common.shape.orientation.ShapeOrientation;
import com.tridevmc.architecture.common.shape.orientation.ShapeOrientationPropertyAxis;
import com.tridevmc.architecture.common.shape.orientation.ShapeOrientationPropertySpin;
import com.tridevmc.architecture.core.math.IMatrix4Immutable;
import com.tridevmc.architecture.core.math.ITrans3;
import org.jetbrains.annotations.NotNull;

/**
 * An implementation of {@link IShapeTransformationResolver} for an orientation with both an axis property (as in
 * {@link ShapeTransformationResolverOnAxis}) and a spin property, for shapes that aren't symmetric around their
 * axis - e.g. window_corner, whose two frame arms make it look different after a 90 degree spin, unlike the
 * fully-symmetric window_frame/window_mullion which only need the axis.
 * <p>
 * Assumes the model is authored resting on the Y axis by default (matching {@link ShapeTransformationResolverOnAxis}'s
 * convention), with spin rotating it around that same local Y axis - i.e. around whichever world axis it ends up
 * mapped to - before the axis transform re-orients the whole thing. Rotating around local Y (rather than a
 * perpendicular axis) is what keeps the spin a rotation around the shape's own axis regardless of which world
 * axis that axis is placed on - mirroring how {@link ShapeTransformationResolverPointedWithSpin} composes its own
 * spin with its facing transform.
 */
public class ShapeTransformationResolverAxisWithSpin implements IShapeTransformationResolver {
    public static final ShapeTransformationResolverAxisWithSpin INSTANCE = new ShapeTransformationResolverAxisWithSpin();

    @Override
    public @NotNull ITrans3 resolve(@NotNull ShapeOrientation orientation) {
        var axis = orientation.getValue(ShapeOrientationPropertyAxis.INSTANCE).value();
        var spinValue = orientation.getValue(ShapeOrientationPropertySpin.INSTANCE);
        var spin = spinValue != null ? spinValue.value() : EnumSpin.NONE;
        var spinMatrix = IMatrix4Immutable.ofRotationXYZ(0.5, 0.5, 0.5, 0, spin.getDegrees(), 0);

        return switch (axis) {
            case X -> ITrans3.ofImmutable(
                    IMatrix4Immutable.ofRotationXYZ(0.5, 0.5, 0.5, 0, 0, -90).asMutable().mul(spinMatrix)
            );
            case Y -> ITrans3.ofImmutable(spinMatrix);
            case Z -> ITrans3.ofImmutable(
                    IMatrix4Immutable.ofRotationXYZ(0.5, 0.5, 0.5, 90, 0, 0).asMutable().mul(spinMatrix)
            );
        };
    }
}
