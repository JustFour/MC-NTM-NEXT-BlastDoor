package com.blastdoor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Отрисовка рельса под дверью. В обычной сборке рисовать нечего: контроллер — ванильный рельс
 * со своей блочной моделью. В сборке под Traincraft ({@code -Ptc}) здесь рисуется его путь.
 */
final class RailTrackVisual {
    RailTrackVisual(BlockEntityRendererProvider.Context context) {}

    /** Состояние для одного кадра; хранится в render state двери как непрозрачный объект. */
    @Nullable Object createState() {
        return null;
    }

    void extract(BlockEntity be, @Nullable Object state, float partialTick, Vec3 cameraPos,
                 ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {}

    void submit(@Nullable Object state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {}
}
