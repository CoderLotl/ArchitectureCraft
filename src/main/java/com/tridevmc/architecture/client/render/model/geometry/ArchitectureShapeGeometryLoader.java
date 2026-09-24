package com.tridevmc.architecture.client.render.model.geometry;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.tridevmc.architecture.client.render.model.impl.BakedModelRoofGeneric;
import com.tridevmc.architecture.client.render.model.impl.BakedModelShapeGeneric;
import com.tridevmc.architecture.common.shape.EnumShape;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;

import java.util.Map;

public class ArchitectureShapeGeometryLoader implements IGeometryLoader<IArchitectureModelGeometry>, ResourceManagerReloadListener {

    // These shapes have no .objson mesh - 1.12 generated their geometry procedurally instead of exporting a
    // static model, so BakedModelRoofGeneric does the same rather than a mesh lookup that would just fail.
    private static final ImmutableSet<EnumShape> PROCEDURAL_ROOF_SHAPES = ImmutableSet.of(
            EnumShape.ROOF_TILE, EnumShape.ROOF_OUTER_CORNER, EnumShape.ROOF_INNER_CORNER,
            EnumShape.ROOF_RIDGE, EnumShape.ROOF_SMART_RIDGE,
            EnumShape.ROOF_VALLEY, EnumShape.ROOF_SMART_VALLEY
    );

    private final Map<EnumShape, IArchitectureModelGeometry> models = Maps.newConcurrentMap();

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.models.clear();
    }

    @Override
    public IArchitectureModelGeometry read(JsonObject modelContents, JsonDeserializationContext deserializationContext) {
        var shapeName = modelContents.get("shapeName").getAsString();
        var shape = EnumShape.byName(shapeName);
        if (shape == null) {
            throw new IllegalArgumentException("Unknown shape: " + shapeName);
        }

        return this.models.computeIfAbsent(shape, s -> PROCEDURAL_ROOF_SHAPES.contains(s)
                ? (context, modelBaker, function, modelState, itemOverrides) -> new BakedModelRoofGeneric(shape, context.getTransforms())
                : (context, modelBaker, function, modelState, itemOverrides) -> new BakedModelShapeGeneric(shape, context.getTransforms()));
    }

}
