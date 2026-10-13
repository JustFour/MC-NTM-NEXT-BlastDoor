package com.blastdoor.client;

import com.blastdoor.BlastDoorMod;
import com.blastdoor.block.StationGateBlock;
import com.blastdoor.blockentity.StationGateBlockEntity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Рисует станционный гермозатвор: плиту с силовым набором, гидроцилиндры, складную площадку
 * с перилами и приямок с сигнальными лампами.
 *
 * <p>Системы координат (все после поворота по {@code facing}: +Z — сквозь проём, куда смотрел игрок,
 * +X — поперёк, влево от игрока):</p>
 * <ul>
 *   <li><b>приямок</b> — начало в центре низа блока-контроллера, то есть на дне приямка;
 *       приямок занимает x ∈ [-1.5; 2.5], z ∈ [-0.5; 3.5], уровень пола — y = 1;</li>
 *   <li><b>петля</b> — начало на оси петли: уровень пола, ближний к игроку край приямка;</li>
 *   <li><b>плита</b> — система петли, повёрнутая на угол хода: +Y вдоль плиты от петли к свободной
 *       кромке, +Z — в глубину плиты от настила к силовому набору. Стоящая плита совпадает с системой петли.</li>
 * </ul>
 *
 * <p>Всё, что есть на плите, умещается в её толщину, а площадка и перила при опускании складываются
 * вплотную к ней — поэтому лежащая плита оказывается вровень с полом.</p>
 *
 * <p>Текстура {@code textures/entity/station_gate.png} 512x512, 64 пикселя на блок;
 * координаты ниже — в сетке 256x256 (32 единицы на блок), UV нормированы: настил (верх лежащей
 * плиты) — (0,0)-(128,128), изнанка — (0,128)-(128,256), образцы материалов 32x32 — от (128,0),
 * предупреждающая полоса — (128,64)-(256,80), полки балок с заклёпками — (128,84) и (128,96),
 * оборудование приямка — от (144,96).</p>
 */
