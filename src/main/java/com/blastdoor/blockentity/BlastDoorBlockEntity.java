package com.blastdoor.blockentity;

import com.blastdoor.BlastDoorMod;
import com.blastdoor.block.DoorGeometry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/** Откатная гермодверь 3x3 на рельсах: створка уезжает вбок. */
public class BlastDoorBlockEntity extends AbstractRailDoorBlockEntity {
    /** Время полного хода створки в тиках (20 тиков = 1 с). */
    public static final int TRAVEL_TICKS = 120;

    public BlastDoorBlockEntity(BlockPos pos, BlockState state) {
        super(BlastDoorMod.BLAST_DOOR_BE.get(), pos, state, TRAVEL_TICKS);
    }

    @Override
    protected Iterable<BlockPos> structurePositions() {
        Direction facing = facing();
        List<BlockPos> positions = new ArrayList<>(9);
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                positions.add(DoorGeometry.partPos(worldPosition, facing, dx, dy));
            }
        }
        return positions;
    }

    /** У всех 9 блоков двери одно свойство {@code open}: оно и убирает коллизию. */
    @Override
    protected void setPassable(boolean passable) {
        for (BlockPos p : structurePositions()) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlastDoorMod.BLAST_DOOR.get()) || s.is(BlastDoorMod.BLAST_DOOR_PART.get())) {
                level.setBlock(p, s.setValue(BlockStateProperties.OPEN, passable), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected boolean isClearToClose() {
        AABB box = DoorGeometry.doorway(worldPosition, facing(), 0.0);
        return level.getEntities((Entity) null, box, AbstractDoorBlockEntity::blocksClosing).isEmpty();
    }
}
