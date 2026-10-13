package com.blastdoor.blockentity;

import com.blastdoor.BlastDoorMod;
import com.blastdoor.block.DoorGeometry;
import com.blastdoor.block.SideDoorGeometry;
import com.blastdoor.block.SideDoorPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * Распашная гермодверь 3x3 на рельсах: створка на вертикальных петлях поворачивается из проёма
 * в карман сбоку от пути.
 *
 * <p>Как и у гермозатвора, коллизий две: проём проходим с половины хода, а карман становится твёрдым,
 * только когда створка встала в него до конца.</p>
 */
public class SideDoorBlockEntity extends AbstractRailDoorBlockEntity {
    /** Время полного хода створки в тиках (20 тиков = 1 с). */
    public static final int TRAVEL_TICKS = 144;

    public SideDoorBlockEntity(BlockPos pos, BlockState state) {
        super(BlastDoorMod.SIDE_BLAST_DOOR_BE.get(), pos, state, TRAVEL_TICKS);
    }

    @Override
    protected Iterable<BlockPos> structurePositions() {
        return SideDoorGeometry.allPositions(worldPosition, facing(), mirrored());
    }

    @Override
    protected void setPassable(boolean passable) {
        setOpen(false, passable);
    }

    /** Никого нет ни в проёме, ни перед ним — там, где створка проходит, закрываясь. */
    @Override
    protected boolean isClearToClose() {
        AABB box = SideDoorGeometry.swingZone(worldPosition, facing());
        return level.getEntities((Entity) null, box, AbstractDoorBlockEntity::blocksClosing).isEmpty();
    }

    /** Створка встала в карман: всё, что там было, выталкивается к пути, а не остаётся внутри. */
    @Override
    protected void onFullyOpen() {
        Direction outward = DoorGeometry.lateral(facing());
        int side = SideDoorGeometry.side(mirrored());
        AABB pocket = SideDoorGeometry.pocket(worldPosition, facing(), mirrored());
        for (Entity e : level.getEntities((Entity) null, pocket, e -> !e.isSpectator())) {
            // Ближняя к пути грань кармана — в полутора блоках от центра контроллера.
            double offset = side * (1.5 - e.getBbWidth() / 2.0 - 0.01);
            double x = outward.getAxis() == Direction.Axis.X
                    ? worldPosition.getX() + 0.5 + outward.getStepX() * offset : e.getX();
            double z = outward.getAxis() == Direction.Axis.Z
                    ? worldPosition.getZ() + 0.5 + outward.getStepZ() * offset : e.getZ();
            e.teleportTo(x, e.getY(), z);
        }
        setOpen(true, true);
    }

    /** Створка выходит из кармана: он снова пуст. */
    @Override
    protected void onStartClosing() {
        setOpen(true, false);
    }

    /** Переключить {@code open} у блоков одного вида: кармана или проёма (вместе с контроллером). */
    private void setOpen(boolean pocketCells, boolean open) {
        Direction facing = facing();
        boolean mirrored = mirrored();
        for (int a = 0; a < SideDoorGeometry.SIZE; a++) {
            for (int dy = 0; dy < SideDoorGeometry.SIZE; dy++) {
                BlockPos p = pocketCells
                        ? SideDoorGeometry.pocketPos(worldPosition, facing, mirrored, a, dy)
                        : DoorGeometry.partPos(worldPosition, facing, a, dy);
                BlockState s = level.getBlockState(p);
                if (s.is(BlastDoorMod.SIDE_BLAST_DOOR.get())
                        || (s.is(BlastDoorMod.SIDE_BLAST_DOOR_PART.get()) && s.getValue(SideDoorPartBlock.POCKET) == pocketCells)) {
                    level.setBlock(p, s.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
                }
            }
        }
    }
}
