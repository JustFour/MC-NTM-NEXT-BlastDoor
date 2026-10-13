package com.blastdoor.client;

import com.blastdoor.BlastDoorMod;
import com.blastdoor.block.SideDoorBlock;
import com.blastdoor.blockentity.SideDoorBlockEntity;
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
 * Рисует распашную гермодверь: створку на вертикальных петлях и гидроцилиндр, который её двигает.
 *
 * <p>Системы координат (после поворота по {@code facing}: +Z — вдоль пути, куда смотрел игрок,
 * +X — поперёк, влево от игрока):</p>
 * <ul>
 *   <li><b>контроллер</b> — начало в центре низа блока-контроллера; проём занимает x ∈ [-1.5; 1.5],
 *       z ∈ [-0.5; 0.5];</li>
 *   <li><b>петли</b> — начало на оси петель: ближний к игроку угол проёма со стороны кармана;</li>
 *   <li><b>створка</b> — система петель, повёрнутая на угол хода. Координаты в ней заданы как (u, y, w):
 *       u — вдоль створки от петель к свободной кромке (0..3), w — в толщину от лицевой стороны A (0)
 *       к оборотной стороне B (1). Сторона A смотрит на игрока у закрытой двери и на путь — у открытой.</li>
 * </ul>
 *
 * <p>Плита занимает w от 2 до 10 px. Перед ней 2 px рельефа стороны A, за ней 6 px стороны B: там
 * балки и пролёт, в котором лежит гидроцилиндр. Цилиндр закреплён на стене кармана и толкает створку
 * за проушину у свободной кромки: дверь закрыта — шток выдвинут, открыта — убран.
 * За габарит в один блок выходят только ригели запертой двери.</p>
 *
 * <p>Текстура {@code textures/entity/side_blast_door.png} 512x512, 64 пикселя на блок;
 * координаты ниже — в сетке 256x256 (32 единицы на блок), UV нормированы: сторона A — (0,0)-(96,92), сторона B — (0,96)-(96,188), торец свободной
 * кромки — (96,0)-(112,92), полки стоек — (112,0)-(118,96), полки поясов — (0,192) и (0,200),
 * образцы материалов 32x32 — от (128,0).</p>
 */
