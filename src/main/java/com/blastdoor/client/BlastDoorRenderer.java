package com.blastdoor.client;

import com.blastdoor.BlastDoorMod;
import com.blastdoor.block.BlastDoorBlock;
import com.blastdoor.blockentity.BlastDoorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Рисует створку гермодвери и направляющие рельсы, по которым она откатывается.
 *
 * <p>Локальная система координат (после поворота): начало — центр низа блока-контроллера,
 * +Z — вдоль пути (куда смотрел игрок), +X — поперёк пути, влево от игрока.
 * Створка занимает x ∈ [-1.5; 1.5], y ∈ [0; 3] и при открытии уезжает на 3 блока вдоль X.</p>
 *
 * <p>Текстура {@code textures/entity/blast_door.png} 64x64:
 * лицевая сторона створки — (0,0)-(48,48), образцы материалов — столбец 48..64.</p>
 */
public class BlastDoorRenderer implements BlockEntityRenderer<BlastDoorBlockEntity, BlastDoorRenderState> {
    public static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BlastDoorMod.MODID, "textures/entity/blast_door.png");

    private static final float TEX = 64.0F;

    // Области текстуры в пикселях: u0, v0, u1, v1
    private static final float[] FACE = {0, 0, 48, 46};
    private static final float[] PAINT = {48, 0, 64, 16};
    private static final float[] STEEL = {48, 16, 64, 32};
    private static final float[] BRASS = {48, 32, 64, 48};
    private static final float[] RUBBER = {48, 48, 64, 64};
    private static final float[] HAZARD = {0, 48, 44, 52};

    private static final float P = 1.0F / 16.0F; // один пиксель в блоках

    public BlastDoorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public BlastDoorRenderState createRenderState() {
        return new BlastDoorRenderState();
    }

    @Override
    public void extractRenderState(BlastDoorBlockEntity be, BlastDoorRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, cameraPos, crumblingOverlay);
        state.facing = be.getBlockState().getValue(BlastDoorBlock.FACING);
        state.mirrored = be.getBlockState().getValue(BlastDoorBlock.MIRRORED);
        state.progress = be.getRenderProgress(partialTick);
    }

    @Override
    public void submit(BlastDoorRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        final float dir = state.mirrored ? -1.0F : 1.0F;          // куда откатывается створка
        final float p = state.progress;
        final float eased = p * p * (3.0F - 2.0F * p);            // плавный разгон и торможение
        final float slide = dir * 3.0F * eased;
        final float bolts = 1.0F - Mth.clamp(p * 8.0F, 0.0F, 1.0F); // ригели прячутся в начале хода
        final int light = state.lightCoords;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));

        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(TEXTURE), (pose, vc) -> {
            Mesh m = new Mesh(pose, vc, light);
            drawGuideRail(m, dir);
            drawLeaf(m, slide, dir, bolts);
        });

        poseStack.popPose();
    }

    /** Неподвижная направляющая в полу: под проёмом и под «карманом», куда уезжает створка. */
    private static void drawGuideRail(Mesh m, float dir) {
        float a = dir > 0 ? -1.5F : -4.5F;
        float b = dir > 0 ? 4.5F : 1.5F;
        m.box(a, 0.0F, -1.5F * P, b, 0.04F, 1.5F * P, STEEL, null);
    }

    private static void drawLeaf(Mesh m, float sx, float dir, float bolts) {
        final float bottom = 2 * P;
        final float top = 3.0F;
        final float half = 5 * P;      // полутолщина основного листа (10 px)
        final float rim = 8 * P;       // до этой глубины — рамка и рёбра (16 px, как коллизия);
                                       // штурвал и вентили выступают на 3 px и остаются внутри блока

        // Основной лист
        m.box(sx - 1.5F, bottom, -half, sx + 1.5F, top, half, PAINT, FACE);

        for (int s = -1; s <= 1; s += 2) {
            float z0 = s > 0 ? half : -rim;
            float z1 = s > 0 ? rim : -half;

            // Рамка по периметру (2 px)
            m.box(sx - 1.5F, bottom, z0, sx - 1.5F + 2 * P, top, z1, PAINT, null);
            m.box(sx + 1.5F - 2 * P, bottom, z0, sx + 1.5F, top, z1, PAINT, null);
            m.box(sx - 1.5F + 2 * P, top - 2 * P, z0, sx + 1.5F - 2 * P, top, z1, PAINT, null);
            m.box(sx - 1.5F + 2 * P, bottom, z0, sx + 1.5F - 2 * P, bottom + 2 * P, z1, PAINT, null);

            // Горизонтальные рёбра жёсткости
            m.box(sx - 1.5F + 2 * P, 1.0F - P, z0, sx + 1.5F - 2 * P, 1.0F + P, z1, PAINT, null);
            m.box(sx - 1.5F + 2 * P, 2.0F - P, z0, sx + 1.5F - 2 * P, 2.0F + P, z1, PAINT, null);

            // Предупреждающая полоса внизу
            float hz0 = s > 0 ? half : -half - 0.5F * P;
            float hz1 = s > 0 ? half + 0.5F * P : -half;
            m.box(sx - 1.5F + 2 * P, bottom + 2 * P, hz0, sx + 1.5F - 2 * P, bottom + 6 * P, hz1, HAZARD, HAZARD);

            drawHandwheel(m, sx, 1.5F, s, half);
            drawValve(m, sx - 0.85F, 2.45F, s, half);
            drawValve(m, sx + 0.85F, 2.45F, s, half);
        }

        // Ригели на набегающей кромке (входят в косяк, когда дверь закрыта)
        float edge = -dir * 1.5F + sx;
        float out = bolts * 3 * P;
        if (out > 0.001F) {
            for (float y : new float[]{0.6F, 1.5F, 2.4F}) {
                float xa = dir > 0 ? edge - out : edge;
                float xb = dir > 0 ? edge : edge + out;
                m.box(xa, y - 1.5F * P, -1.5F * P, xb, y + 1.5F * P, 1.5F * P, STEEL, null);
            }
        }

        // Резиновый уплотнитель по набегающей кромке
        float sealA = dir > 0 ? edge - 0.5F * P : edge;
        float sealB = dir > 0 ? edge : edge + 0.5F * P;
        m.box(sealA, bottom, -half, sealB, top, half, RUBBER, RUBBER);

        // Катки внизу, на направляющей
        for (float x : new float[]{-1.0F, 1.0F}) {
            m.box(sx + x - 2.5F * P, 0.04F, -1.5F * P, sx + x + 2.5F * P, bottom, 1.5F * P, STEEL, STEEL);
        }
    }

    /** Отрезок по глубине от поверхности листа: от a до b пикселей наружу на стороне s. */
    private static float[] depth(int s, float half, float a, float b) {
        float z0 = s * (half + a * P);
        float z1 = s * (half + b * P);
        return new float[]{Math.min(z0, z1), Math.max(z0, z1)};
    }

    /** Штурвал задрайки: шток от поверхности листа, обод и крестовина над рёбрами. */
    private static void drawHandwheel(Mesh m, float cx, float cy, int s, float half) {
        float r = 5 * P;      // внешний радиус обода
        float t = 1 * P;      // толщина обода

        // Опорная шайба и шток — вплотную к листу
        float[] base = depth(s, half, 0, 0.5F);
        m.box(cx - 2 * P, cy - 2 * P, base[0], cx + 2 * P, cy + 2 * P, base[1], STEEL, STEEL);
        float[] stem = depth(s, half, 0.5F, 2);
        m.box(cx - P, cy - P, stem[0], cx + P, cy + P, stem[1], STEEL, STEEL);

        // Обод и спицы
        float[] w = depth(s, half, 2, 3);
        m.box(cx - r, cy + r - t, w[0], cx + r, cy + r, w[1], BRASS, BRASS);
        m.box(cx - r, cy - r, w[0], cx + r, cy - r + t, w[1], BRASS, BRASS);
        m.box(cx - r, cy - r + t, w[0], cx - r + t, cy + r - t, w[1], BRASS, BRASS);
        m.box(cx + r - t, cy - r + t, w[0], cx + r, cy + r - t, w[1], BRASS, BRASS);
        m.box(cx - r + t, cy - 0.5F * P, w[0], cx + r - t, cy + 0.5F * P, w[1], STEEL, STEEL);
        m.box(cx - 0.5F * P, cy - r + t, w[0], cx + 0.5F * P, cy + r - t, w[1], STEEL, STEEL);
    }

    /** Небольшой вентиль (как на фото — справа вверху): корпус на листе и латунный маховичок. */
    private static void drawValve(Mesh m, float cx, float cy, int s, float half) {
        float[] body = depth(s, half, 0, 2);
        m.box(cx - 2 * P, cy - 2 * P, body[0], cx + 2 * P, cy + 2 * P, body[1], STEEL, STEEL);
        float[] cap = depth(s, half, 2, 3);
        m.box(cx - 1 * P, cy - 1 * P, cap[0], cx + 1 * P, cy + 1 * P, cap[1], BRASS, BRASS);
    }

    @Override
    public AABB getRenderBoundingBox(BlastDoorBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        return new AABB(pos.getX() - 4.5, pos.getY(), pos.getZ() - 4.5,
                pos.getX() + 5.5, pos.getY() + 3.25, pos.getZ() + 5.5);
    }

    // ---------------------------------------------------------------- построение коробок

    /** Мини-построитель параллелепипедов с текстурой и нормалями. */
    private record Mesh(PoseStack.Pose pose, VertexConsumer vc, int light) {

        /**
         * @param side  область текстуры для боковых граней (±X, ±Y)
         * @param front область для граней ±Z (лицевые стороны); null — как у боковых
         */
        void box(float x0, float y0, float z0, float x1, float y1, float z1, float[] side, float @Nullable [] front) {
            float[] f = front != null ? front : side;
            // -Z
            quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, f, 0, 0, -1);
            // +Z
            quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, f, 0, 0, 1);
            // -X
            quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, side, -1, 0, 0);
            // +X
            quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, side, 1, 0, 0);
            // -Y
            quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, side, 0, -1, 0);
            // +Y
            quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, side, 0, 1, 0);
        }

        /** Вершины: низ-лево, низ-право, верх-право, верх-лево (если смотреть на грань снаружи). */
        private void quad(float ax, float ay, float az, float bx, float by, float bz,
                          float cx, float cy, float cz, float dx, float dy, float dz,
                          float[] uv, float nx, float ny, float nz) {
            float u0 = uv[0] / TEX, v0 = uv[1] / TEX, u1 = uv[2] / TEX, v1 = uv[3] / TEX;
            vertex(ax, ay, az, u0, v1, nx, ny, nz);
            vertex(bx, by, bz, u1, v1, nx, ny, nz);
            vertex(cx, cy, cz, u1, v0, nx, ny, nz);
            vertex(dx, dy, dz, u0, v0, nx, ny, nz);
        }

        private void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
            vc.addVertex(pose, x, y, z)
                    .setColor(255, 255, 255, 255)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }
}