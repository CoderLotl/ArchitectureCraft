package com.tridevmc.architecture.client.render.model.impl;

import com.google.common.collect.Maps;
import com.tridevmc.architecture.client.render.model.baked.BakedQuadContainer;
import com.tridevmc.architecture.client.render.model.baked.IArchitectureBakedModel;
import com.tridevmc.architecture.common.block.BlockShape;
import com.tridevmc.architecture.common.block.entity.BlockEntityShape;
import com.tridevmc.architecture.common.block.state.BlockStateArchitecture;
import com.tridevmc.architecture.common.item.ItemShape;
import com.tridevmc.architecture.common.model.ModelProperties;
import com.tridevmc.architecture.common.shape.EnumShape;
import com.tridevmc.architecture.core.math.IVector3;
import com.tridevmc.architecture.core.math.IVector3Immutable;
import com.tridevmc.architecture.core.math.ITrans3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A hand-generated (not mesh-based) baked model for the roof shape family, ported from the 1.12 branch's
 * RenderRoof.java. These shapes were never given .objson mesh exports - 1.12 built their geometry procedurally,
 * including auto-connecting slopes to neighbouring ridge/valley pieces, so there was nothing to export in the
 * first place. This is a faithful-as-practical translation of that generator onto our baked-quad pipeline.
 * <p>
 * Currently covers ROOF_TILE, ROOF_OUTER_CORNER, ROOF_INNER_CORNER, ROOF_RIDGE and ROOF_VALLEY (the "dumb"
 * members of the family that don't need to re-decide their own shape based on neighbours) plus their smart
 * counterparts ROOF_SMART_RIDGE/ROOF_SMART_VALLEY, which use the same geometry but always try to connect on
 * all four sides instead of only towards known ridge/valley neighbours.
 */
public class BakedModelRoofGeneric implements IArchitectureBakedModel {

    private static final Map<BlockState, TextureAtlasSprite> TEXTURE_CACHE = Maps.newConcurrentMap();

    private static final EnumShape[] RIDGE_SHAPES = {EnumShape.ROOF_RIDGE, EnumShape.ROOF_SMART_RIDGE};
    private static final EnumShape[] RIDGE_OR_SLOPE_SHAPES = {
            EnumShape.ROOF_RIDGE, EnumShape.ROOF_SMART_RIDGE,
            EnumShape.ROOF_TILE, EnumShape.ROOF_OUTER_CORNER, EnumShape.ROOF_INNER_CORNER
    };
    private static final EnumShape[] VALLEY_SHAPES = {EnumShape.ROOF_VALLEY, EnumShape.ROOF_SMART_VALLEY};
    private static final EnumShape[] VALLEY_OR_SLOPE_SHAPES = {
            EnumShape.ROOF_VALLEY, EnumShape.ROOF_SMART_VALLEY,
            EnumShape.ROOF_TILE, EnumShape.ROOF_INNER_CORNER
    };

    private final EnumShape shape;
    private final ItemTransforms transforms;

    public BakedModelRoofGeneric(EnumShape shape, ItemTransforms transforms) {
        this.shape = shape;
        this.transforms = transforms;
    }

    private static TextureAtlasSprite getTextureForState(BlockState state) {
        return TEXTURE_CACHE.computeIfAbsent(state, s -> Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getBlockModel(s).getParticleIcon());
    }

    @Override
    @NotNull
    public ItemTransforms getTransforms() {
        return this.transforms;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    @NotNull
    public TextureAtlasSprite getParticleIcon() {
        return getTextureForState(Blocks.OAK_PLANKS.defaultBlockState());
    }

    @Override
    @NotNull
    public java.util.List<BakedQuad> getQuads(@Nullable BlockStateArchitecture state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData extraData, @Nullable RenderType renderType) {
        var transform = state == null ? ITrans3.ofIdentity() : state.getTransform();
        var level = extraData.get(ModelProperties.LEVEL);
        var pos = extraData.get(ModelProperties.POS);

        BlockState baseMaterial = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState secondaryMaterial = baseMaterial;
        if (level != null && pos != null) {
            var shapeBe = BlockEntityShape.getAt(level, pos);
            if (shapeBe != null) {
                baseMaterial = shapeBe.getMaterialStateForIndex(0);
                secondaryMaterial = shapeBe.getMaterialStateForIndex(1);
            }
        }

        var builder = new RoofGeometryBuilder(this.shape, transform, level, pos, getTextureForState(baseMaterial), getTextureForState(secondaryMaterial));
        builder.render();
        return builder.build().quadsFor(side);
    }

    @Override
    @NotNull
    public java.util.List<BakedQuad> getQuads(@NotNull ItemStack stack) {
        return this.buildStandaloneQuads(ITrans3.ofIdentity(), stack).allQuads();
    }

    @Override
    @NotNull
    public java.util.List<BakedQuad> getQuadsForPreview(@NotNull BlockStateArchitecture state, @NotNull ItemStack stack) {
        return this.buildStandaloneQuads(state.getTransform(), stack).allQuads();
    }

    /**
     * Shared by getQuads(ItemStack) and getQuadsForPreview(): no neighbours to connect to either way (there's
     * either no real position at all, or - for the preview - a position we shouldn't be reading a block entity
     * from, since nothing is actually placed there yet), just the given orientation textured from the stack.
     */
    private BakedQuadContainer buildStandaloneQuads(ITrans3 transform, ItemStack stack) {
        var material = ItemShape.getStateFromStack(stack);
        var texture = getTextureForState(material);
        var builder = new RoofGeometryBuilder(this.shape, transform, null, null, texture, texture);
        builder.render();
        return builder.build();
    }

    /**
     * The actual quad generator. One instance per getQuads() call - not reused, not thread-shared.
     */
    private static class RoofGeometryBuilder {

        private final EnumShape shape;
        private final ITrans3 transform;
        private final Level level;
        private final BlockPos pos;
        private final TextureAtlasSprite baseTexture;
        private final TextureAtlasSprite secondaryTexture;
        private final BakedQuadContainer.Builder container = new BakedQuadContainer.Builder();

        // Per-face state, set by normal()/beginOuterFace()/beginInnerFace() and consumed by endFace().
        private TextureAtlasSprite currentTexture;
        private Direction cullDirection; // non-null for axis-aligned outer faces, null for sloped/general faces
        private IVector3Immutable currentNormal;
        private final java.util.List<double[]> currentVerts = new java.util.ArrayList<>(); // {x,y,z,u,v} in LOCAL (-0.5..0.5) space

        RoofGeometryBuilder(EnumShape shape, ITrans3 transform, Level level, BlockPos pos, TextureAtlasSprite baseTexture, TextureAtlasSprite secondaryTexture) {
            this.shape = shape;
            this.transform = transform;
            this.level = level;
            this.pos = pos;
            this.baseTexture = baseTexture;
            this.secondaryTexture = secondaryTexture;
        }

        BakedQuadContainer build() {
            return this.container.build();
        }

        void render() {
            switch (this.shape) {
                case ROOF_TILE -> this.renderSlope();
                case ROOF_OUTER_CORNER -> this.renderOuterCorner();
                case ROOF_INNER_CORNER -> this.renderInnerCorner();
                case ROOF_RIDGE -> this.renderRidge();
                case ROOF_SMART_RIDGE -> this.renderSmartRidge();
                case ROOF_VALLEY -> this.renderValley();
                case ROOF_SMART_VALLEY -> this.renderSmartValley();
                default -> throw new IllegalStateException("BakedModelRoofGeneric does not support " + this.shape);
            }
        }

        // ------------------------------------------------------------------------------------------------
        // Ported 1:1 (modulo the -0.5 offset baked into vertex() here instead of at every call site) from
        // RenderRoof.java in the 1.12 branch. See that file for the original comments/structure.

        private void renderSlope() {
            boolean valley = this.valleyAt(0, 0, 1);
            this.beginNegZSlope();
            if (valley) {
                this.beginTriangle();
                this.vertex(1, 1, 1, 0, 0);
                this.vertex(1, 0, 0, 0, 1);
                this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
                this.newTriangle();
                this.vertex(1, 0, 0, 0, 1);
                this.vertex(0, 0, 0, 1, 1);
                this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
                this.newTriangle();
                this.vertex(0, 0, 0, 1, 1);
                this.vertex(0, 1, 1, 1, 0);
                this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
                this.endFace();
                this.connectValleyBack();
            } else {
                this.beginQuad();
                this.vertex(1, 1, 1, 0, 0);
                this.vertex(1, 0, 0, 0, 1);
                this.vertex(0, 0, 0, 1, 1);
                this.vertex(0, 1, 1, 1, 0);
                this.endFace();
            }
            this.leftTriangle();
            this.rightTriangle();
            this.bottomQuad();
            if (!valley) this.backQuad();
            if (this.ridgeAt(0, 0, -1)) this.connectRidgeFront();
        }

        private void renderOuterCorner() {
            this.beginNegZSlope();
            this.beginTriangle();
            this.vertex(0, 1, 1, 1, 0);
            this.vertex(1, 0, 0, 0, 1);
            this.vertex(0, 0, 0, 1, 1);
            this.endFace();
            this.beginPosXSlope();
            this.beginTriangle();
            this.vertex(0, 1, 1, 0, 0);
            this.vertex(1, 0, 1, 0, 1);
            this.vertex(1, 0, 0, 1, 1);
            this.endFace();

            this.beginPosZFace();
            this.beginTriangle();
            this.vertex(0, 1, 1, 0, 0);
            this.vertex(0, 0, 1, 0, 1);
            this.vertex(1, 0, 1, 1, 1);
            this.endFace();
            this.rightTriangle();
            this.bottomQuad();

            if (this.ridgeAt(0, 0, -1)) this.connectRidgeFront();
            if (this.ridgeAt(1, 0, 0)) this.connectRidgeLeft();
        }

        private void renderInnerCorner() {
            this.beginPosXSlope();
            this.beginTriangle();
            this.vertex(0, 1, 0, 1, 0);
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(1, 0, 0, 1, 1);
            this.endFace();
            this.beginNegZSlope();
            this.beginTriangle();
            this.vertex(1, 1, 1, 0, 0);
            this.vertex(1, 0, 0, 0, 1);
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.endFace();

            this.beginNegZFace();
            this.beginTriangle();
            this.vertex(0, 1, 0, 1, 0);
            this.vertex(1, 0, 0, 0, 1);
            this.vertex(0, 0, 0, 1, 1);
            this.endFace();
            this.leftTriangle();
            this.bottomQuad();

            if (this.valleyAt(0, 0, 1)) this.connectValleyBack();
            else this.terminateValleyBack();
            if (this.valleyAt(-1, 0, 0)) this.connectValleyRight();
            else this.terminateValleyRight();
        }

        private void renderRidge() {
            this.beginNegZSlope();
            this.beginQuad();
            this.vertex(1, 0.5, 0.5, 0, 0.5);
            this.vertex(1, 0, 0, 0, 1);
            this.vertex(0, 0, 0, 1, 1);
            this.vertex(0, 0.5, 0.5, 1, 0.5);
            this.endFace();
            this.ridgeBackSlope();
            this.ridgeFront(false);
            this.ridgeBack(false);

            this.ridgeLeftFace();
            this.ridgeRightFace();
            this.bottomQuad();
        }

        private void renderSmartRidge() {
            this.ridgeLeft();
            this.ridgeRight();
            this.ridgeBack(true);
            this.ridgeFront(true);
            this.bottomQuad();
        }

        private void renderValley() {
            this.connectValleyLeft();
            this.connectValleyRight();
            this.smartValleyFront();
            this.smartValleyBack();
            this.bottomQuad();
        }

        private void renderSmartValley() {
            this.smartValleyLeft();
            this.smartValleyRight();
            this.smartValleyFront();
            this.smartValleyBack();
            this.bottomQuad();
        }

        // ------------------------------------------------------------------------------------------------

        private void smartValleyLeft() {
            if (this.valleyOrSlopeAt(1, 0, 0)) this.connectValleyLeft();
            else this.terminateValleyLeft();
        }

        private void terminateValleyLeft() {
            this.beginNegXSlope();
            this.beginTriangle();
            this.vertex(1, 1, 0, 0, 0);
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(1, 1, 1, 1, 0);
            this.endFace();
            this.leftQuad();
        }

        private void smartValleyRight() {
            if (this.valleyOrSlopeAt(-1, 0, 0)) this.connectValleyRight();
            else this.terminateValleyRight();
        }

        private void terminateValleyRight() {
            this.beginPosXSlope();
            this.beginTriangle();
            this.vertex(0, 1, 1, 0, 0);
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(0, 1, 0, 1, 0);
            this.endFace();
            this.rightQuad();
        }

        private void smartValleyFront() {
            if (this.valleyOrSlopeAt(0, 0, -1)) this.connectValleyFront();
            else this.terminateValleyFront();
        }

        private void terminateValleyFront() {
            this.beginPosZSlope();
            this.beginTriangle();
            this.vertex(0, 1, 0, 0, 0);
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(1, 1, 0, 1, 0);
            this.endFace();
            this.frontQuad();
        }

        private void smartValleyBack() {
            if (this.valleyOrSlopeAt(0, 0, 1)) this.connectValleyBack();
            else this.terminateValleyBack();
        }

        private void terminateValleyBack() {
            this.beginNegZSlope();
            this.beginTriangle();
            this.vertex(1, 1, 1, 0, 0);
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(0, 1, 1, 1, 0);
            this.endFace();
            this.backQuad();
        }

        // ------------------------------------------------------------------------------------------------

        private void leftQuad() {
            this.beginPosXFace();
            this.beginQuad();
            this.vertex(1, 1, 1, 0, 0);
            this.vertex(1, 0, 1, 0, 1);
            this.vertex(1, 0, 0, 1, 1);
            this.vertex(1, 1, 0, 1, 0);
            this.endFace();
        }

        private void rightQuad() {
            this.beginNegXFace();
            this.beginQuad();
            this.vertex(0, 1, 0, 0, 0);
            this.vertex(0, 0, 0, 0, 1);
            this.vertex(0, 0, 1, 1, 1);
            this.vertex(0, 1, 1, 1, 0);
            this.endFace();
        }

        private void frontQuad() {
            this.beginNegZFace();
            this.beginQuad();
            this.vertex(1, 1, 0, 0, 0);
            this.vertex(1, 0, 0, 0, 1);
            this.vertex(0, 0, 0, 1, 1);
            this.vertex(0, 1, 0, 1, 0);
            this.endFace();
        }

        private void backQuad() {
            this.beginPosZFace();
            this.beginQuad();
            this.vertex(0, 1, 1, 0, 0);
            this.vertex(0, 0, 1, 0, 1);
            this.vertex(1, 0, 1, 1, 1);
            this.vertex(1, 1, 1, 1, 0);
            this.endFace();
        }

        private void bottomQuad() {
            this.beginBottomFace();
            this.beginQuad();
            this.vertex(0, 0, 1, 0, 0);
            this.vertex(0, 0, 0, 0, 1);
            this.vertex(1, 0, 0, 1, 1);
            this.vertex(1, 0, 1, 1, 0);
            this.endFace();
        }

        private void leftTriangle() {
            this.beginPosXFace();
            this.beginTriangle();
            this.vertex(1, 1, 1, 0, 0);
            this.vertex(1, 0, 1, 0, 1);
            this.vertex(1, 0, 0, 1, 1);
            this.endFace();
        }

        private void rightTriangle() {
            this.beginNegXFace();
            this.beginTriangle();
            this.vertex(0, 1, 1, 1, 0);
            this.vertex(0, 0, 0, 0, 1);
            this.vertex(0, 0, 1, 1, 1);
            this.endFace();
        }

        private void ridgeLeftFace() {
            this.beginPosXFace();
            this.beginTriangle();
            this.vertex(1, 0.5, 0.5, 0.5, 0.5);
            this.vertex(1, 0, 1, 0, 1);
            this.vertex(1, 0, 0, 1, 1);
            this.endFace();
        }

        private void ridgeRightFace() {
            this.beginNegXFace();
            this.beginTriangle();
            this.vertex(0, 0.5, 0.5, 0.5, 0.5);
            this.vertex(0, 0, 0, 0, 1);
            this.vertex(0, 0, 1, 1, 1);
            this.endFace();
        }

        private void ridgeBackSlope() {
            this.beginPosZSlope();
            this.beginQuad();
            this.vertex(0, 0.5, 0.5, 0, 0.5);
            this.vertex(0, 0, 1, 0, 1);
            this.vertex(1, 0, 1, 1, 1);
            this.vertex(1, 0.5, 0.5, 1, 0.5);
            this.endFace();
        }

        private void ridgeLeft() {
            if (this.ridgeOrSlopeAt(1, 0, 0)) {
                this.connectRidgeLeft();
            } else {
                this.beginPosXSlope();
                this.beginTriangle();
                this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
                this.vertex(1, 0, 1, 0, 1);
                this.vertex(1, 0, 0, 1, 1);
                this.endFace();
            }
        }

        private void connectRidgeLeft() {
            this.beginNegZSlope();
            this.beginTriangle();
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(1, 0.5, 0.5, 0, 0.5);
            this.vertex(1, 0, 0, 0, 1);
            this.endFace();
            this.beginPosZSlope();
            this.beginTriangle();
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(1, 0, 1, 1, 1);
            this.vertex(1, 0.5, 0.5, 1, 0.5);
            this.endFace();
        }

        private void ridgeRight() {
            if (this.ridgeOrSlopeAt(-1, 0, 0)) {
                this.connectRidgeRight();
            } else {
                this.beginNegXSlope();
                this.beginTriangle();
                this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
                this.vertex(0, 0, 0, 0, 1);
                this.vertex(0, 0, 1, 1, 1);
                this.endFace();
            }
        }

        private void connectRidgeRight() {
            this.beginNegZSlope();
            this.beginTriangle();
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(0, 0, 0, 1, 1);
            this.vertex(0, 0.5, 0.5, 1, 0.5);
            this.endFace();
            this.beginPosZSlope();
            this.beginTriangle();
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(0, 0.5, 0.5, 0, 0.5);
            this.vertex(0, 0, 1, 0, 1);
            this.endFace();
        }

        private void ridgeFront(boolean fill) {
            if (this.ridgeOrSlopeAt(0, 0, -1)) {
                this.connectRidgeFront();
            } else if (fill) {
                this.beginNegZSlope();
                this.beginTriangle();
                this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
                this.vertex(1, 0, 0, 0, 1);
                this.vertex(0, 0, 0, 1, 1);
                this.endFace();
            }
        }

        private void connectRidgeFront() {
            this.beginPosXSlope();
            this.beginTriangle();
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(1, 0, 0, 1, 1);
            this.vertex(0.5, 0.5, 0, 1, 0.5);
            this.endFace();
            this.beginNegXSlope();
            this.beginTriangle();
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(0.5, 0.5, 0, 0, 0.5);
            this.vertex(0, 0, 0, 0, 1);
            this.endFace();
        }

        private void ridgeBack(boolean fill) {
            if (this.ridgeOrSlopeAt(0, 0, 1)) {
                this.connectRidgeBack();
            } else if (fill) {
                this.beginPosZSlope();
                this.beginTriangle();
                this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
                this.vertex(0, 0, 1, 0, 1);
                this.vertex(1, 0, 1, 1, 1);
                this.endFace();
            }
        }

        private void connectRidgeBack() {
            this.beginPosXSlope();
            this.beginTriangle();
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(0.5, 0.5, 1, 0, 0.5);
            this.vertex(1, 0, 1, 0, 1);
            this.endFace();
            this.beginNegXSlope();
            this.beginTriangle();
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(0, 0, 1, 1, 1);
            this.vertex(0.5, 0.5, 1, 1, 0.5);
            this.endFace();
        }

        private void connectValleyLeft() {
            this.beginPosZSlope();
            this.beginTriangle();
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(1, 0.5, 0.5, 1, 0.5);
            this.vertex(1, 1, 0, 1, 0);
            this.endFace();
            this.beginNegZSlope();
            this.beginTriangle();
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(1, 1, 1, 0, 0);
            this.vertex(1, 0.5, 0.5, 0, 0.5);
            this.endFace();
            this.valleyEndLeft();
        }

        private void connectValleyRight() {
            this.beginPosZSlope();
            this.beginTriangle();
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(0, 1, 0, 0, 0);
            this.vertex(0, 0.5, 0.5, 0, 0.5);
            this.endFace();
            this.beginNegZSlope();
            this.beginTriangle();
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(0, 0.5, 0.5, 1, 0.5);
            this.vertex(0, 1, 1, 1, 0);
            this.endFace();
            this.valleyEndRight();
        }

        private void connectValleyFront() {
            this.beginPosXSlope();
            this.beginTriangle();
            this.vertex(0, 1, 0, 1, 0);
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(0.5, 0.5, 0, 1, 0.5);
            this.endFace();
            this.beginNegXSlope();
            this.beginTriangle();
            this.vertex(1, 1, 0, 0, 0);
            this.vertex(0.5, 0.5, 0, 0, 0.5);
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.endFace();
            this.valleyEndFront();
        }

        private void connectValleyBack() {
            this.beginPosXSlope();
            this.beginTriangle();
            this.vertex(0, 1, 1, 0, 0);
            this.vertex(0.5, 0.5, 1, 0, 0.5);
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.endFace();
            this.beginNegXSlope();
            this.beginTriangle();
            this.vertex(1, 1, 1, 1, 0);
            this.vertex(0.5, 0.5, 0.5, 0.5, 0.5);
            this.vertex(0.5, 0.5, 1, 1, 0.5);
            this.endFace();
            this.valleyEndBack();
        }

        private void valleyEndLeft() {
            this.beginPosXFace();
            this.beginTriangle();
            this.vertex(1, 1, 1, 0, 0);
            this.vertex(1, 0, 1, 0, 1);
            this.vertex(1, 0.5, 0.5, 0.5, 0.5);
            this.newTriangle();
            this.vertex(1, 0, 1, 0, 1);
            this.vertex(1, 0, 0, 1, 1);
            this.vertex(1, 0.5, 0.5, 0.5, 0.5);
            this.newTriangle();
            this.vertex(1, 0, 0, 1, 1);
            this.vertex(1, 1, 0, 1, 0);
            this.vertex(1, 0.5, 0.5, 0.5, 0.5);
            this.endFace();
        }

        private void valleyEndRight() {
            this.beginNegXFace();
            this.beginTriangle();
            this.vertex(0, 0, 1, 1, 1);
            this.vertex(0, 1, 1, 1, 0);
            this.vertex(0, 0.5, 0.5, 0.5, 0.5);
            this.newTriangle();
            this.vertex(0, 0, 0, 0, 1);
            this.vertex(0, 0, 1, 1, 1);
            this.vertex(0, 0.5, 0.5, 0.5, 0.5);
            this.newTriangle();
            this.vertex(0, 1, 0, 0, 0);
            this.vertex(0, 0, 0, 0, 1);
            this.vertex(0, 0.5, 0.5, 0.5, 0.5);
            this.endFace();
        }

        private void valleyEndFront() {
            this.beginNegZFace();
            this.beginTriangle();
            this.vertex(1, 1, 0, 0, 0);
            this.vertex(1, 0, 0, 0, 1);
            this.vertex(0.5, 0.5, 0, 0.5, 0.5);
            this.newTriangle();
            this.vertex(1, 0, 0, 0, 1);
            this.vertex(1, 0, 0, 1, 1);
            this.vertex(0.5, 0.5, 0, 0.5, 0.5);
            this.newTriangle();
            this.vertex(0, 0, 0, 1, 1);
            this.vertex(0, 1, 0, 1, 0);
            this.vertex(0.5, 0.5, 0, 0.5, 0.5);
            this.endFace();
        }

        private void valleyEndBack() {
            this.beginPosZFace();
            this.beginTriangle();
            this.vertex(0, 1, 1, 0, 0);
            this.vertex(0, 0, 1, 0, 1);
            this.vertex(0.5, 0.5, 1, 0.5, 0.5);
            this.newTriangle();
            this.vertex(0, 0, 1, 0, 1);
            this.vertex(1, 0, 1, 1, 1);
            this.vertex(0.5, 0.5, 1, 0.5, 0.5);
            this.newTriangle();
            this.vertex(1, 0, 1, 1, 1);
            this.vertex(1, 1, 1, 1, 0);
            this.vertex(0.5, 0.5, 1, 0.5, 0.5);
            this.endFace();
        }

        // ------------------------------------------------------------------------------------------------
        // Neighbour detection. dx/dy/dz are in the shape's own LOCAL frame (e.g. (0,0,-1) always means "the
        // direction this tile slopes down towards"), transformed into a world Direction via this block's own
        // placement transform before looking at the actual neighbouring block.

        private boolean ridgeAt(int dx, int dy, int dz) {
            return this.hasNeighbour(dx, dy, dz, RIDGE_SHAPES);
        }

        private boolean ridgeOrSlopeAt(int dx, int dy, int dz) {
            return this.hasNeighbour(dx, dy, dz, RIDGE_OR_SLOPE_SHAPES);
        }

        private boolean valleyAt(int dx, int dy, int dz) {
            return this.hasNeighbour(dx, dy, dz, VALLEY_SHAPES);
        }

        private boolean valleyOrSlopeAt(int dx, int dy, int dz) {
            return this.hasNeighbour(dx, dy, dz, VALLEY_OR_SLOPE_SHAPES);
        }

        private boolean hasNeighbour(int dx, int dy, int dz, EnumShape[] shapes) {
            if (this.level == null || this.pos == null) {
                return false;
            }
            var localDir = Direction.getNearest(dx, dy, dz);
            var worldDir = this.transform.transformDirection(localDir);
            var neighbourPos = this.pos.relative(worldDir);
            var neighbourState = this.level.getBlockState(neighbourPos);
            if (!(neighbourState.getBlock() instanceof BlockShape<?> neighbourBlock)) {
                return false;
            }
            var neighbourShape = neighbourBlock.getShape();
            for (var shape : shapes) {
                if (neighbourShape == shape) return true;
            }
            return false;
        }

        // ------------------------------------------------------------------------------------------------
        // Face-plane selection. "Outer" faces are the ones flush with the block's own bounding cube (bottom,
        // and the two vertical triangles/quads either side) - these get culled against solid neighbours like
        // any normal cube face. "Inner"/sloped faces are never axis-aligned, so they're never culled.

        private void beginTopFace() {
            this.beginOuterFace(IVector3.ofImmutable(0, 1, 0));
        }

        private void beginBottomFace() {
            this.beginOuterFace(IVector3.ofImmutable(0, -1, 0));
        }

        private void beginPosXFace() {
            this.beginOuterFace(IVector3.ofImmutable(1, 0, 0));
        }

        private void beginNegXFace() {
            this.beginOuterFace(IVector3.ofImmutable(-1, 0, 0));
        }

        private void beginPosZFace() {
            this.beginOuterFace(IVector3.ofImmutable(0, 0, 1));
        }

        private void beginNegZFace() {
            this.beginOuterFace(IVector3.ofImmutable(0, 0, -1));
        }

        private void beginPosXSlope() {
            this.beginInnerFace(IVector3.ofImmutable(1, 1, 0));
        }

        private void beginNegXSlope() {
            this.beginInnerFace(IVector3.ofImmutable(-1, 1, 0));
        }

        private void beginPosZSlope() {
            this.beginInnerFace(IVector3.ofImmutable(0, 1, 1));
        }

        private void beginNegZSlope() {
            this.beginInnerFace(IVector3.ofImmutable(0, 1, -1));
        }

        private void beginOuterFace(IVector3 localNormal) {
            this.currentTexture = this.baseTexture;
            var worldNormal = this.transform.transformNormalImmutable(localNormal);
            this.currentNormal = worldNormal;
            this.cullDirection = Direction.getNearest(worldNormal.x(), worldNormal.y(), worldNormal.z());
        }

        private void beginInnerFace(IVector3 localNormal) {
            this.currentTexture = this.secondaryTexture;
            this.currentNormal = this.transform.transformNormalImmutable(localNormal);
            this.cullDirection = null;
        }

        // ------------------------------------------------------------------------------------------------
        // Vertex/quad accumulation. Coordinates given to vertex() are 0..1 across the block, matching the
        // 1.12 source directly, and converted to our -0.5..0.5 local space here instead of at every call site.

        private void beginQuad() {
            this.currentVerts.clear();
        }

        private void beginTriangle() {
            this.currentVerts.clear();
        }

        private void newTriangle() {
            this.endFace();
            this.beginTriangle();
        }

        private void vertex(double x, double y, double z, double u, double v) {
            // NOT "x - 0.5" etc: unlike the 1.12 source this was ported from (which used its own -0.5..0.5
            // local convention), our runtime mesh space is 0..1 - OBJSON.fromResource() shifts loaded .objson
            // files (authored in -0.5..0.5) by ITrans3.BLOCK_CENTER (+0.5,+0.5,+0.5) specifically to land in
            // that same 0..1 space before any placement transform is ever applied to them. These literal
            // vertex(x,y,z,...) calls, copied straight from 1.12, are already the 0..1 unit-cube coordinates
            // its own vertex() helper converted *from* - so here they need to be used as-is.
            this.currentVerts.add(new double[]{x, y, z, u, v});
        }

        private void endFace() {
            if (this.currentVerts.isEmpty()) {
                return;
            }
            var baker = new QuadBakingVertexConsumer();
            baker.setSprite(this.currentTexture);
            baker.setTintIndex(-1);
            baker.setDirection(this.cullDirection != null ? this.cullDirection : Direction.getNearest(this.currentNormal.x(), this.currentNormal.y(), this.currentNormal.z()));
            baker.setShade(true);
            baker.setHasAmbientOcclusion(true);

            int vertCount = this.currentVerts.size();
            // Triangles get their first vertex duplicated to make a degenerate quad - QuadBakingVertexConsumer
            // only accepts exactly 4 vertices, same trick BakedQuadContainerProviderMesh uses for tris.
            int startIndex = vertCount == 3 ? -1 : 0;
            for (int i = startIndex; i < vertCount; i++) {
                var v = this.currentVerts.get(Math.max(0, i));
                var localPos = IVector3.ofImmutable(v[0], v[1], v[2]);
                var worldPos = this.transform.transformPosImmutable(localPos);
                baker.addVertex((float) worldPos.x(), (float) worldPos.y(), (float) worldPos.z())
                        .setColor(-1)
                        .setNormal((float) this.currentNormal.x(), (float) this.currentNormal.y(), (float) this.currentNormal.z())
                        .setUv(this.currentTexture.getU((float) v[3]), this.currentTexture.getV((float) v[4]))
                        .setUv2(1, 0)
                        .setUv1(1, 0);
            }
            this.container.addQuad(baker.bakeQuad(), this.cullDirection != null);
            this.currentVerts.clear();
        }
    }

}
