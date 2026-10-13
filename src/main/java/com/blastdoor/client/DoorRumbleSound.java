package com.blastdoor.client;

import com.blastdoor.blockentity.AbstractDoorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Звук работы двери: низкий глухой гул, который плавно нарастает, звучит без пауз, пока створка
 * движется, и затихает, когда она остановилась. Ведётся на клиенте, поэтому обрывается ровно
 * вместе с движением.
 */
class DoorRumbleSound extends AbstractTickableSoundInstance implements AbstractDoorBlockEntity.MotionSound {
    private static final float MAX_VOLUME = 0.55F;
    private static final float FADE_PER_TICK = 0.07F;
    /** Стук вагонетки, опущенный почти на октаву, звучит как тяжёлый механизм за стеной. */
    private static final float PITCH = 0.55F;

    private final AbstractDoorBlockEntity door;

    private DoorRumbleSound(AbstractDoorBlockEntity door) {
        super(SoundEvents.MINECART_RIDING, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.door = door;
        this.looping = true;
        this.delay = 0;
        this.volume = FADE_PER_TICK;
        this.pitch = PITCH;
        BlockPos pos = door.getBlockPos();
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 1.5;
        this.z = pos.getZ() + 0.5;
    }

    /** Запустить гул для двери; подставляется в {@link AbstractDoorBlockEntity#motionSoundFactory}. */
    static AbstractDoorBlockEntity.MotionSound start(AbstractDoorBlockEntity door) {
        DoorRumbleSound sound = new DoorRumbleSound(door);
        Minecraft.getInstance().getSoundManager().play(sound);
        return sound;
    }

    @Override
    public void tick() {
        if (door.isRemoved()) {
            stop();
        } else if (door.isMoving()) {
            volume = Math.min(MAX_VOLUME, volume + FADE_PER_TICK);
        } else {
            volume -= FADE_PER_TICK;
            if (volume <= 0.0F) {
                stop();
            }
        }
    }

    @Override
    public boolean isFinished() {
        return isStopped();
    }
}
