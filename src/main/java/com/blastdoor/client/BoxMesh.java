package com.blastdoor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

/**
 * Мини-построитель параллелепипедов с текстурой и нормалями — из них собраны створки всех дверей.
 * Области текстуры задаются в пикселях: u0, v0, u1, v1.
 *
 * @param texSize сторона квадратной текстуры в пикселях
 */
record BoxMesh(PoseStack.Pose pose, VertexConsumer vc, int light, float texSize) {

    // Подвижные и наклонные детали рисуются тем же построителем в сдвинутой или повёрнутой системе координат.

    BoxMesh translated(float x, float y, float z) {
        PoseStack.Pose moved = pose.copy();
        moved.translate(x, y, z);
        return new BoxMesh(moved, vc, light, texSize);
    }

    /** Поворот вокруг оси X: положительный угол наклоняет +Y в сторону +Z. */
    BoxMesh rotatedX(float degrees) {
        return rotated(Axis.XP.rotationDegrees(degrees));
    }

    /** Поворот вокруг вертикальной оси Y: положительный угол поворачивает -X в сторону +Z. */
    BoxMesh rotatedY(float degrees) {
        return rotated(Axis.YP.rotationDegrees(degrees));
    }

    /** Поворот вокруг оси Z: положительный угол наклоняет +X в сторону +Y. */
    BoxMesh rotatedZ(float degrees) {
        return rotated(Axis.ZP.rotationDegrees(degrees));
    }

    private BoxMesh rotated(Quaternionfc rotation) {
        PoseStack.Pose turned = pose.copy();
        turned.rotate(rotation);
        return new BoxMesh(turned, vc, light, texSize);
    }

    /** Та же система координат, но с другой освещённостью (например, полной — для горящих ламп). */
    BoxMesh withLight(int newLight) {
        return new BoxMesh(pose, vc, newLight, texSize);
    }

    /**
     * @param side  область текстуры для боковых граней (±X, ±Y)
     * @param front область для граней ±Z (лицевые стороны); null — как у боковых
     */
    void box(float x0, float y0, float z0, float x1, float y1, float z1, float[] side, float @Nullable [] front) {
        box(x0, y0, z0, x1, y1, z1, side, front, front);
    }

    /**
     * @param side область текстуры для боковых граней (±X, ±Y)
     * @param negZ область для грани -Z; null — как у боковых
     * @param posZ область для грани +Z; null — как у боковых
     */
    void box(float x0, float y0, float z0, float x1, float y1, float z1,
             float[] side, float @Nullable [] negZ, float @Nullable [] posZ) {
        box(x0, y0, z0, x1, y1, z1, negZ != null ? negZ : side, posZ != null ? posZ : side, side, side, side, side);
    }

    /** Коробка со своей областью текстуры на каждой грани. */
    void box(float x0, float y0, float z0, float x1, float y1, float z1,
             float[] negZ, float[] posZ, float[] negX, float[] posX, float[] negY, float[] posY) {
        quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, negZ, 0, 0, -1);
        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, posZ, 0, 0, 1);
        quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, negX, -1, 0, 0);
        quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, posX, 1, 0, 0);
        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, negY, 0, -1, 0);
        quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, posY, 0, 1, 0);
    }

    /** Вершины: низ-лево, низ-право, верх-право, верх-лево (если смотреть на грань снаружи). */
    private void quad(float ax, float ay, float az, float bx, float by, float bz,
                      float cx, float cy, float cz, float dx, float dy, float dz,
                      float[] uv, float nx, float ny, float nz) {
        float u0 = uv[0] / texSize, v0 = uv[1] / texSize, u1 = uv[2] / texSize, v1 = uv[3] / texSize;
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
