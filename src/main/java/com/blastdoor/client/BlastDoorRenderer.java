package com.blastdoor.client;

import com.blastdoor.BlastDoorMod;
import com.blastdoor.block.BlastDoorBlock;
import com.blastdoor.blockentity.BlastDoorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
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
 * <p>Текстура {@code textures/entity/blast_door.png} 256x256, 32 текселя на блок: лицевая сторона
 * створки — (0,0)-(96,92), предупреждающая полоса — (0,96)-(88,104), полки рамы с заклёпками —
 * (0,108)-(96,112) и (100,0)-(104,96), образцы материалов 32x32 — от (128,0).</p>
 */
public class BlastDoorRenderer implements BlockEntityRenderer<BlastDoorBlockEntity, BlastDoorRenderState> {
    public static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BlastDoorMod.MODID, "textures/entity/blast_door.png");

    private static final float TEX = 256.0F;

    // Области текстуры в текселях: u0, v0, u1, v1
    private static final float[] FACE = {0, 0, 96, 92};
    private static final float[] HAZARD = {0, 96, 88, 104};
    /** Полки рамы и рёбер с рядом заклёпок: горизонтальная (88x4) и вертикальная (4x92). */
    private static final float[] FLANGE_H = {0, 108, 88, 112};
    private static final float[] FLANGE_V = {100, 0, 104, 92};
    private static final float[] PAINT = {128, 0, 160, 32};
    private static final float[] STEEL = {160, 0, 192, 32};
    private static final float[] BRASS = {192, 0, 224, 32};
    private static final float[] RUBBER = {224, 0, 256, 32};
    private static final float[] LAMP_OFF = {128, 32, 160, 64};
    private static final float[] LAMP_ON = {160, 32, 192, 64};
    private static final float[] DARK = {192, 32, 224, 64};

    private static final float P = 1.0F / 16.0F; // один пиксель в блоках

    /** Сколькими прямыми сегментами набран обод штурвала. */
    private static final int WHEEL_SEGMENTS = 16;
    /** На сколько градусов поворачивается штурвал, пока отходят ригели. */
    private static final float WHEEL_TURN = 270.0F;

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
        boolean moving = state.progress > 0.001F && state.progress < 0.999F;
        state.lampsLit = moving && be.getLevel() != null && (be.getLevel().getGameTime() / 5) % 2 == 0;
    }

    @Override
    public void submit(BlastDoorRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        final float dir = state.mirrored ? -1.0F : 1.0F;          // куда откатывается створка
        final float p = state.progress;
        final float eased = p * p * (3.0F - 2.0F * p);            // плавный разгон и торможение
        final float slide = dir * 3.0F * eased;
        final float bolts = 1.0F - Mth.clamp(p * 8.0F, 0.0F, 1.0F); // ригели прячутся в начале хода
        final int light = state.lightCoords;
        final boolean lampsLit = state.lampsLit;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));

        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(TEXTURE), (pose, vc) -> {
            BoxMesh m = new BoxMesh(pose, vc, light, TEX);
            drawGuideRail(m, dir);
            drawLeaf(m, slide, dir, bolts, lampsLit);
        });

        poseStack.popPose();
    }

    /** Неподвижная направляющая в полу: под проёмом и под «карманом», куда уезжает створка. */
    private static void drawGuideRail(BoxMesh m, float dir) {
        float a = dir > 0 ? -1.5F : -4.5F;
        float b = dir > 0 ? 4.5F : 1.5F;
        m.box(a, 0.0F, -1.5F * P, b, 0.04F, 1.5F * P, STEEL, null);
    }

    private static void drawLeaf(BoxMesh m, float sx, float dir, float bolts, boolean lampsLit) {
        final float bottom = 2 * P;
        final float top = 3.0F;
        final float half = 5 * P;      // полутолщина основного листа (10 px)
        final float rim = 8 * P;       // до этой глубины — рамка и рёбра (16 px, как коллизия);
                                       // штурвал и лампы выступают на 3 px и остаются внутри блока

        // Основной лист
        m.box(sx - 1.5F, bottom, -half, sx + 1.5F, top, half, PAINT, FACE);

        // Горящая лампа не зависит от освещения вокруг.
        final BoxMesh glow = lampsLit ? m.withLight(LightCoordsUtil.FULL_BRIGHT) : m;
        final float[] lens = lampsLit ? LAMP_ON : LAMP_OFF;
        // Штурвал поворачивается, пока отходят ригели.
        final float wheelTurn = (1.0F - bolts) * WHEEL_TURN;

        for (int s = -1; s <= 1; s += 2) {
            float z0 = s > 0 ? half : -rim;
            float z1 = s > 0 ? rim : -half;

            // Рамка по периметру (2 px)
            m.box(sx - 1.5F, bottom, z0, sx - 1.5F + 2 * P, top, z1, PAINT, FLANGE_V);
            m.box(sx + 1.5F - 2 * P, bottom, z0, sx + 1.5F, top, z1, PAINT, FLANGE_V);
            m.box(sx - 1.5F + 2 * P, top - 2 * P, z0, sx + 1.5F - 2 * P, top, z1, PAINT, FLANGE_H);
            m.box(sx - 1.5F + 2 * P, bottom, z0, sx + 1.5F - 2 * P, bottom + 2 * P, z1, PAINT, FLANGE_H);

            // Горизонтальные рёбра жёсткости
            m.box(sx - 1.5F + 2 * P, 1.0F - P, z0, sx + 1.5F - 2 * P, 1.0F + P, z1, PAINT, FLANGE_H);
            m.box(sx - 1.5F + 2 * P, 2.0F - P, z0, sx + 1.5F - 2 * P, 2.0F + P, z1, PAINT, FLANGE_H);

            // Предупреждающая полоса внизу
            float hz0 = s > 0 ? half : -half - 0.5F * P;
            float hz1 = s > 0 ? half + 0.5F * P : -half;
            m.box(sx - 1.5F + 2 * P, bottom + 2 * P, hz0, sx + 1.5F - 2 * P, bottom + 6 * P, hz1, HAZARD, HAZARD);

            drawHandwheel(m, sx, 1.5F, s, half, wheelTurn);
            drawLamp(m, glow, lens, sx - 0.85F, 2.45F, s, half);
            drawLamp(m, glow, lens, sx + 0.85F, 2.45F, s, half);
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

    /**
     * Штурвал задрайки: шайба и шток от поверхности листа, круглый обод из прямых сегментов,
     * шесть спиц и ступица. Обе стороны сидят на одном валу и поворачиваются вместе.
     *
     * @param turn угол поворота штурвала в градусах
     */
    private static void drawHandwheel(BoxMesh m, float cx, float cy, int s, float half, float turn) {
        final float r = 5 * P;      // внешний радиус обода
        final float t = 1 * P;      // толщина обода
        final float hub = 1.25F * P;
        // Соседние детали штурвала перекрываются; чтобы их грани не совпадали и не мерцали,
        // каждая вторая утоплена на волосок.
        final float inset = 0.002F;

        // Опорная шайба и шток — вплотную к листу
        float[] base = depth(s, half, 0, 0.5F);
        m.box(cx - 2 * P, cy - 2 * P, base[0], cx + 2 * P, cy + 2 * P, base[1], STEEL, STEEL);
        float[] stem = depth(s, half, 0.5F, 2);
        m.box(cx - P, cy - P, stem[0], cx + P, cy + P, stem[1], STEEL, STEEL);

        float[] w = depth(s, half, 2, 3);
        BoxMesh wheel = m.translated(cx, cy, 0.0F).rotatedZ(turn);

        // Обод: сегменты по касательной, длиной в хорду внешнего радиуса
        final float chord = (float) (2 * r * Math.tan(Math.PI / WHEEL_SEGMENTS));
        for (int i = 0; i < WHEEL_SEGMENTS; i++) {
            float d = (i % 2) * inset;
            wheel.rotatedZ(i * 360.0F / WHEEL_SEGMENTS)
                    .box(r - t, -chord / 2, w[0] + d, r, chord / 2, w[1] - d, BRASS, BRASS);
        }
        // Спицы от ступицы к ободу
        for (int i = 0; i < 6; i++) {
            wheel.rotatedZ(i * 60.0F)
                    .box(hub, -0.4F * P, w[0] + 2 * inset, r - 0.5F * t, 0.4F * P, w[1] - 2 * inset, STEEL, STEEL);
        }
        wheel.box(-hub, -hub, w[0], hub, hub, w[1], STEEL, STEEL);
    }

    /** Сигнальная лампа: тёмный корпус на листе и янтарная линза. Мигает, пока створка движется. */
    private static void drawLamp(BoxMesh m, BoxMesh glow, float[] lens, float cx, float cy, int s, float half) {
        float[] body = depth(s, half, 0, 2);
        m.box(cx - 2 * P, cy - 2 * P, body[0], cx + 2 * P, cy + 2 * P, body[1], DARK, DARK);
        float[] cap = depth(s, half, 2, 3);
        glow.box(cx - 1.25F * P, cy - 1.25F * P, cap[0], cx + 1.25F * P, cy + 1.25F * P, cap[1], lens, lens);
    }

    @Override
    public AABB getRenderBoundingBox(BlastDoorBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        return new AABB(pos.getX() - 4.5, pos.getY(), pos.getZ() - 4.5,
                pos.getX() + 5.5, pos.getY() + 3.25, pos.getZ() + 5.5);
    }
}
