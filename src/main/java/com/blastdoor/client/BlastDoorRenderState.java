package com.blastdoor.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/** Снимок данных для отрисовки одного кадра (рендер в 26.x работает через render state). */
public class BlastDoorRenderState extends BlockEntityRenderState {
    public Direction facing = Direction.NORTH;
    public boolean mirrored;
    /** 0 — закрыта, 1 — открыта; уже интерполировано по partialTick. */
    public float progress;
    /** Сигнальные лампы на створке сейчас горят: они мигают, пока створка движется. */
    public boolean lampsLit;
}
