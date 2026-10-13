package com.blastdoor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Геометрия структуры 3x3.
 *
 * <p>{@code facing} — направление, куда смотрел игрок при установке, то есть ось пути (вдоль рельса).
 * Плоскость двери перпендикулярна пути. Поперечная ось {@link #lateral(Direction)} — «влево» от игрока.
 * Части структуры адресуются как (dx, dy): dx = 0..2 поперёк пути (1 — центр), dy = 0..2 по высоте.
 * Контроллер (рельс) — это (1, 0).</p>
 */
public final class DoorGeometry {
    /** Половина толщины створки в блоках: створка занимает блок целиком, вплотную к соседним стенам. */
    public static final double HALF_THICKNESS = 0.5;

    private DoorGeometry() {}

    public static Direction lateral(Direction facing) {
        return facing.getCounterClockWise();
    }

    public static BlockPos partPos(BlockPos controller, Direction facing, int dx, int dy) {
        return controller.relative(lateral(facing), dx - 1).above(dy);
    }

    public static BlockPos controllerFromPart(BlockPos part, Direction facing, int dx, int dy) {
        return part.relative(lateral(facing), 1 - dx).below(dy);
    }

    public static boolean isController(int dx, int dy) {
        return dx == 1 && dy == 0;
    }

    /** Форма закрытой створки внутри одного блока. */
    public static VoxelShape panelShape() {
        return Shapes.block();
    }

    /** Объём всего проёма (3x3) в плоскости створки, с небольшим запасом. */
    public static AABB doorway(BlockPos controller, Direction facing, double extraDepth) {
        BlockPos a = partPos(controller, facing, 0, 0);
        BlockPos b = partPos(controller, facing, 2, 2);
        AABB box = AABB.encapsulatingFullBlocks(a, b);
        double depth = HALF_THICKNESS + extraDepth;
        if (facing.getAxis() == Direction.Axis.Z) {
            double c = controller.getZ() + 0.5;
            return new AABB(box.minX, box.minY, c - depth, box.maxX, box.maxY, c + depth);
        } else {
            double c = controller.getX() + 0.5;
            return new AABB(c - depth, box.minY, box.minZ, c + depth, box.maxY, box.maxZ);
        }
    }
}
