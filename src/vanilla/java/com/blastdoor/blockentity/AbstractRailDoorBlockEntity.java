package com.blastdoor.blockentity;

import com.blastdoor.block.AbstractRailDoorBlock;
import com.blastdoor.block.DoorGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Общее для дверей на рельсах: контроллер — рельс в нижнем центре проёма 3x3, а пока проём
 * не проходим, дверь дополнительно удерживает транспорт на пути.
 */
public abstract class AbstractRailDoorBlockEntity extends AbstractDoorBlockEntity {
    /** Зазор между створкой и бортом остановленного транспорта. */
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
    protected void beforeServerTick() {
        keepRailShapeAligned();
    }

    @Override
    protected void afterServerTick() {
        if (!isPassable()) {
            holdBackVehicles();
        }
    }

    /**
     * Ванильная логика соединения рельсов может повернуть свойство {@code shape} соседним рельсом.
     * Наши вагонетки этого не замечают (направление берётся из {@code facing}), но поезда Traincraft
     * читают именно {@code shape} — поэтому возвращаем его по оси двери.
     */
    private void keepRailShapeAligned() {
        BlockState state = getBlockState();
        RailShape expected = facing().getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        if (state.getValue(AbstractRailDoorBlock.SHAPE) != expected) {
            level.setBlock(worldPosition, state.setValue(AbstractRailDoorBlock.SHAPE, expected), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Страховка поверх коллизии: транспорт не может проехать сквозь закрытую дверь, даже если его
     * физика решит иначе. Работает для ванильных вагонеток и для любого другого транспорта на базе
     * {@link VehicleEntity} — в том числе поездов Traincraft, которые ездят по ванильным рельсам.
     */
    private void holdBackVehicles() {
        Direction facing = facing();
        AABB zone = DoorGeometry.doorway(worldPosition, facing, 1.5);
        Direction.Axis axis = facing.getAxis();
        double plane = (axis == Direction.Axis.Z ? worldPosition.getZ() : worldPosition.getX()) + 0.5;

        for (VehicleEntity vehicle : level.getEntitiesOfClass(VehicleEntity.class, zone)) {
            double clearance = DoorGeometry.HALF_THICKNESS + vehicle.getBbWidth() / 2.0 + CLEARANCE_GAP;
            double along = axis == Direction.Axis.Z ? vehicle.getZ() : vehicle.getX();
            double d = along - plane;
            if (Math.abs(d) >= clearance) continue;

            double velAlong = axis == Direction.Axis.Z ? vehicle.getDeltaMovement().z : vehicle.getDeltaMovement().x;
            double side = d != 0 ? Math.signum(d) : (velAlong > 0 ? -1 : 1);
            double target = plane + side * clearance;
            if (axis == Direction.Axis.Z) {
                vehicle.setPos(vehicle.getX(), vehicle.getY(), target);
            } else {
                vehicle.setPos(target, vehicle.getY(), vehicle.getZ());
            }
            vehicle.setDeltaMovement(Vec3.ZERO);
        }
    }
}
