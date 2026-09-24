package com.tridevmc.architecture.client.render.model.baked;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tridevmc.architecture.common.block.entity.BlockEntityShape;
import com.tridevmc.architecture.common.block.state.BlockStateArchitecture;
import com.tridevmc.architecture.common.model.ModelProperties;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Extension to {@link IDynamicBakedModel} that delegates to a new method that accepts our own {@link BlockStateArchitecture} object instead of the vanilla {@link BlockState}.
 */
public interface IArchitectureBakedModel extends IDynamicBakedModel {


    @Override
    default @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData extraData, @Nullable RenderType renderType) {
        if (state instanceof BlockStateArchitecture stateArchitecture) {
            return this.getQuads(stateArchitecture, side, rand, extraData, renderType);
        } else if (state == null) {
            // This is likely an item model, so we'll still try to get quads for it.
            return this.getQuads(null, side, rand, extraData, renderType);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    default ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @Override
    default boolean isCustomRenderer() {
        return true;
    }

    /**
     * Reports which render layer(s) this block needs, so shapes made out of a translucent or cutout material
     * (glass, leaves, etc.) render with that material's transparency instead of always being forced fully
     * opaque. Derived from whichever material(s) are actually applied to the shape at this position - falls
     * back to solid if there's no block entity yet (e.g. during model probing/registration), matching vanilla's
     * own default.
     * <p>
     * This intentionally reports a single combined layer per block rather than splitting solid and translucent
     * quads into separate layers: a shape can mix a solid base material with a translucent secondary/cladding
     * material on the same faces, and this pipeline doesn't tag individual quads with which material produced
     * them (see {@link IModelResolverBaked#getQuads}), so splitting isn't possible without also reworking quad
     * storage to bucket by render type as well as cull face. Rendering solid-textured quads in the translucent
     * layer is visually indistinguishable (alpha 1 either way), so this is a safe simplification.
     */
    @Override
    @NotNull
    default ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        var level = data.get(ModelProperties.LEVEL);
        var pos = data.get(ModelProperties.POS);
        if (level == null || pos == null) {
            return ChunkRenderTypeSet.of(RenderType.solid());
        }
        var shapeBe = BlockEntityShape.getAt(level, pos);
        if (shapeBe == null) {
            return ChunkRenderTypeSet.of(RenderType.solid());
        }
        var baseType = ItemBlockRenderTypes.getChunkRenderType(shapeBe.getEffectiveBaseMaterialState());
        var secondaryType = ItemBlockRenderTypes.getChunkRenderType(shapeBe.getEffectiveSecondaryMaterialState());
        return ChunkRenderTypeSet.of(mostTransparentOf(baseType, secondaryType));
    }

    /**
     * Picks whichever of the two render types lets more light/background through, so a block combining a solid
     * material with a translucent or cutout one renders using the more permissive layer rather than clipping the
     * transparent material's holes/alpha to opaque.
     */
    private static RenderType mostTransparentOf(RenderType a, RenderType b) {
        if (a.equals(RenderType.translucent()) || b.equals(RenderType.translucent())) {
            return RenderType.translucent();
        } else if (a.equals(RenderType.cutoutMipped()) || b.equals(RenderType.cutoutMipped())) {
            return RenderType.cutoutMipped();
        } else if (a.equals(RenderType.cutout()) || b.equals(RenderType.cutout())) {
            return RenderType.cutout();
        } else {
            return RenderType.solid();
        }
    }

    /**
     * Gets a list of quads for the given state, side, rand, extraData, and renderType.
     *
     * @param state      The state of the block.
     * @param side       The side of the block to get quads for, refers to culled faces. Can be null for general quads.
     * @param rand       The random source.
     * @param extraData  The extra data.
     * @param renderType The render type.
     * @return A list of quads.
     */
    @NotNull
    List<BakedQuad> getQuads(@Nullable BlockStateArchitecture state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData extraData, @Nullable RenderType renderType);

    /**
     * Gets a list of quads for the given stack.
     *
     * @param stack The stack to get quads for.
     * @return A list of quads.
     */
    @NotNull
    List<BakedQuad> getQuads(@NotNull ItemStack stack);

    /**
     * Gets quads for a placement preview: the given (not-yet-placed) state's orientation/transform, but with
     * material resolved from the stack instead of a block entity, since nothing is actually placed yet - there
     * may not even be a block entity at the target position (or worse, there's an unrelated one already there).
     * <p>
     * The default just falls back to {@link #getQuads(ItemStack)}, which ignores the target orientation
     * entirely (always rendering the item's default pose) - implementations that can resolve texture from a
     * stack independently of a block position (which, as of writing, is all of them) should override this.
     *
     * @param state The orientation the shape would be placed in.
     * @param stack The stack being placed.
     * @return A list of quads, in the given orientation, textured for the given stack.
     */
    @NotNull
    default List<BakedQuad> getQuadsForPreview(@NotNull BlockStateArchitecture state, @NotNull ItemStack stack) {
        return this.getQuads(stack);
    }

}
