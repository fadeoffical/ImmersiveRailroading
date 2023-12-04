package cam72cam.immersiverailroading.util;

import cam72cam.mod.entity.boundingbox.IBoundingBox;
import cam72cam.mod.math.Vec3d;
import org.apache.commons.lang3.tuple.Pair;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

/*
 * For now this just wraps the AABB constructor
 *
 *  In the future we can override the intersects functions for better bounding boxes
 */
public class RealBB implements IBoundingBox {
    private final Vec3d min;
    private final Vec3d max;
    private final double front;
    private final double rear;
    private final double width;
    private final double height;
    private final float yaw;
    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final float[][] heightMap;

    public RealBB(double front, double rear, double width, double height, float yaw) {
        this(front, rear, width, height, yaw, null);
    }

    public RealBB(double front, double rear, double width, double height, float yaw, float[][] heightMap) {
        this(front, rear, width, height, yaw, 0, 0, 0, heightMap);
    }

    private RealBB(double front, double rear, double width, double height, float yaw, double centerX, double centerY, double centerZ, float[][] heightMap) {
        this.front = front;
        this.rear = rear;
        this.width = width;
        this.height = height;
        this.yaw = yaw;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.heightMap = heightMap;

        Vec3d frontPos = VecUtil.fromWrongYaw(front, yaw);
        Vec3d rearPos = VecUtil.fromWrongYaw(rear, yaw);

        // width
        Vec3d offsetRight = VecUtil.fromWrongYaw(width / 2, yaw + 90);
        Vec3d offsetLeft = VecUtil.fromWrongYaw(width / 2, yaw - 90);

        double[] x = new double[]{
                frontPos.x + offsetRight.x,
                rearPos.x + offsetRight.x,
                frontPos.x + offsetLeft.x,
                rearPos.x + offsetLeft.x,
        };
        double[] z = new double[]{
                frontPos.z + offsetRight.z,
                rearPos.z + offsetRight.z,
                frontPos.z + offsetLeft.z,
                rearPos.z + offsetLeft.z,
        };

        double xMin = x[0];
        double xMax = x[0];
        double zMin = z[0];
        double zMax = z[0];

        for (int i = 1; i < x.length; i++) {
            xMin = Math.min(xMin, x[i]);
            xMax = Math.max(xMax, x[i]);
            zMin = Math.min(zMin, z[i]);
            zMax = Math.max(zMax, z[i]);
        }

        this.min = new Vec3d(xMin + centerX, centerY, zMin + centerZ);
        this.max = new Vec3d(xMax + centerX, centerY + height, zMax + centerZ);
    }

    @Override
    public Vec3d min() {
        return this.min;
    }

    @Override
    public Vec3d max() {
        return this.max;
    }

    @Override
    public RealBB expand(Vec3d val) {
        double front = this.front;
        double rear = this.rear;
        double width = this.width;
        double height = this.height;
        double centerY = this.centerY;
        if (val.x > 0) {
            front += val.x;
        } else {
            rear += val.x;
        }

        if (val.y > 0) {
            height += val.y;
        } else {
            centerY += val.y;
        }

        width += val.z;

        return new RealBB(front, rear, width, height, this.yaw, this.centerX, centerY, this.centerZ, this.heightMap);
    }

    @Override
    public RealBB contract(Vec3d val) {
        double front = this.front;
        double rear = this.rear;
        double width = this.width;
        double height = this.height;
        double centerY = this.centerY;
        if (val.x > 0) {
            front -= val.x;
        } else {
            rear -= val.x;
        }

        if (val.y > 0) {
            height -= val.y;
        } else {
            centerY -= val.y;
        }

        width -= val.z;

        return new RealBB(front, rear, width, height, this.yaw, this.centerX, centerY, this.centerZ, this.heightMap);
    }

    @Override
    public RealBB grow(Vec3d val) {
        return new RealBB(
                this.front + val.x, this.rear + val.x,
                this.width + val.z + val.z, this.height + val.y,
                this.yaw,
                this.centerX, this.centerY + val.y, this.centerZ,
                this.heightMap);
    }

