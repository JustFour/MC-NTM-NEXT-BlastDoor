package com.blastdoor.blockentity;

import com.blastdoor.BlastDoorMod;
import com.blastdoor.block.BlastDoorBlock;
import com.blastdoor.block.BlastDoorPartBlock;
import com.blastdoor.block.DoorGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Логика и анимация гермодвери.
 *
 * <p>{@code progress}: 0 — створка закрыта, 1 — полностью откатилась.
 * Проём становится проходимым ({@code open=true} у всех 9 блоков) только когда створка полностью открыта.
 * При закрытии коллизия появляется сразу, но дверь начинает ехать, только если проём пуст —
 * вагонетку или игрока она не «прищемит».</p>
 */
public class BlastDoorBlockEntity extends BlockEntity {
    /** Время полного хода створки в тиках (20 тиков = 1 с). */
    public static final int TRAVEL_TICKS = 50;
    private static final float SPEED = 1.0F / TRAVEL_TICKS;

    /** Зазор между створкой и бортом остановленного транспорта. */
    private static final double CLEARANCE_GAP = 0.02;

    private boolean targetOpen;
    private float progress;
    private float prevProgress;
    private int moveTicks;

    public BlastDoorBlockEntity(BlockPos pos, BlockState state) {
        super(BlastDoorMod.BLAST_DOOR_BE.get(), pos, state);
    }

    // ---------------------------------------------------------------- редстоун

    /** Перепроверить сигнал редстоуна на всех 9 блоках структуры. */
    public void updatePower() {
        if (level == null || level.isClientSide()) return;
        Direction facing = getBlockState().getValue(BlastDoorBlock.FACING);
        boolean powered = false;
        for (int dx = 0; dx < 3 && !powered; dx++) {
            for (int dy = 0; dy < 3 && !powered; dy++) {
                powered = level.hasNeighborSignal(DoorGeometry.partPos(worldPosition, facing, dx, dy));
            }
        }
        if (powered != targetOpen) {
            targetOpen = powered;
            if (targetOpen && progress < 1.0F) {
                playSound(SoundEvents.PISTON_EXTEND, 0.8F, 0.55F);
            }
            setChanged();
            sync();
        }
    }

    // ---------------------------------------------------------------- тики

    public void serverTick() {
        if (level == null) return;
        keepRailShapeAligned();
        prevProgress = progress;
        boolean passable = getBlockState().getValue(BlastDoorBlock.OPEN);
        boolean moving = false;

        if (targetOpen) {
            if (progress < 1.0F) {
                progress = Math.min(1.0F, progress + SPEED);
                moving = true;
                if (progress >= 1.0F) {
                    setStructureOpen(true);
                    playSound(SoundEvents.IRON_DOOR_CLOSE, 1.0F, 0.5F);
                }
                setChanged();
            }
        } else if (passable) {
            // Пора закрываться: ждём, пока проём освободится.
            if (doorwayIsClear()) {
                setStructureOpen(false);
                playSound(SoundEvents.PISTON_CONTRACT, 0.8F, 0.55F);
                sync();
            }
        } else if (progress > 0.0F) {
            progress = Math.max(0.0F, progress - SPEED);
            moving = true;
            if (progress <= 0.0F) {
                playSound(SoundEvents.ANVIL_LAND, 0.35F, 0.6F);
            }
            setChanged();
        }

        if (moving) {
            if (moveTicks++ % 10 == 0) playSound(SoundEvents.GRINDSTONE_USE, 0.35F, 0.5F);
        } else {
            moveTicks = 0;
        }

        if (!getBlockState().getValue(BlastDoorBlock.OPEN)) {
            holdBackVehicles();
        }
    }

    public void clientTick() {
        prevProgress = progress;
        if (targetOpen) {
            progress = Math.min(1.0F, progress + SPEED);
        } else if (!getBlockState().getValue(BlastDoorBlock.OPEN)) {
            progress = Math.max(0.0F, progress - SPEED);
        }
    }

    public float getRenderProgress(float partialTick) {
        return Mth.lerp(partialTick, prevProgress, progress);
    }

    // ---------------------------------------------------------------- структура

    /**
     * Ванильная логика соединения рельсов может повернуть свойство {@code shape} соседним рельсом.
     * Наши вагонетки этого не замечают (направление берётся из {@code facing}), но поезда Traincraft
     * читают именно {@code shape} — поэтому возвращаем его по оси двери.
     */
    private void keepRailShapeAligned() {
        BlockState state = getBlockState();
        RailShape expected = state.getValue(BlastDoorBlock.FACING).getAxis() == Direction.Axis.X
                ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        if (state.getValue(BlastDoorBlock.SHAPE) != expected) {
            level.setBlock(worldPosition, state.setValue(BlastDoorBlock.SHAPE, expected), Block.UPDATE_CLIENTS);
        }
    }

    private void setStructureOpen(boolean open) {
        if (level == null) return;
        Direction facing = getBlockState().getValue(BlastDoorBlock.FACING);
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                BlockPos p = DoorGeometry.partPos(worldPosition, facing, dx, dy);
                BlockState s = level.getBlockState(p);
                if (s.is(BlastDoorMod.BLAST_DOOR.get())) {
                    level.setBlock(p, s.setValue(BlastDoorBlock.OPEN, open), Block.UPDATE_ALL);
                } else if (s.is(BlastDoorMod.BLAST_DOOR_PART.get())) {
                    level.setBlock(p, s.setValue(BlastDoorPartBlock.OPEN, open), Block.UPDATE_ALL);
                }
            }
        }
    }

    private boolean doorwayIsClear() {
        Direction facing = getBlockState().getValue(BlastDoorBlock.FACING);
        AABB box = DoorGeometry.doorway(worldPosition, facing, 0.0);
        return level.getEntities((Entity) null, box, BlastDoorBlockEntity::blocksClosing).isEmpty();
    }

    private static boolean blocksClosing(Entity e) {
        return e.isAlive() && !e.isSpectator() && !(e instanceof ItemEntity) && !(e instanceof ExperienceOrb);
    }

    /**
     * Страховка поверх коллизии: транспорт не может проехать сквозь закрытую дверь, даже если его
     * физика решит иначе. Работает для ванильных вагонеток и для любого другого транспорта на базе
     * {@link VehicleEntity} — в том числе поездов Traincraft, которые ездят по ванильным рельсам.
     */
    private void holdBackVehicles() {
        Direction facing = getBlockState().getValue(BlastDoorBlock.FACING);
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

    private void playSound(SoundEvent sound, float volume, float pitch) {
        if (level != null && !level.isClientSide()) {
            level.playSound(null, worldPosition.above(), sound, SoundSource.BLOCKS, volume, pitch);
        }
    }

    // ---------------------------------------------------------------- сохранение и синхронизация

    private void sync() {
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("target_open", targetOpen);
        output.putFloat("progress", progress);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        targetOpen = input.getBooleanOr("target_open", false);
        progress = Mth.clamp(input.getFloatOr("progress", 0.0F), 0.0F, 1.0F);
        prevProgress = progress;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}