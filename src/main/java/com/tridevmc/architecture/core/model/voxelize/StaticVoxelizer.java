package com.tridevmc.architecture.core.model.voxelize;

import com.google.common.collect.ImmutableList;
import com.tridevmc.architecture.core.math.integer.IVector3i;
import com.tridevmc.architecture.core.model.mesh.IMesh;
import com.tridevmc.architecture.core.model.mesh.IPolygonData;
import com.tridevmc.architecture.core.physics.AABB;

import java.util.concurrent.CompletableFuture;

/**
 * An {@link IVoxelizer} that always returns the same fixed set of boxes, for shapes that don't have a mesh to
 * analytically voxelize (see {@link com.tridevmc.architecture.client.render.model.impl.BakedModelRoofGeneric}).
 * <p>
 * Without a voxelizer at all, these shapes fall back to a full 1x1x1 cube for their collision/occlusion shape
 * (see {@code BlockArchitecture#DEFAULT_BOX}) - which isn't just an imprecise hitbox, it also makes neighbouring
 * blocks think this shape fully covers the face between them and skip rendering their own face there, leaving a
 * see-through hole wherever the (sloped, not full-cube) render geometry doesn't actually cover it. A rough
 * approximation here is far better than that, even if it isn't pixel-perfect collision.
 * <p>
 * The mesh-dependent default methods on {@link IVoxelizer} (used only by the dev-only voxelizer debug tool) are
 * not supported, since there's no real mesh backing this - not needed for normal placement/collision.
 */
public class StaticVoxelizer implements IVoxelizer {

    private final ImmutableList<AABB> boxes;

    public StaticVoxelizer(ImmutableList<AABB> boxes) {
        this.boxes = boxes;
    }

    @Override
    public ImmutableList<AABB> voxelizeNow() {
        return this.boxes;
    }

    @Override
    public CompletableFuture<ImmutableList<AABB>> voxelize() {
        return CompletableFuture.completedFuture(this.boxes);
    }

    @Override
    public IMesh<?, ? extends IPolygonData<?>> getMesh() {
        throw new UnsupportedOperationException("StaticVoxelizer has no backing mesh");
    }

    @Override
    public int getBlockResolution() {
        return 16;
    }

    @Override
    public double getResolution() {
        return 1D / 16;
    }

    @Override
    public IVector3i getMin() {
        return IVector3i.ofImmutable(0, 0, 0);
    }

    @Override
    public IVector3i getMax() {
        return IVector3i.ofImmutable(16, 16, 16);
    }
}
