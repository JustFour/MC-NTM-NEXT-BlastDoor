package com.blastdoor.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Геометрия распашной гермодвери 3x3 на рельсах.
 *
 * <p>Створка толщиной в блок висит на вертикальных петлях у края проёма. Закрыта — стоит в проёме
 * поперёк пути (те же блоки (dx, dy), что и у откатной двери, см. {@link DoorGeometry}). Открыта —
 * стоит вдоль пути в «кармане» сбоку: это колонна шириной в блок сразу за краем проёма, 3 блока в длину
 * от плоскости двери вглубь (куда смотрел игрок) и 3 в высоту. Блоки кармана — (dz, dy).</p>
 *
 * <p>Карман слева от игрока, а с {@code mirrored} — справа.</p>
 */
public final class SideDoorGeometry {
    public static final int SIZE = 3;

    private SideDoorGeometry() {}

    /** +1 — карман слева от игрока, -1 — справа. */
    public static int side(boolean mirrored) {
        return mirrored ? -1 : 1;
    }

    public static BlockPos pocketPos(BlockPos controller, Direction facing, boolean mirrored, int dz, int dy) {
        return controller.relative(DoorGeometry.lateral(facing), 2 * side(mirrored)).relative(facing, dz).above(dy);
    }

    public static BlockPos controllerFromPocket(BlockPos part, Direction facing, boolean mirrored, int dz, int dy) {
        return part.below(dy).relative(facing, -dz).relative(DoorGeometry.lateral(facing), -2 * side(mirrored));
    }

    /** Все 18 блоков структуры: 9 блоков проёма (включая контроллер) и 9 блоков кармана. */
    public static List<BlockPos> allPositions(BlockPos controller, Direction facing, boolean mirrored) {
        List<BlockPos> positions = new ArrayList<>(2 * SIZE * SIZE);
        for (int a = 0; a < SIZE; a++) {
            for (int dy = 0; dy < SIZE; dy++) {
                positions.add(DoorGeometry.partPos(controller, facing, a, dy));
                positions.add(pocketPos(controller, facing, mirrored, a, dy));
            }
        }
        return positions;
    }

    /** Карман: объём, который занимает открытая створка. */
    public static AABB pocket(BlockPos controller, Direction facing, boolean mirrored) {
        return AABB.encapsulatingFullBlocks(
                pocketPos(controller, facing, mirrored, 0, 0),
                pocketPos(controller, facing, mirrored, SIZE - 1, SIZE - 1));
    }

    /** Проём и пространство перед ним, через которое створка проходит, закрываясь. Карман сюда не входит. */
    public static AABB swingZone(BlockPos controller, Direction facing) {
        return AABB.encapsulatingFullBlocks(
                DoorGeometry.partPos(controller, facing, 0, 0),
                DoorGeometry.partPos(controller, facing, SIZE - 1, SIZE - 1).relative(facing, SIZE - 1));
    }
}
