package com.blastdoor.blockentity;

import java.util.function.Function;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Общая логика и анимация всех гермодверей мода.
 *
 * <p>{@code progress}: 0 — створка закрыта, 1 — полностью открыта.
 * Проём теряет коллизию, когда створка прошла {@link #PASSABLE_FROM} хода, и получает её обратно
 * на той же отметке при закрытии.</p>
 *
 * <p>Створка никого не «прищемит»: она не трогается из открытого положения и не возвращает коллизию,
 * пока на её пути кто-то есть, — в это время дверь стоит и ждёт.</p>
 */
public abstract class AbstractDoorBlockEntity extends BlockEntity {
    /** Доля хода створки, начиная с которой проём проходим. */
    protected static final float PASSABLE_FROM = 0.5F;

    /** Звук работы двери, который клиент ведёт сам, пока створка движется. */
    public interface MotionSound {
        boolean isFinished();
    }

    /**
     * Запускает звук работы для двери. Задаётся клиентской частью мода при загрузке; здесь — заглушка,
     * чтобы общий код не ссылался на клиентские классы.
     */
    public static Function<AbstractDoorBlockEntity, @Nullable MotionSound> motionSoundFactory = door -> null;

    private final float speed;

    private boolean targetOpen;
    private float progress;
    private float prevProgress;
    /** Закрытие приостановлено: створке кто-то мешает. Клиенту это нужно, чтобы не доигрывать анимацию самому. */
    private boolean waiting;
    /** Звук работы на клиенте: гул, который звучит, пока створка движется. На сервере его нет. */
    private @Nullable MotionSound motionSound;

    /** @param travelTicks время полного хода створки в тиках (20 тиков = 1 с) */
    protected AbstractDoorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int travelTicks) {
        super(type, pos, state);
        this.speed = 1.0F / travelTicks;
    }

    // ---------------------------------------------------------------- что задаёт конкретная дверь

    /** Все блоки структуры, включая контроллер. */
    protected abstract Iterable<BlockPos> structurePositions();

    /** Проём сейчас без коллизии. Источник правды — состояния блоков, а не поле блок-сущности. */
    protected abstract boolean isPassable();

    /** Убрать или вернуть коллизию проёма. */
    protected abstract void setPassable(boolean passable);

    /** На пути створки никого нет: можно трогаться из открытого положения и возвращать коллизию. */
    protected abstract boolean isClearToClose();

    /** Створка дошла до полностью открытого положения. */
    protected void onFullyOpen() {}

    /** Створка трогается из полностью открытого положения. */
    protected void onStartClosing() {}

    protected void beforeServerTick() {}

    protected void afterServerTick() {}

    // ---------------------------------------------------------------- редстоун

    /** Перепроверить сигнал редстоуна на всех блоках структуры. */
    public void updatePower() {
        if (level == null || level.isClientSide()) return;
        boolean powered = false;
        for (BlockPos p : structurePositions()) {
            if (level.hasNeighborSignal(p)) {
                powered = true;
                break;
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
        beforeServerTick();
        prevProgress = progress;
        boolean blocked = false;

        if (targetOpen) {
            if (progress < 1.0F) {
                progress = Math.min(1.0F, progress + speed);
                if (progress >= PASSABLE_FROM && !isPassable()) {
                    setPassable(true);
                }
                if (progress >= 1.0F) {
                    onFullyOpen();
                    playSound(SoundEvents.IRON_DOOR_CLOSE, 1.0F, 0.5F);
                }
                setChanged();
            }
        } else if (progress > 0.0F) {
            float next = Math.max(0.0F, progress - speed);
            boolean starting = progress >= 1.0F;
            boolean restoringCollision = next < PASSABLE_FROM && isPassable();
            if ((starting || restoringCollision) && !isClearToClose()) {
                blocked = true;
            } else {
                if (starting) {
                    onStartClosing();
                    playSound(SoundEvents.PISTON_CONTRACT, 0.8F, 0.55F);
                }
                if (restoringCollision) {
                    setPassable(false);
                }
                progress = next;
                if (progress <= 0.0F) {
                    playSound(SoundEvents.ANVIL_LAND, 0.35F, 0.6F);
                }
                setChanged();
            }
        }

        if (blocked != waiting) {
            waiting = blocked;
            setChanged();
            sync();
        }

        afterServerTick();
    }

    public void clientTick() {
        prevProgress = progress;
        if (targetOpen) {
            progress = Math.min(1.0F, progress + speed);
        } else if (!waiting) {
            progress = Math.max(0.0F, progress - speed);
        }
        if (isMoving() && (motionSound == null || motionSound.isFinished())) {
            motionSound = motionSoundFactory.apply(this);
        }
    }

    /** Створка сдвинулась за последний тик. */
    public boolean isMoving() {
        return progress != prevProgress;
    }

    public float getRenderProgress(float partialTick) {
        return Mth.lerp(partialTick, prevProgress, progress);
    }

    // ---------------------------------------------------------------- общее для дверей

    /** Кто мешает створке двигаться: всё живое и транспорт, но не предметы и опыт. */
    protected static boolean blocksClosing(Entity e) {
        return e.isAlive() && !e.isSpectator() && !(e instanceof ItemEntity) && !(e instanceof ExperienceOrb);
    }

    protected void playSound(SoundEvent sound, float volume, float pitch) {
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
        output.putBoolean("waiting", waiting);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        targetOpen = input.getBooleanOr("target_open", false);
        progress = Mth.clamp(input.getFloatOr("progress", 0.0F), 0.0F, 1.0F);
        prevProgress = progress;
        waiting = input.getBooleanOr("waiting", false);
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
