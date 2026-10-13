package com.blastdoor.block;

import com.blastdoor.BlastDoorMod;
import com.blastdoor.blockentity.BlastDoorBlockEntity;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Контроллер откатной гермодвери 3x3: рельс в нижнем центре проёма и 8 частей вокруг него.
 * Створка уезжает вбок; {@code mirrored} — в другую сторону.
 */
public class BlastDoorBlock extends AbstractRailDoorBlock {
    public static final MapCodec<BlastDoorBlock> CODEC = simpleCodec(BlastDoorBlock::new);

    public BlastDoorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<BlastDoorBlock> codec() {
        return CODEC;
    }

    @Override
    protected List<BlockPos> partPositions(BlockPos controller, Direction facing, boolean mirrored) {
        List<BlockPos> positions = new ArrayList<>(8);
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                if (!DoorGeometry.isController(dx, dy)) {
                    positions.add(DoorGeometry.partPos(controller, facing, dx, dy));
                }
            }
        }
        return positions;
    }

    @Override
    protected void placeParts(Level level, BlockPos controller, BlockState controllerState) {
        Direction facing = controllerState.getValue(FACING);
        BlockState part = BlastDoorMod.BLAST_DOOR_PART.get().defaultBlockState()
                .setValue(BlastDoorPartBlock.FACING, facing)
                .setValue(BlastDoorPartBlock.OPEN, false);
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                if (DoorGeometry.isController(dx, dy)) continue;
                level.setBlock(DoorGeometry.partPos(controller, facing, dx, dy),
                        part.setValue(BlastDoorPartBlock.DX, dx).setValue(BlastDoorPartBlock.DY, dy),
                        Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected boolean isPartOf(BlockPos partPos, BlockState partState, BlockPos controller) {
        return partState.is(BlastDoorMod.BLAST_DOOR_PART.get())
                && BlastDoorPartBlock.controllerPos(partPos, partState).equals(controller);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlastDoorBlockEntity(pos, state);
    }
}
