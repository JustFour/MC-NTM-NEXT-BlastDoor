package com.blastdoor.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/** Снимок данных для отрисовки одного кадра распашной гермодвери. */
public class SideDoorRenderState extends BlockEntityRenderState {
    public Direction facing = Direction.NORTH;
    public boolean mirrored;
    /** 0 — створка в проёме, 1 — в кармане; уже интерполировано по partialTick. */
    public float progress;
    /** Сигнальная лампа на створке сейчас горит: она мигает, пока створка движется. */
    public boolean lampLit;
}
