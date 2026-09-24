package com.tridevmc.architecture.common.shape;

import com.google.common.collect.ImmutableList;
import com.tridevmc.architecture.core.ArchitectureLog;
import com.tridevmc.architecture.core.model.mesh.IMesh;
import com.tridevmc.architecture.core.model.mesh.PolygonData;
import com.tridevmc.architecture.core.model.objson.OBJSON;
import com.tridevmc.architecture.core.model.voxelize.IVoxelizer;
import com.tridevmc.architecture.core.model.voxelize.StaticVoxelizer;
import com.tridevmc.architecture.core.physics.AABB;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Responsible for getting a mesh for a given shape enum, and a voxelizer for a given shape enum.
 */
public class ShapeMeshes {

    // TODO: There are likely special cases here we need to error for.

    // Shapes with no .objson mesh (BakedModelRoofGeneric generates their geometry procedurally instead - see
    // that class) also have no analytical voxelizer, so without this they'd fall back to a full 1x1x1 cube for
    // collision/occlusion. That's not just imprecise collision, it also makes neighbouring blocks think this
    // shape fully covers the shared face and skip rendering their own - leaving a see-through hole wherever
    // the sloped render geometry doesn't actually reach. A rough two-box slope approximation (same idea as
    // vanilla stairs' own simplified collision shape) fixes that; it doesn't need to be pixel-perfect.
    private static final ImmutableList<AABB> ROOF_SLOPE_APPROXIMATION = ImmutableList.of(
            new AABB(0, 0, 0.5, 1, 1, 1),
            new AABB(0, 0, 0, 1, 0.5, 0.5)
    );
    private static final ImmutableList<EnumShape> PROCEDURAL_ROOF_SHAPES = ImmutableList.of(
            EnumShape.ROOF_TILE, EnumShape.ROOF_OUTER_CORNER, EnumShape.ROOF_INNER_CORNER,
            EnumShape.ROOF_RIDGE, EnumShape.ROOF_SMART_RIDGE,
            EnumShape.ROOF_VALLEY, EnumShape.ROOF_SMART_VALLEY
    );

    private static final Map<EnumShape, IMesh<String, PolygonData>> MESHES = new HashMap<>();
    private static final Map<EnumShape, IVoxelizer> VOXELIZERS = new HashMap<>();

    static {
        Arrays.stream(EnumShape.values()).forEach(
                enumShape -> {
                    try {
                        var objson = OBJSON.fromResource(enumShape.getAssetLocation());
                        var mesh = objson.mesh();
                        var voxelizer = objson.voxelizer();
                        register(enumShape, mesh, voxelizer);
                    } catch (Exception e) {
                        if (PROCEDURAL_ROOF_SHAPES.contains(enumShape)) {
                            VOXELIZERS.put(enumShape, new StaticVoxelizer(ROOF_SLOPE_APPROXIMATION));
                        } else {
                            ArchitectureLog.error("Failed to load mesh for shape: " + enumShape.getAssetLocation(), e);
                        }
                    }
                }
        );
    }

    private static void register(EnumShape enumShape, IMesh<String, PolygonData> mesh, IVoxelizer voxelizer) {
        MESHES.put(enumShape, mesh);
        VOXELIZERS.put(enumShape, voxelizer);
    }

    public static IMesh<String, PolygonData> getMesh(EnumShape enumShape) {
        return MESHES.get(enumShape);
    }

    public static IVoxelizer getVoxelizer(EnumShape enumShape) {
        return VOXELIZERS.get(enumShape);
    }

}
