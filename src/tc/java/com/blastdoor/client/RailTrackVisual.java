package com.blastdoor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import traincraft.client.render.TrackRenderer;
import traincraft.client.render.state.TrackRenderState;
import traincraft.track.block.TrackBlockEntity;

/**
 * Отрисовка рельса под дверью в сборке под Traincraft: путь в проёме рисует рендерер самого
 * Traincraft, как для обычного короткого прямого отрезка. Наш рендерер только вызывает его перед
 * тем, как нарисовать створку.
 */
final class RailTrackVisual {
    private final TrackRenderer trackRenderer;

    RailTrackVisual(BlockEntityRendererProvider.Context context) {
        this.trackRenderer = new TrackRenderer(context);
    }

    /** Состояние для одного кадра; хранится в render state двери как непрозрачный объект. */
    @Nullable Object createState() {
        return trackRenderer.createRenderState();
    }

    void extract(BlockEntity be, @Nullable Object state, float partialTick, Vec3 cameraPos,
                 ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        if (be instanceof TrackBlockEntity track && state instanceof TrackRenderState trackState) {
            trackRenderer.extractRenderState(track, trackState, partialTick, cameraPos, crumblingOverlay);
        }
    }

    void submit(@Nullable Object state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state instanceof TrackRenderState trackState) {
            trackRenderer.submit(trackState, poseStack, collector, camera);
        }
    }
}
