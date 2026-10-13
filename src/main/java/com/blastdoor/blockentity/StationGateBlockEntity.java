package com.blastdoor.blockentity;

import com.blastdoor.BlastDoorMod;
import com.blastdoor.block.StationGateBlock;
import com.blastdoor.block.StationGateGeometry;
import com.blastdoor.block.StationGatePartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * Станционный гермозатвор 4x4: плита на петле у пола. Открыт — плита лежит и служит полом,
 * закрыт — стоит в проёме, а на её месте остаётся приямок.
 *
 * <p>У затвора две независимые коллизии, и свойство {@code open} у блоков проёма и блоков пола
 * переключается в разное время: проём проходим ({@code open=true} у блоков проёма) с половины хода,
 * а полом плита становится ({@code open=true} у блоков пола) только когда легла до конца.</p>
 */
public class StationGateBlockEntity extends AbstractDoorBlockEntity {
    /** Время полного хода плиты в тиках (20 тиков = 1 с). */
    public static final int TRAVEL_TICKS = 192;

    public StationGateBlockEntity(BlockPos pos, BlockState state) {
        super(BlastDoorMod.STATION_GATE_BE.get(), pos, state, TRAVEL_TICKS);
    }

    private Direction facing() {
        return getBlockState().getValue(StationGateBlock.FACING);
    }

    @Override
    protected Iterable<BlockPos> structurePositions() {
        return StationGateGeometry.allPositions(worldPosition, facing());
    }

    /** Проходимость проёма читаем с блока проёма прямо над контроллером. */
    @Override
    protected boolean isPassable() {
        BlockState above = level.getBlockState(worldPosition.above());
        return above.is(BlastDoorMod.STATION_GATE_PART.get()) && above.getValue(StationGatePartBlock.OPEN);
    }

    @Override
    protected void setPassable(boolean passable) {
        setOpen(false, passable);
    }

    /** Никого нет ни в проёме, ни над лежащей плитой — там, где плита проходит при подъёме. */
    @Override
    protected boolean isClearToClose() {
        AABB box = StationGateGeometry.sweptVolume(worldPosition, facing());
        return level.getEntities((Entity) null, box, AbstractDoorBlockEntity::blocksClosing).isEmpty();
    }

    /** Плита легла в приямок и стала полом: всё, что там было, оказывается на ней, а не внутри. */
    @Override
    protected void onFullyOpen() {
        AABB pit = StationGateGeometry.pit(worldPosition, facing());
        for (Entity e : level.getEntities((Entity) null, pit, e -> !e.isSpectator())) {
            e.teleportTo(e.getX(), pit.maxY, e.getZ());
        }
        setOpen(true, true);
    }

    /** Плита отрывается от пола: на её месте снова приямок. */
    @Override
    protected void onStartClosing() {
        setOpen(true, false);
    }

    /** Переключить {@code open} у блоков одного вида: пола (вместе с контроллером) или проёма. */
    private void setOpen(boolean floorCells, boolean open) {
        Direction facing = facing();
        for (int dx = 0; dx < StationGateGeometry.SIZE; dx++) {
            for (int n = 0; n < StationGateGeometry.SIZE; n++) {
                BlockPos p = floorCells
                        ? StationGateGeometry.floorPos(worldPosition, facing, dx, n)
                        : StationGateGeometry.planePos(worldPosition, facing, dx, n);
                BlockState s = level.getBlockState(p);
                if (s.is(BlastDoorMod.STATION_GATE.get()) || s.is(BlastDoorMod.STATION_GATE_PART.get())) {
                    level.setBlock(p, s.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
                }
            }
        }
    }
}
