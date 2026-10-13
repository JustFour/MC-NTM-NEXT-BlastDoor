package com.blastdoor.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/** Снимок данных для отрисовки одного кадра станционного гермозатвора. */
public class StationGateRenderState extends BlockEntityRenderState {
    public Direction facing = Direction.NORTH;
    /** 0 — плита стоит в проёме, 1 — лежит в полу; уже интерполировано по partialTick. */
    public float progress;
    /** Сигнальные лампы в приямке сейчас горят: они мигают, пока плита движется. */
    public boolean lampsLit;
}
