package com.blastdoor.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/** Снимок данных для отрисовки одного кадра распашной гермодвери. */
public class SideDoorRenderState extends BlockEntityRenderState {
    public Direction facing = Direction.NORTH;
    public boolean mirrored;
    /** 0 — створка в проёме, 1 — в кармане; уже интерполировано по partialTick. */
    public float progress;
    /** Состояние отрисовки рельса под дверью; чем оно является, знает только RailTrackVisual. */
    public @Nullable Object track;
    /** Сигнальная лампа на створке сейчас горит: она мигает, пока створка движется. */
    public boolean lampLit;
}