public class SideDoorRenderer implements BlockEntityRenderer<SideDoorBlockEntity, SideDoorRenderState> {
    public static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BlastDoorMod.MODID, "textures/entity/side_blast_door.png");

    private static final float TEX = 256.0F;
    /** Единиц сетки {@link #TEX} на блок: области текстуры для деталей рамы берутся по их длине. */
    private static final float TEXELS = 32.0F;

    // Области текстуры в текселях: u0, v0, u1, v1
    private static final float[] FACE_A = {0, 0, 96, 92};
    private static final float[] FACE_B = {0, 96, 96, 188};
    private static final float[] EDGE = {96, 0, 112, 92};
    private static final float[] PAINT = {128, 0, 160, 32};
    private static final float[] FRAME = {160, 0, 192, 32};
    private static final float[] STEEL = {192, 0, 224, 32};
    private static final float[] DARK = {224, 0, 256, 32};
    private static final float[] LAMP_OFF = {128, 32, 160, 64};
    private static final float[] LAMP_ON = {160, 32, 192, 64};
    private static final float[] CHROME = {192, 32, 224, 64};
    private static final float[] BARREL = {224, 32, 256, 64};

    private static final float P = 1.0F / 16.0F; // один пиксель в блоках

    private static final float LENGTH = 3.0F;
    private static final float BOTTOM = 2 * P;   // створка висит над рельсом
    private static final float TOP = 3.0F;
    /** Глубина рельефа стороны A: плита начинается с этой отметки. */
    private static final float FRONT = 2 * P;
    /** Глубина, на которой плита кончается; дальше до полного блока — балки стороны B. */
    private static final float PLATE = 10 * P;
    private static final float POST = 3 * P;
    private static final float RAIL = 2 * P;
    /** Нижние кромки поясов (сторона A) и балок (сторона B), в пикселях от земли; общие для обеих сторон. */
    private static final float[] BELTS = {11.0F, 19.5F, 29.0F, 37.5F};
    private static final float BELT_HEIGHT = 1.5F * P;

    // ------------------------------------------------------------ гидропривод (в пролёте между 2-й и 3-й балкой)
    private static final float DRIVE_Y = 25 * P;
    /** Глубина оси цилиндра на стороне B и такое же расстояние его опоры от обеих стен угла кармана. */
    private static final float DRIVE_DEPTH = 13 * P;
    /** Проушина штока на створке — расстояние вдоль створки от петель. */
    private static final float DRIVE_MOUNT = 2.65F;
    private static final float BARREL_LENGTH = 1.68F;

    /** Рельс под дверью: в обычной сборке пусто, в сборке под Traincraft — его путь. */
    private final RailTrackVisual track;

    public SideDoorRenderer(BlockEntityRendererProvider.Context context) {
        this.track = new RailTrackVisual(context);
    }

    @Override
    public SideDoorRenderState createRenderState() {
        SideDoorRenderState state = new SideDoorRenderState();
        state.track = track.createState();
        return state;
    }

    @Override
    public void extractRenderState(SideDoorBlockEntity be, SideDoorRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, cameraPos, crumblingOverlay);
        track.extract(be, state.track, partialTick, cameraPos, crumblingOverlay);
        state.facing = be.getBlockState().getValue(SideDoorBlock.FACING);
        state.mirrored = be.getBlockState().getValue(SideDoorBlock.MIRRORED);
        state.progress = be.getRenderProgress(partialTick);
        boolean moving = state.progress > 0.001F && state.progress < 0.999F;
        state.lampLit = moving && be.getLevel() != null && (be.getLevel().getGameTime() / 5) % 2 == 0;
    }

    @Override
    public void submit(SideDoorRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        final int side = state.mirrored ? -1 : 1;                  // с какой стороны карман
        track.submit(state.track, poseStack, collector, camera);
        final float p = state.progress;
        final float eased = p * p * (3.0F - 2.0F * p);            // плавный разгон и торможение
        final float angle = 90.0F * eased;                        // 0 — в проёме, 90 — в кармане
        final float lock = 1.0F - Mth.clamp(p * 8.0F, 0.0F, 1.0F); // запоры отходят в начале хода
        final int light = state.lightCoords;
        final boolean lampLit = state.lampLit;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(TEXTURE), (pose, vc) -> {
            BoxMesh controller = new BoxMesh(pose, vc, light, TEX);
            BoxMesh hinge = controller.translated(1.5F * side, 0.0F, -0.5F);
            drawLeaf(new Leaf(hinge.rotatedY(side * angle), side), lock, lampLit);
            drawDrive(hinge, side, angle);
        });
        poseStack.popPose();
    }

    // ---------------------------------------------------------------- створка

    /**
     * Створка в своих координатах (u, y, w).
     *
     * @param lock 1 — дверь заперта (ригели выдвинуты, тяги опущены), 0 — запоры отведены
     */
    private static void drawLeaf(Leaf l, float lock, boolean lampLit) {
        final float inner = LENGTH - 2 * POST;                    // длина поясов между стойками

        // Плита: обе стороны из одного листа, торец свободной кромки с гнёздами ригелей.
        l.box(0.0F, BOTTOM, FRONT, LENGTH, TOP, PLATE, FACE_A, FACE_B, PAINT, EDGE, PAINT, PAINT);

        drawFront(l, inner, lock);
        drawBack(l, inner);

        // Сигнальные лампы на обеих сторонах: мигают, пока створка движется.
        // Горящая лампа не зависит от освещения вокруг.
        Leaf glow = lampLit ? new Leaf(l.m().withLight(LightCoordsUtil.FULL_BRIGHT), l.side()) : l;
        float[] lamp = lampLit ? LAMP_ON : LAMP_OFF;
        l.box(2.4F, 40 * P, 0.0F, 2.7F, 41 * P, FRONT, DARK);
        glow.box(2.45F, 41 * P, 0.25F * P, 2.65F, 44 * P, 1.75F * P, lamp);
        l.box(2.4F, 39 * P, PLATE, 2.7F, 40 * P, 15.75F * P, DARK);
        glow.box(2.45F, 40 * P, 13.75F * P, 2.65F, 43 * P, 15.75F * P, lamp);

        // Ригели на свободной кромке: у запертой двери входят в косяк.
        final float out = lock * 3 * P;
        if (out > 0.001F) {
            for (float by : new float[]{0.6F, 1.5F, 2.4F}) {
                l.box(LENGTH, by - 1.5F * P, 4.5F * P, LENGTH + out, by + 1.5F * P, 7.5F * P, STEEL);
            }
        }

        // Опорные катки под створкой, в стороне от рельса.
        for (float u : new float[]{0.6F, 2.4F}) {
            l.box(u - 2 * P, 0.5F * P, 4 * P, u + 2 * P, BOTTOM, 8 * P, DARK);
        }
    }

    /** Сторона A: рама с петлями, пояса на тех же высотах, что балки стороны B, и привод запоров. */
    private static void drawFront(Leaf l, float inner, float lock) {
        // Стойка у свободной кромки, верхний и нижний пояса рамы
        l.frame(LENGTH - POST, BOTTOM, 0.0F, LENGTH, TOP, FRONT, postFlange(TOP - BOTTOM), true);
        l.frame(POST, TOP - RAIL, 0.0F, LENGTH - POST, TOP, FRONT, railFlange(inner), true);
        l.frame(POST, BOTTOM, 0.0F, LENGTH - POST, BOTTOM + RAIL, FRONT, railFlange(inner), true);

        // У петель вместо сплошной стойки — три петли, между ними отрезки стойки
        float y = BOTTOM;
        final int[] hingeColumn = {6, 6, 10, 6, 10, 6, 2};        // высоты в пикселях: стойка и петли через одну
        for (int i = 0; i < hingeColumn.length; i++) {
            float next = y + hingeColumn[i] * P;
            if (i % 2 == 0) {
                l.frame(0.0F, y, 0.0F, POST, next, FRONT, postFlange(next - y), true);
            } else {
                l.box(0.0F, y, 0.0F, POST, next, FRONT, STEEL);
                l.box(POST, y + 1.25F * P, 0.5F * P, POST + 3 * P, next - 1.75F * P, FRONT, STEEL); // крыло петли
            }
            y = next;
        }

        // Пояса с головками болтов
        for (float belt : BELTS) {
            l.frame(POST, belt * P, P, LENGTH - POST, belt * P + BELT_HEIGHT, FRONT, beltFlange(inner), true);
            for (float u = 0.75F; u < 2.6F; u += 0.5F) {
                l.box(u - 0.4F * P, (belt + 0.35F) * P, 0.5F * P, u + 0.4F * P, (belt + 1.15F) * P, P, STEEL);
            }
        }

        // Привод запоров в нижнем пролёте: горизонтальная тяга с редуктором и две вертикальные тяги.
        // Отпирая дверь, горизонтальная тяга сдвигается к петлям, вертикальные поднимаются.
        final float slide = lock * 2 * P;
        l.box(0.5F + slide, 7 * P, 0.5F * P, 2.4F + slide, 8.25F * P, 1.75F * P, CHROME);
        for (float u : new float[]{0.8F, 1.85F}) {
            l.box(u - P, 6 * P, 0.0F, u + P, 9.25F * P, FRONT, DARK);
        }
        l.box(2.45F, 5.25F * P, 0.0F, 2.78F, 10 * P, FRONT, DARK);
        l.box(2.5F, 6.25F * P, 0.25F * P, 2.73F, 9 * P, 0.5F * P, STEEL);                            // крышка редуктора

        final float lift = (1.0F - lock) * 2 * P;
        for (float u : new float[]{1.0F, 2.0F}) {
            l.box(u - 0.5F * P, BOTTOM + RAIL + lift, 0.4F * P, u + 0.5F * P, 27 * P + lift, 0.9F * P, STEEL);
            l.box(u - P, 15 * P, 0.0F, u + P, 16.5F * P, FRONT, DARK);
            l.box(u - P, 23 * P, 0.0F, u + P, 24.5F * P, FRONT, DARK);
        }

        // Поручень у свободной кромки
        l.box(2.55F, 31.5F * P, 0.25F * P, 2.62F, 36.5F * P, 0.85F * P, CHROME);
        l.box(2.55F, 31.5F * P, 0.85F * P, 2.62F, 32.25F * P, FRONT, DARK);
        l.box(2.55F, 35.75F * P, 0.85F * P, 2.62F, 36.5F * P, FRONT, DARK);
    }

    /** Сторона B: рама и глубокие балки; в среднем пролёте — место под гидроцилиндр и его проушина. */
    private static void drawBack(Leaf l, float inner) {
        final float beamFront = 15 * P;
        final float bayBottom = BELTS[1] * P + BELT_HEIGHT;       // пролёт привода: между 2-й и 3-й балкой
        final float bayTop = BELTS[2] * P;

        // Стойки (у петель — с вырезом под цилиндр), верхний и нижний пояса рамы
        l.frame(LENGTH - POST, BOTTOM, PLATE, LENGTH, TOP, 1.0F, postFlange(TOP - BOTTOM), false);
        l.frame(0.0F, BOTTOM, PLATE, POST, bayBottom, 1.0F, postFlange(bayBottom - BOTTOM), false);
        l.frame(0.0F, bayTop, PLATE, POST, TOP, 1.0F, postFlange(TOP - bayTop), false);
        l.frame(POST, TOP - RAIL, PLATE, LENGTH - POST, TOP, 1.0F, railFlange(inner), false);
        l.frame(POST, BOTTOM, PLATE, LENGTH - POST, BOTTOM + RAIL, 1.0F, railFlange(inner), false);

        // Балки с головками болтов
        for (float belt : BELTS) {
            l.frame(POST, belt * P, PLATE, LENGTH - POST, belt * P + BELT_HEIGHT, beamFront, beltFlange(inner), false);
            for (float u = 0.75F; u < 2.6F; u += 0.5F) {
                l.box(u - 0.4F * P, (belt + 0.35F) * P, beamFront, u + 0.4F * P, (belt + 1.15F) * P, beamFront + 0.5F * P, STEEL);
            }
        }

        // Короткие стойки в пролётах (пролёт привода свободен)
        final float[][] dividers = {
                {1.0F, BOTTOM + RAIL, BELTS[0] * P}, {2.0F, BOTTOM + RAIL, BELTS[0] * P},
                {1.5F, BELTS[0] * P + BELT_HEIGHT, BELTS[1] * P},
                {1.5F, BELTS[3] * P + BELT_HEIGHT, TOP - RAIL}};
        for (float[] d : dividers) {
            l.box(d[0] - 0.5F * P, d[1], PLATE, d[0] + 0.5F * P, d[2], 14 * P, FRAME);
        }

        // Проушина штока у свободной кромки: плита, две щеки и палец
        l.box(DRIVE_MOUNT - 2.25F * P, 22.5F * P, PLATE, DRIVE_MOUNT + 2.25F * P, 27.5F * P, 11.25F * P, DARK);
        l.box(DRIVE_MOUNT - 1.5F * P, 27 * P, 11.25F * P, DRIVE_MOUNT + 1.5F * P, 28 * P, 15 * P, STEEL);
        l.box(DRIVE_MOUNT - 1.5F * P, 22 * P, 11.25F * P, DRIVE_MOUNT + 1.5F * P, 23 * P, 15 * P, STEEL);
        l.box(DRIVE_MOUNT - 0.5F * P, 23 * P, 12.5F * P, DRIVE_MOUNT + 0.5F * P, 27 * P, 13.5F * P, STEEL);

        // Распределительная коробка и труба с проводкой к лампе
        l.box(0.4F, 32 * P, PLATE, 0.62F, 36 * P, 12.5F * P, STEEL);
        l.box(0.44F, 32.75F * P, 12.5F * P, 0.58F, 35.25F * P, 12.9F * P, DARK);
        l.box(0.62F, 34 * P, PLATE, 2.55F, 34.5F * P, 10.6F * P, DARK);
        l.box(2.53F, 34.5F * P, PLATE, 2.57F, 39 * P, 10.6F * P, DARK);
    }

    // ---------------------------------------------------------------- гидропривод

    /**
     * Гидроцилиндр двери. Опора стоит неподвижно в углу кармана у стены, шток держит створку за проушину.
     * Закрывая дверь, цилиндр поворачивается на опоре и выдвигает шток; у открытой двери он целиком
     * лежит в среднем пролёте стороны B.
     *
     * @param m     система координат петель (не повёрнутая)
     * @param angle угол хода створки в градусах: 0 — в проёме, 90 — в кармане
     */
    private static void drawDrive(BoxMesh m, int side, float angle) {
        final float baseX = side * DRIVE_DEPTH;
        final float baseZ = DRIVE_DEPTH;

        // Опора на стене кармана: плита, две щеки и ось
        final float wall = side * 1.0F;
        box(m, wall - side * 0.75F * P, 21.25F * P, baseZ - 3 * P, wall, 28.75F * P, baseZ + 3 * P, DARK);
        box(m, baseX - side * 1.75F * P, 28 * P, baseZ - 1.75F * P, wall - side * 0.75F * P, 28.75F * P, baseZ + 1.75F * P, STEEL);
        box(m, baseX - side * 1.75F * P, 21.25F * P, baseZ - 1.75F * P, wall - side * 0.75F * P, 22 * P, baseZ + 1.75F * P, STEEL);
        box(m, baseX - 0.5F * P, 22 * P, baseZ - 0.5F * P, baseX + 0.5F * P, 28 * P, baseZ + 0.5F * P, STEEL);

        // Где сейчас проушина на створке (в системе петель)
        final double a = Math.toRadians(angle);
        final double mountX = side * (-DRIVE_MOUNT * Math.cos(a) + DRIVE_DEPTH * Math.sin(a));
        final double mountZ = DRIVE_MOUNT * Math.sin(a) + DRIVE_DEPTH * Math.cos(a);
        final double dx = mountX - baseX;
        final double dz = mountZ - baseZ;
        final float length = (float) Math.sqrt(dx * dx + dz * dz);

        // Ось цилиндра — вдоль +Z от опоры к проушине.
        BoxMesh c = m.translated(baseX, DRIVE_Y, baseZ).rotatedY((float) Math.toDegrees(Math.atan2(dx, dz)));
        c.box(-2.25F * P, -2.25F * P, -1.5F * P, 2.25F * P, 2.25F * P, BARREL_LENGTH, BARREL, DARK);
        c.box(-2.75F * P, -2.75F * P, -P, 2.75F * P, 2.75F * P, P, DARK, DARK);
        c.box(-2.75F * P, -2.75F * P, BARREL_LENGTH - 2 * P, 2.75F * P, 2.75F * P, BARREL_LENGTH + 0.25F * P, DARK, DARK);
        c.box(-0.4F * P, 2.25F * P, 2 * P, 0.4F * P, 2.7F * P, BARREL_LENGTH - 2 * P, STEEL, STEEL);   // гидролиния
        final float rodStart = BARREL_LENGTH + 0.25F * P;
        final float rodEnd = length - 1.75F * P;
        if (rodEnd > rodStart) {
            c.box(-P, -P, rodStart, P, P, rodEnd, CHROME, CHROME);
        }
        c.box(-1.75F * P, -1.75F * P, length - 1.75F * P, 1.75F * P, 1.75F * P, length + 1.5F * P, STEEL, STEEL);
    }

    /** Коробка между двумя точками без требования x0 &lt; x1: у зеркальной двери опора стоит с другой стороны. */
    private static void box(BoxMesh m, float xa, float y0, float z0, float xb, float y1, float z1, float[] all) {
        m.box(Math.min(xa, xb), y0, z0, Math.max(xa, xb), y1, z1, all, all);
    }

    // ---------------------------------------------------------------- области текстуры по длине детали

    /** Наружная полка вертикальной стойки (3 px шириной) с рядом заклёпок. */
    private static float[] postFlange(float length) {
        return new float[]{112, 0, 118, length * TEXELS};
    }

    /** Наружная полка пояса рамы (2 px высотой) с рядом заклёпок. */
    private static float[] railFlange(float length) {
        return new float[]{0, 192, length * TEXELS, 196};
    }

    /** Наружная полка пояса или балки (1.5 px высотой) с рядом заклёпок. */
    private static float[] beltFlange(float length) {
        return new float[]{0, 200, length * TEXELS, 203};
    }

    /**
     * Построитель коробок в координатах створки (u, y, w). Прячет зеркальность: у двери с карманом
     * слева створка тянется от петель в сторону -X, у зеркальной — в сторону +X.
     */
    private record Leaf(BoxMesh m, int side) {
        void box(float u0, float y0, float w0, float u1, float y1, float w1, float[] all) {
            box(u0, y0, w0, u1, y1, w1, all, all, all, all, all, all);
        }

        /**
         * Деталь рамы: краска рамы на всех гранях, кроме наружной — на ней полка с заклёпками.
         *
         * @param sideA наружная грань смотрит на сторону A (иначе — на сторону B)
         */
        void frame(float u0, float y0, float w0, float u1, float y1, float w1, float[] outward, boolean sideA) {
            box(u0, y0, w0, u1, y1, w1, sideA ? outward : FRAME, sideA ? FRAME : outward, FRAME, FRAME, FRAME, FRAME);
        }

        /**
         * @param hinge область текстуры для грани, обращённой к петлям
         * @param free  область для грани, обращённой к свободной кромке
         */
        void box(float u0, float y0, float w0, float u1, float y1, float w1,
                 float[] faceA, float[] faceB, float[] hinge, float[] free, float[] bottom, float[] top) {
            if (side > 0) {
                m.box(-u1, y0, w0, -u0, y1, w1, faceA, faceB, free, hinge, bottom, top);
            } else {
                m.box(u0, y0, w0, u1, y1, w1, faceA, faceB, hinge, free, bottom, top);
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox(SideDoorBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        return new AABB(pos.getX() - 3.5, pos.getY(), pos.getZ() - 3.5,
                pos.getX() + 4.5, pos.getY() + 3.25, pos.getZ() + 4.5);
    }
}