    @Override
    public RealBB offset(Vec3d val) {
        return new RealBB(this.front, this.rear, this.width, this.height, this.yaw, this.centerX + val.x, this.centerY + val.y, this.centerZ + val.z, this.heightMap);
    }

    @Override
    public double calculateXOffset(IBoundingBox other, double offsetX) {
        return 0;
    }

    @Override
    public double calculateYOffset(IBoundingBox other, double offsetY) {
        double hack = 0.05;
        Double intersect = this.intersectsAt(other.min().subtract(hack, 0, hack), other.max()
                .add(hack, 0, hack), true).getRight();
        double minY = other.min().y;
        return Math.max(-0.1, Math.min(0.1, intersect - minY));
    }

    @Override
    public double calculateZOffset(IBoundingBox other, double offsetZ) {
        return 0;
    }

    @Override
    public boolean intersects(Vec3d min, Vec3d max) {
        return this.intersectsAt(min, max, true).getLeft();
    }

    @Override
    public boolean contains(Vec3d vec) {
        return this.intersectsAt(vec, vec, false).getLeft();
    }

    public Pair<Boolean, Double> intersectsAt(Vec3d min, Vec3d max, boolean useHeightmap) {
        if (!(this.min.x < max.x && this.max.x > min.x && this.min.y < max.y && this.max.y > min.y && this.min.z < max.z && this.max.z > min.z)) {
            return Pair.of(false, min.y);
        }

        double actualYMin = this.centerY;
        double actualYMax = this.centerY + this.height;
        if (!(actualYMin < max.y && actualYMax > min.y)) {
            return Pair.of(false, min.y);
        }

        Rectangle2D otherRect = new Rectangle2D.Double(min.x, min.z, 0, 0);
        if (min.x == max.x && min.z == max.z) {
            otherRect.add(max.x + 0.2, max.z + 0.2);
        } else {
            otherRect.add(max.x, max.z);
        }

        Rectangle2D myRect = new Rectangle2D.Double(
                this.rear + this.centerX,
                -this.width / 2 + this.centerZ,
                this.front - this.rear,
                this.width);

        AffineTransform otherTransform = new AffineTransform();
        otherTransform.rotate(Math.toRadians(180 - this.yaw + 90), this.centerX, this.centerZ);
        Shape otherShape = otherTransform.createTransformedShape(otherRect);

        if (!otherShape.intersects(myRect)) {
            return Pair.of(false, min.y);
        }
        if (this.heightMap != null && useHeightmap) {
            int xRes = this.heightMap.length - 1;
            int zRes = this.heightMap[0].length - 1;

            double length = this.front - this.rear;

            actualYMin = this.centerY;
            actualYMax = this.centerY;

            Rectangle2D bds = otherShape.getBounds2D();


            double px = bds.getMinX() - (this.centerX - length / 2);
            double pz = bds.getMinY() - (this.centerZ - this.width / 2);
            double Px = bds.getMaxX() - (this.centerX - length / 2);
            double Pz = bds.getMaxY() - (this.centerZ - this.width / 2);

            double cx = Math.max(0, Math.min(length, px));
            double cz = Math.max(0, Math.min(this.width, pz));
            double Cx = Math.max(0, Math.min(length, Px));
            double Cz = Math.max(0, Math.min(this.width, Pz));

            cx = (cx / length * xRes);
            cz = (cz / this.width * zRes);
            Cx = (Cx / length * xRes);
            Cz = (Cz / this.width * zRes);

            for (int x = (int) cx; x < (int) Cx; x++) {
                for (int z = (int) cz; z < (int) Cz; z++) {
                    actualYMax = Math.max(actualYMax, this.centerY + this.height * this.heightMap[x][z]);
                }
            }

            return Pair.of(actualYMin < max.y && actualYMax > min.y, actualYMax);
        }

        return Pair.of(true, this.max.y);
    }

    @Override
    public RealBB clone() {
        return new RealBB(this.front, this.rear, this.width, this.height, this.yaw, this.centerX, this.centerY, this.centerZ, this.heightMap);
    }

    public RealBB withHeightMap(float[][] heightMap) {
        return new RealBB(this.front, this.rear, this.width, this.height, this.yaw, this.centerX, this.centerY, this.centerZ, heightMap);
    }
}
