package com.blastdoor.blockentity;

import com.blastdoor.block.AbstractRailDoorBlock;
import com.blastdoor.block.DoorGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import traincraft.vehicle.entity.RollingStockEntity;

/**
 * Общее для дверей на рельсах в сборке под Traincraft: контроллер — блок его пути в нижнем центре
 * проёма 3x3, а пока проём не проходим, дверь останавливает подходящие к ней поезда.
 *
 * <p>Останавливать их приходится заранее: состав, упёршийся в сплошной блок быстрее 10 км/ч,
 * Traincraft считает разбитым навсегда. Поэтому перед закрытой дверью поезда тормозят так же,
 * как перед красным сигналом Traincraft, и у самой двери встают.</p>
 */
public abstract class AbstractRailDoorBlockEntity extends AbstractDoorBlockEntity {
    /** На таком расстоянии от двери вдоль пути поезда начинают тормозить. */
    private static final double BRAKE_RANGE = 16.0;
    /** Доля скорости, которая остаётся каждый тик, как у сигнала Traincraft. */
    private static final double BRAKE_FACTOR = 0.85;
    /** Ближе этого от створки до передней точки состава поезд стоит. */
    private static final double STOP_DISTANCE = 1.0;
    /** Зазор между створкой и бортом состава, оказавшегося внутри закрывшейся двери. */
    private static final double CLEARANCE_GAP = 0.02;

    protected AbstractRailDoorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int travelTicks) {
        super(type, pos, state, travelTicks);
    }

    protected Direction facing() {
        return getBlockState().getValue(AbstractRailDoorBlock.FACING);
    }

    protected boolean mirrored() {
        return getBlockState().getValue(AbstractRailDoorBlock.MIRRORED);
    }

    /** Проходимость проёма хранит сам контроллер: он стоит в проёме. */
    @Override
    protected boolean isPassable() {
        return getBlockState().getValue(AbstractRailDoorBlock.OPEN);
    }

    @Override
    protected void afterServerTick() {
        if (!isPassable()) {
            stopApproachingStock();
        }
    }

    /**
     * Тормозит составы, которые едут к закрытой двери, и не подпускает их вплотную. Составы, которые
     * стоят или уезжают, не трогает. Если состав всё же оказался в самой двери, выдвигает его наружу.
     */
    private void stopApproachingStock() {
        Direction.Axis axis = facing().getAxis();
        double plane = (axis == Direction.Axis.Z ? worldPosition.getZ() : worldPosition.getX()) + 0.5;
        AABB zone = DoorGeometry.doorway(worldPosition, facing(), BRAKE_RANGE);

        for (RollingStockEntity stock : level.getEntitiesOfClass(RollingStockEntity.class, zone)) {
            double along = axis == Direction.Axis.Z ? stock.getZ() : stock.getX();
            double offset = along - plane;                         // со знаком: с какой стороны двери состав
            double side = offset >= 0 ? 1.0 : -1.0;
            Vec3 toDoor = axis == Direction.Axis.Z ? new Vec3(0, 0, -side) : new Vec3(-side, 0, 0);
            double reach = reachTowards(stock, toDoor);            // от центра состава до его ближнего к двери конца
            double gap = Math.abs(offset) - DoorGeometry.HALF_THICKNESS - reach;

            Vec3 motion = stock.getDeltaMovement();
            double speedAlong = axis == Direction.Axis.Z ? motion.z : motion.x;
            boolean approaching = speedAlong * side < 0;

            if (gap < -CLEARANCE_GAP) {
                // Внутри двери: выдвигаем вдоль пути. Сдвиг делает сам Traincraft, чтобы тележка
                // локомотива поехала вместе с кузовом.
                double shift = side * (CLEARANCE_GAP - gap);
                stock.shiftAlongTrack(axis == Direction.Axis.Z ? new Vec3(0, 0, shift) : new Vec3(shift, 0, 0));
                stock.setDeltaMovement(Vec3.ZERO);
            } else if (approaching && gap < STOP_DISTANCE) {
                stock.setDeltaMovement(axis == Direction.Axis.Z
                        ? new Vec3(motion.x, motion.y, 0) : new Vec3(0, motion.y, motion.z));
            } else if (approaching) {
                stock.setDeltaMovement(motion.x * BRAKE_FACTOR, motion.y, motion.z * BRAKE_FACTOR);
            }
        }
    }

    /**
     * Насколько кузов состава выступает от его центра в сторону {@code direction} (единичный вектор
     * вдоль пути). Длину кузова знает сам Traincraft; она больше основной коллизии сущности.
     */
    private static double reachTowards(RollingStockEntity stock, Vec3 direction) {
        double alongBody = stock.endFacing(direction).subtract(stock.position()).dot(direction);
        return Math.max(alongBody, stock.getBbWidth() / 2.0);
    }
}
