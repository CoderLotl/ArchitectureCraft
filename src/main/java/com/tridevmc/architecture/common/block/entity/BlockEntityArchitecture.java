package com.tridevmc.architecture.common.block.entity;

import com.tridevmc.architecture.common.model.ModelProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public class BlockEntityArchitecture extends BlockEntity {

    public BlockEntityArchitecture(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Vanilla's default implementation sends no data at all, which means our custom state (e.g. shape materials)
     * never reaches clients that weren't present when the block was placed. Override to keep clients in sync.
     */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    /**
     * Our baked models read the level/position out of ModelData to look up the shape's material state (see
     * ModelProperties). The default getModelData() returns ModelData.EMPTY, so without this override that
     * lookup always sees null and every shape renders with its texture-resolver fallback (oak planks) once
     * placed in the world, even though the item icon renders correctly.
     */
    @Override
    public ModelData getModelData() {
        return ModelData.builder()
                .with(ModelProperties.LEVEL, this.getLevel())
                .with(ModelProperties.POS, this.getBlockPos())
                .with(ModelProperties.TILE, this)
                .build();
    }

    /**
     * The default handleUpdateTag() applies the synced data but never invalidates the model data cache, so a
     * client that receives an updated material after the block already rendered once would keep showing the
     * stale texture until some unrelated re-render happened to occur.
     */
    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        super.handleUpdateTag(tag, lookupProvider);
        this.refreshModelAndRerender();
    }

    /**
     * Placement itself is what actually triggers this path (via Level#sendBlockUpdated in
     * ItemShape#placeBlock): the block is placed speculatively on the client with no material set yet, and
     * this packet is what delivers the real one a moment later. Same cache-invalidation gap as handleUpdateTag.
     */
    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        super.onDataPacket(net, pkt, lookupProvider);
        this.refreshModelAndRerender();
    }

    /**
     * requestModelDataUpdate() only invalidates ModelDataManager's cache - it's lazily re-read the next time
     * something *else* happens to trigger a chunk section rebuild, which is why the wrong material stuck around
     * until an unrelated nearby placement forced a rebuild. sendBlockUpdated(pos, state, state, ...) on the
     * client's own level goes straight to LevelRenderer.blockChanged(), which forces that rebuild immediately -
     * this is the standard pairing NeoForge expects for block entity data changes that affect rendering.
     */
    private void refreshModelAndRerender() {
        this.requestModelDataUpdate();
        var level = this.getLevel();
        if (level != null && level.isClientSide()) {
            var state = this.getBlockState();
            level.sendBlockUpdated(this.getBlockPos(), state, state, 3);
        }
    }

}