public class StationGateRenderer implements BlockEntityRenderer<StationGateBlockEntity, StationGateRenderState> {
    public static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BlastDoorMod.MODID, "textures/entity/station_gate.png");

    private static final float TEX = 256.0F;
    /** Единиц сетки {@link #TEX} на блок: области текстуры для балок и решётки берутся по их размеру. */
    private static final float TEXELS = 32.0F;

    // Области текстуры в пикселях: u0, v0, u1, v1
    private static final float[] DECK = {0, 0, 128, 128};
    private static final float[] UNDERSIDE = {0, 128, 128, 256};
    private static final float[] PAINT = {128, 0, 160, 32};
    private static final float[] STEEL = {160, 0, 192, 32};
    private static final float[] PIT = {192, 0, 224, 32};
    private static final float[] CHROME = {224, 0, 256, 32};
    private static final float[] GRATE = {128, 32, 160, 64};
    private static final float[] DARK = {160, 32, 192, 64};
    private static final float[] LAMP_OFF = {192, 32, 224, 64};
    private static final float[] LAMP_ON = {224, 32, 256, 64};
    private static final float[] HAZARD = {128, 64, 256, 80};
    // Оборудование приямка
    private static final float[] PIPE = {144, 96, 176, 128};      // труба, блик вдоль V
    private static final float[] PIPE_T = {176, 96, 208, 128};    // труба, блик вдоль U
    private static final float[] TANK = {208, 96, 240, 128};
    private static final float[] CABINET = {144, 128, 176, 160};
    private static final float[] RED = {176, 128, 208, 160};
    private static final float[] GAUGE = {208, 128, 240, 160};

    private static final float P = 1.0F / 16.0F; // один пиксель в блоках

    // ------------------------------------------------------------ размеры плиты (в её системе координат)
    private static final float X0 = -1.5F;
    private static final float X1 = 2.5F;
    private static final float LENGTH = 4.0F;
    /** Толщина дна приямка. Плита тоньше блока ровно на неё, чтобы лежать вровень с полом. */
    private static final float PIT_FLOOR = 2 * P;
    private static final float THICKNESS = 1.0F - PIT_FLOOR;
    private static final float DECK_DEPTH = 3 * P;
    private static final float BEAM = 3 * P;
    /** Отсюда боковые балки рамы уходят в скосы к верхней балке. */
    private static final float SHOULDER = 3.0F;

    /** Главные балки: между ними — средняя часть набора, снаружи — каналы гидроцилиндров. */
    private static final float GIRDER_LEFT = -0.5F;
    private static final float GIRDER_RIGHT = 1.5F;
    private static final float MID_X0 = GIRDER_LEFT + BEAM / 2;
    private static final float MID_X1 = GIRDER_RIGHT - BEAM / 2;
    private static final float RIB_X = 0.5F;
    /** Глубина рёбер средней части: всё, что глубже, свободно для сложенной площадки. */
    private static final float RIB_DEPTH = 11 * P;

    // ------------------------------------------------------------ гидроцилиндры
    private static final float[] CYLINDER_X = {
            (X0 + BEAM + GIRDER_LEFT - BEAM / 2) / 2,
            (GIRDER_RIGHT + BEAM / 2 + X1 - BEAM) / 2};
    /** Глубина оси цилиндра в канале плиты и такая же глубина его опоры под полом. */
    private static final float CYLINDER_DEPTH = 8.5F * P;
    /** Опора цилиндра в приямке — расстояние от оси петли. */
    private static final float CYLINDER_BASE_Z = 0.9F;
    /** Точка крепления штока — расстояние вдоль плиты от петли. */
    private static final float CYLINDER_MOUNT = 2.8F;
    private static final float BARREL_LENGTH = 1.7F;

    // ------------------------------------------------------------ складная площадка
    private static final float PLATFORM_X0 = MID_X0 + 0.5F * P;
    private static final float PLATFORM_X1 = MID_X1 - 0.5F * P;
    private static final float PLATFORM_ALONG = 2.5F;
    private static final float PLATFORM_DEPTH = 12 * P;
    private static final float PLATFORM_WIDTH = 8 * P;
    private static final float RAILING_HEIGHT = 9 * P;

    public StationGateRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public StationGateRenderState createRenderState() {
        return new StationGateRenderState();
    }

    @Override
    public void extractRenderState(StationGateBlockEntity be, StationGateRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, cameraPos, crumblingOverlay);
        state.facing = be.getBlockState().getValue(StationGateBlock.FACING);
        state.progress = be.getRenderProgress(partialTick);
        state.lampsLit = false;
        if (be.getLevel() != null) {
            // Контроллер сидит в полу, где темно: свет берём из проёма над ним.
            state.lightCoords = LightCoordsUtil.getLightCoords(be.getLevel(), be.getBlockPos().above());
            boolean moving = state.progress > 0.001F && state.progress < 0.999F;
            state.lampsLit = moving && (be.getLevel().getGameTime() / 5) % 2 == 0;
        }
    }

    @Override
    public void submit(StationGateRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        final float p = state.progress;
        final float eased = p * p * (3.0F - 2.0F * p);            // плавный разгон и торможение
        final float angle = 90.0F * eased;                        // 0 — стоит, 90 — лежит
        final int light = state.lightCoords;
        final boolean lampsLit = state.lampsLit;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(TEXTURE), (pose, vc) -> {
            BoxMesh pit = new BoxMesh(pose, vc, light, TEX);
            BoxMesh hinge = pit.translated(0.0F, 1.0F, -0.5F);
            BoxMesh leaf = hinge.rotatedX(angle);

            drawPit(pit, lampsLit);
            drawCylinders(hinge, angle);
            drawLeaf(leaf);
            drawPlatform(leaf, angle);
        });
        poseStack.popPose();
    }

    // ---------------------------------------------------------------- приямок

    /** Неподвижная часть: решётчатое дно, опоры гидроцилиндров и сигнальные лампы. */
    private static void drawPit(BoxMesh m, boolean lampsLit) {
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                m.box(X0 + i, 0.0F, -0.5F + j, X0 + i + 1, PIT_FLOOR, 0.5F + j, PIT, PIT);
            }
        }

        // Опоры цилиндров: тумба и две щеки, между которыми качается цилиндр.
        final float baseZ = CYLINDER_BASE_Z - 0.5F;               // в системе приямка
        final float pivotY = 1.0F - CYLINDER_DEPTH;
        for (float cx : CYLINDER_X) {
            m.box(cx - 3 * P, PIT_FLOOR, baseZ - 3 * P, cx + 3 * P, 4.5F * P, baseZ + 3 * P, DARK, DARK);
            m.box(cx - 4 * P, 4.5F * P, baseZ - 1.5F * P, cx - 3.2F * P, pivotY + 1.5F * P, baseZ + 1.5F * P, STEEL, STEEL);
            m.box(cx + 3.2F * P, 4.5F * P, baseZ - 1.5F * P, cx + 4 * P, pivotY + 1.5F * P, baseZ + 1.5F * P, STEEL, STEEL);
        }

        // Сигнальные лампы: мигают, пока плита движется. Горящая лампа не зависит от освещения вокруг.
        BoxMesh glow = lampsLit ? m.withLight(LightCoordsUtil.FULL_BRIGHT) : m;
        float[] lamp = lampsLit ? LAMP_ON : LAMP_OFF;
        for (float lx : new float[]{0.0F, 1.0F}) {
            m.box(lx - 2 * P, PIT_FLOOR, 1.0F - 2 * P, lx + 2 * P, 2.5F * P, 1.0F + 2 * P, DARK, DARK);
            glow.box(lx - 1.5F * P, 2.5F * P, 1.0F - 1.5F * P, lx + 1.5F * P, 5 * P, 1.0F + 1.5F * P, lamp, lamp);
        }

        drawPitEquipment(m);
    }

    /**
     * Оборудование приямка: опоры петель, гидравлические трубы с вентилями, насосная станция и шкаф
     * управления. Всё стоит там, где у лежащей плиты пустоты: в каналах цилиндров, под рёбрами средней
     * части (не выше 5 px от дна) и в углах за скосами рамы.
     */
    private static void drawPitEquipment(BoxMesh m) {
        final float pipeTop = 3.75F * P;

        for (int i = 0; i < CYLINDER_X.length; i++) {
            final float cx = CYLINDER_X[i];
            final float inward = i == 0 ? 1.0F : -1.0F;           // в сторону середины затвора

            // Опора петли у ближнего края
            m.box(cx - 3 * P, PIT_FLOOR, -0.48F, cx + 3 * P, 7 * P, -0.2F, DARK, DARK);
            m.box(cx - 1.5F * P, 7 * P, -0.42F, cx + 1.5F * P, 8 * P, -0.26F, STEEL, STEEL);

            // Напорная труба под цилиндром, с фланцами
            pipeAlongZ(m, cx - 0.75F * P, PIT_FLOOR, 0.65F, cx + 0.75F * P, 3.5F * P, 2.7F);
            for (float z : new float[]{0.7F, 1.5F, 2.2F, 2.7F}) {
                m.box(cx - P, PIT_FLOOR, z - 0.5F * P, cx + P, pipeTop, z + 0.5F * P, STEEL, STEEL);
            }
            // Тонкая сливная линия вдоль главной балки
            final float drain = cx + inward * 4.375F * P;
            m.box(drain - 0.4F * P, PIT_FLOOR, 0.0F, drain + 0.4F * P, 2.8F * P, 2.7F, STEEL, DARK);
        }

        // Средняя часть: две магистрали вдоль приямка и коллектор с вентилями между ними
        final float headerZ = 1.75F;
        final float[] mains = {-0.27F, 1.27F};
        for (float x : mains) {
            pipeAlongZ(m, x - 0.875F * P, PIT_FLOOR, -0.05F, x + 0.875F * P, pipeTop, headerZ);
            m.box(x - 1.1F * P, PIT_FLOOR, -0.05F - 0.5F * P, x + 1.1F * P, 4 * P, -0.05F + 0.5F * P, STEEL, STEEL);
            m.box(x - 1.1F * P, PIT_FLOOR, headerZ - 1.1F * P, x + 1.1F * P, 4 * P, headerZ + 1.1F * P, STEEL, STEEL);
        }
        pipeAlongX(m, mains[0], PIT_FLOOR, headerZ - 0.875F * P, mains[1], pipeTop, headerZ + 0.875F * P);
        for (float x : new float[]{0.25F, 0.75F}) {
            m.box(x - P, PIT_FLOOR, headerZ - P, x + P, 4.25F * P, headerZ + P, STEEL, STEEL);
            m.box(x - 1.5F * P, 4.25F * P, headerZ - 1.5F * P, x + 1.5F * P, 4.75F * P, headerZ + 1.5F * P, RED, RED);
        }

        // Дальние углы за скосами рамы: насосная станция с манометром слева, шкаф управления справа
        m.box(-1.47F, PIT_FLOOR, 3.1F, -1.2F, 10 * P, 3.47F, TANK, TANK);
        m.box(-1.44F, 10 * P, 3.14F, -1.23F, 12.5F * P, 3.43F, STEEL, DARK);
        m.box(-1.4F, 5.5F * P, 3.08F, -1.27F, 7.5F * P, 3.1F, DARK, GAUGE);
        m.box(2.2F, PIT_FLOOR, 3.1F, 2.47F, 12.5F * P, 3.47F, STEEL, CABINET);
    }

    /** Труба вдоль Z: блик идёт по её длине на всех видимых гранях, торцы тёмные. */
    private static void pipeAlongZ(BoxMesh m, float x0, float y0, float z0, float x1, float y1, float z1) {
        m.box(x0, y0, z0, x1, y1, z1, DARK, DARK, PIPE_T, PIPE_T, PIPE, PIPE);
    }

    /** Труба вдоль X. */
    private static void pipeAlongX(BoxMesh m, float x0, float y0, float z0, float x1, float y1, float z1) {
        m.box(x0, y0, z0, x1, y1, z1, PIPE_T, PIPE_T, DARK, DARK, PIPE_T, PIPE_T);
    }

    // ---------------------------------------------------------------- гидроцилиндры

    /**
     * Цилиндр упирается в опору в приямке и держит плиту за точку в канале набора: при подъёме он
     * поворачивается на опоре и выдвигает шток, а у лежащей плиты прячется в канал целиком.
     *
     * @param m     система координат петли
     * @param angle угол хода плиты в градусах: 0 — стоит, 90 — лежит
     */
    private static void drawCylinders(BoxMesh m, float angle) {
        final double a = Math.toRadians(angle);
        final float baseY = -CYLINDER_DEPTH;
        // Куда сейчас смотрит точка крепления на плите.
        final double mountY = CYLINDER_MOUNT * Math.cos(a) - CYLINDER_DEPTH * Math.sin(a);
        final double mountZ = CYLINDER_MOUNT * Math.sin(a) + CYLINDER_DEPTH * Math.cos(a);
        final double dy = mountY - baseY;
        final double dz = mountZ - CYLINDER_BASE_Z;
        final float length = (float) Math.sqrt(dy * dy + dz * dz);
        final float tilt = (float) Math.toDegrees(Math.atan2(dz, dy));

        for (float cx : CYLINDER_X) {
            // Ось цилиндра — вдоль +Y от опоры к плите.
            BoxMesh c = m.translated(cx, baseY, CYLINDER_BASE_Z).rotatedX(tilt);
            c.box(-2.5F * P, 0.0F, -2.5F * P, 2.5F * P, BARREL_LENGTH, 2.5F * P, PAINT, PAINT);
            c.box(-3 * P, -0.5F * P, -3 * P, 3 * P, 2 * P, 3 * P, DARK, DARK);
            c.box(-3 * P, BARREL_LENGTH - 2 * P, -3 * P, 3 * P, BARREL_LENGTH + 0.5F * P, 3 * P, DARK, DARK);
            c.box(-1.25F * P, BARREL_LENGTH + 0.5F * P, -1.25F * P, 1.25F * P, length - 2 * P, 1.25F * P, CHROME, CHROME);
            c.box(-2 * P, length - 2 * P, -2 * P, 2 * P, length + P, 2 * P, STEEL, STEEL);
        }
    }

    // ---------------------------------------------------------------- плита

    /**
     * Плита в своей системе координат: настил (будущий пол) смотрит на -Z, силовой набор — на +Z.
     * Рама повторяет фото: боковые балки, скосы к укороченной верхней балке, две главные балки.
     */
    private static void drawLeaf(BoxMesh m) {
        final float top = LENGTH - BEAM;

        // Настил во всю плиту: он и пол, и уплотнение проёма.
        m.box(X0, 0.0F, 0.0F, X1, LENGTH, DECK_DEPTH, STEEL, DECK, UNDERSIDE);

        // Рама: боковые балки, главные балки, нижняя балка у петли и верхняя с предупреждающей полосой.
        m.box(X0, 0.0F, DECK_DEPTH, X0 + BEAM, SHOULDER, THICKNESS, PAINT, null, verticalFlange(SHOULDER));
        m.box(X1 - BEAM, 0.0F, DECK_DEPTH, X1, SHOULDER, THICKNESS, PAINT, null, verticalFlange(SHOULDER));
        for (float gx : new float[]{GIRDER_LEFT, GIRDER_RIGHT}) {
            m.box(gx - BEAM / 2, 0.0F, DECK_DEPTH, gx + BEAM / 2, top, THICKNESS, PAINT, null, verticalFlange(top));
        }
        m.box(MID_X0, 0.0F, DECK_DEPTH, MID_X1, BEAM, THICKNESS, PAINT, null, horizontalFlange(MID_X1 - MID_X0));
        m.box(GIRDER_LEFT - BEAM / 2, top, DECK_DEPTH, GIRDER_RIGHT + BEAM / 2, LENGTH, THICKNESS, HAZARD, HAZARD);

        // Скосы от боковых балок к верхней: чуть ниже рамы, чтобы грани в стыках не совпадали.
        final float chamferDepth = THICKNESS - 0.5F * P;
        diagonal(m, X0 + BEAM / 2, SHOULDER, GIRDER_LEFT - BEAM / 2, LENGTH - BEAM / 2,
                BEAM / 2, DECK_DEPTH, chamferDepth, true);
        diagonal(m, X1 - BEAM / 2, SHOULDER, GIRDER_RIGHT + BEAM / 2, LENGTH - BEAM / 2,
                BEAM / 2, DECK_DEPTH, chamferDepth, true);

        // Средняя часть: продольное ребро, два пояса, раскосы и проушины.
        m.box(RIB_X - P, BEAM, DECK_DEPTH, RIB_X + P, top, RIB_DEPTH, PAINT, null);
        for (float y : new float[]{1.0F, 2.0F}) {
            m.box(MID_X0, y - P, DECK_DEPTH, MID_X1, y + P, RIB_DEPTH - P, PAINT, null);
        }
        final float braceDepth = 8 * P;
        for (float[] bay : new float[][]{{MID_X0, RIB_X - P}, {RIB_X + P, MID_X1}}) {
            float left = bay[0], right = bay[1], centre = (left + right) / 2;
            // V-образные раскосы в верхнем пролёте и по одному раскосу в нижнем
            diagonal(m, centre, 2.0F + 2 * P, left + P, top - P, 0.75F * P, DECK_DEPTH, braceDepth, false);
            diagonal(m, centre, 2.0F + 2 * P, right - P, top - P, 0.75F * P, DECK_DEPTH, braceDepth, false);
            diagonal(m, left + P, BEAM + 2 * P, right - P, 1.0F - 2 * P, 0.75F * P, DECK_DEPTH, braceDepth, false);
            // Подъёмная проушина под верхней балкой и проушина петли у нижней
            m.box(centre - P, top - 4 * P, DECK_DEPTH, centre + P, top, RIB_DEPTH, STEEL, STEEL);
            m.box(centre - 2 * P, BEAM, DECK_DEPTH, centre + 2 * P, BEAM + 3 * P, THICKNESS - P, STEEL, STEEL);
        }

        // Каналы гидроцилиндров: только низкие рёбра жёсткости и кронштейн штока.
        final float[][] channels = {{X0 + BEAM, GIRDER_LEFT - BEAM / 2}, {GIRDER_RIGHT + BEAM / 2, X1 - BEAM}};
        for (int i = 0; i < channels.length; i++) {
            for (float y : new float[]{1.0F, 2.0F}) {
                m.box(channels[i][0], y - P, DECK_DEPTH, channels[i][1], y + P, 5 * P, PAINT, null);
            }
            float cx = CYLINDER_X[i];
            m.box(cx - 2 * P, CYLINDER_MOUNT - 1.5F * P, DECK_DEPTH, cx + 2 * P, CYLINDER_MOUNT + 1.5F * P, 6 * P, STEEL, STEEL);
        }
    }

    /**
     * Наклонная балка между двумя точками в плоскости плиты.
     *
     * @param riveted рисовать на наружной полке ряд заклёпок
     */
    private static void diagonal(BoxMesh m, float ax, float ay, float bx, float by,
                                 float halfWidth, float z0, float z1, boolean riveted) {
        float dx = bx - ax, dy = by - ay;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        BoxMesh d = m.translated(ax, ay, 0.0F).rotatedZ((float) Math.toDegrees(Math.atan2(dy, dx)));
        d.box(0.0F, -halfWidth, z0, length, halfWidth, z1, PAINT, null, riveted ? horizontalFlange(length) : null);
    }

    /** Наружная полка вертикальной балки с рядом заклёпок, по длине балки. */
    private static float[] verticalFlange(float length) {
        return new float[]{128, 96, 134, 96 + length * TEXELS};
    }

    /** Наружная полка горизонтальной балки с рядом заклёпок, по длине балки. */
    private static float[] horizontalFlange(float length) {
        return new float[]{128, 84, 128 + length * TEXELS, 90};
    }

    // ---------------------------------------------------------------- складная площадка

    /**
     * Площадка с перилами между главными балками. Настил площадки всегда горизонтален, перила всегда
     * параллельны плите: у стоящей плиты это балкон с ограждением, у лежащей — оба листа сложены
     * вплотную к набору.
     *
     * @param m     система координат плиты
     * @param angle угол хода плиты в градусах: 0 — стоит, 90 — лежит
     */
    private static void drawPlatform(BoxMesh m, float angle) {
        // Настил: его ось — на плите, сам он остаётся горизонтальным.
        BoxMesh platform = m.translated(0.0F, PLATFORM_ALONG, PLATFORM_DEPTH).rotatedX(-angle);
        final float segment = (PLATFORM_X1 - PLATFORM_X0) / 4;
        // Кусок решётки по размеру секции, чтобы ячейки были в масштабе остальных текстур.
        final float[] grate = {GRATE[0], GRATE[1], GRATE[0] + segment * TEXELS, GRATE[1] + PLATFORM_WIDTH * TEXELS};
        for (int i = 0; i < 4; i++) {
            float x = PLATFORM_X0 + i * segment;
            platform.box(x, -P, 0.0F, x + segment, 0.0F, PLATFORM_WIDTH, grate, STEEL);
        }

        // Перила: ось — на наружной кромке настила, сами они параллельны плите.
        BoxMesh railing = platform.translated(0.0F, 0.0F, PLATFORM_WIDTH).rotatedX(angle);
        final float postStep = (PLATFORM_X1 - PLATFORM_X0 - P) / 3;
        for (int i = 0; i < 4; i++) {
            float x = PLATFORM_X0 + i * postStep;
            railing.box(x, 0.0F, 0.0F, x + P, RAILING_HEIGHT - P, P, STEEL, STEEL);
        }
        railing.box(PLATFORM_X0, RAILING_HEIGHT - P, 0.0F, PLATFORM_X1, RAILING_HEIGHT, P, STEEL, STEEL);
        railing.box(PLATFORM_X0, RAILING_HEIGHT / 2 - 0.5F * P, 0.2F * P,
                PLATFORM_X1, RAILING_HEIGHT / 2 + 0.5F * P, 0.8F * P, STEEL, STEEL);
    }

    @Override
    public AABB getRenderBoundingBox(StationGateBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        return new AABB(pos.getX() - 4.5, pos.getY(), pos.getZ() - 4.5,
                pos.getX() + 5.5, pos.getY() + 5.25, pos.getZ() + 5.5);
    }
}
