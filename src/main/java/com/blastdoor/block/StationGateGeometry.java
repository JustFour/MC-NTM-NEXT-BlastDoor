package com.blastdoor.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Геометрия станционного гермозатвора 4x4.
 *
 * <p>Затвор — плита толщиной в блок на петле у пола. Открыт: плита лежит в углублении и служит полом
 * (блоки «пола»). Закрыт: плита стоит вертикально в проёме (блоки «проёма»), а на её месте в полу
 * остаётся приямок глубиной в блок.</p>
 *
 * <p>{@code facing} — куда смотрел игрок при установке, то есть сквозь проём. Поперечная ось
 * {@link #lateral(Direction)} — «влево» от игрока, dx = 0..3 (контроллер — dx = 1).
 * Блоки пола адресуются как (dx, dz): dz = 0..3 от игрока вглубь, ряд dz = 0 лежит под проёмом.
 * Блоки проёма — (dx, dy): dy = 0..3 вверх от уровня пола. Контроллер — блок пола (1, 0).</p>
 */
public final class StationGateGeometry {
    public static final int SIZE = 4;
    private static final int CONTROLLER_DX = 1;

    /** Дно приямка: остаётся на месте плиты, когда она поднята. */
    public static final VoxelShape PIT_FLOOR = Block.box(0, 0, 0, 16, 2, 16);

    private StationGateGeometry() {}

    public static Direction lateral(Direction facing) {
        return facing.getCounterClockWise();
    }

    public static BlockPos floorPos(BlockPos controller, Direction facing, int dx, int dz) {
        return controller.relative(lateral(facing), dx - CONTROLLER_DX).relative(facing, dz);
    }

    public static BlockPos planePos(BlockPos controller, Direction facing, int dx, int dy) {
        return controller.relative(lateral(facing), dx - CONTROLLER_DX).above(dy + 1);
    }

    public static BlockPos controllerFromFloor(BlockPos part, Direction facing, int dx, int dz) {
        return part.relative(facing, -dz).relative(lateral(facing), CONTROLLER_DX - dx);
    }

    public static BlockPos controllerFromPlane(BlockPos part, Direction facing, int dx, int dy) {
        return part.below(dy + 1).relative(lateral(facing), CONTROLLER_DX - dx);
    }

    public static boolean isController(int dx, int dz) {
        return dx == CONTROLLER_DX && dz == 0;
    }

    /** Все 32 блока структуры: 16 блоков пола (включая контроллер) и 16 блоков проёма. */
    public static List<BlockPos> allPositions(BlockPos controller, Direction facing) {
        List<BlockPos> positions = new ArrayList<>(2 * SIZE * SIZE);
        for (int dx = 0; dx < SIZE; dx++) {
            for (int n = 0; n < SIZE; n++) {
                positions.add(floorPos(controller, facing, dx, n));
                positions.add(planePos(controller, facing, dx, n));
            }
        }
        return positions;
    }

    /** Приямок: объём, который занимает лежащая плита. */
    public static AABB pit(BlockPos controller, Direction facing) {
        return AABB.encapsulatingFullBlocks(
                floorPos(controller, facing, 0, 0),
                floorPos(controller, facing, SIZE - 1, SIZE - 1));
    }

    /** Всё, что плита задевает при подъёме: проём и пространство над лежащей плитой. */
    public static AABB sweptVolume(BlockPos controller, Direction facing) {
        return AABB.encapsulatingFullBlocks(
                planePos(controller, facing, 0, 0),
                planePos(controller, facing, SIZE - 1, SIZE - 1).relative(facing, SIZE - 1));
    }
}
