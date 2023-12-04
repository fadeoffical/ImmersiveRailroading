package cam72cam.immersiverailroading.track;

import cam72cam.immersiverailroading.library.TrackSmoothing;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.math.Vec3d;
import org.apache.commons.lang3.tuple.Pair;
import util.Matrix4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CubicCurve {
    //http://spencermortensen.com/articles/bezier-circle/
    public final static double c = 0.55191502449;
    public final Vec3d p1;
    public final Vec3d ctrl1;
    public final Vec3d ctrl2;
    public final Vec3d p2;

    public CubicCurve(Vec3d p1, Vec3d ctrl1, Vec3d ctrl2, Vec3d p2) {
        this.p1 = p1;
        this.ctrl1 = ctrl1;
        this.ctrl2 = ctrl2;
        this.p2 = p2;
    }

    public static CubicCurve circle(int radius, float degrees) {
        float cRadScale = degrees / 90;
        Vec3d p1 = new Vec3d(0, 0, radius);
        Vec3d ctrl1 = new Vec3d(cRadScale * c * radius, 0, radius);
        Vec3d ctrl2 = new Vec3d(radius, 0, cRadScale * c * radius);
        Vec3d p2 = new Vec3d(radius, 0, 0);

        Matrix4 quart = new Matrix4();
        quart.rotate(Math.toRadians(-90 + degrees), 0, 1, 0);

        return new CubicCurve(p1, ctrl1, quart.apply(ctrl2), quart.apply(p2)).apply(new Matrix4().translate(0, 0, -radius));
    }

    public CubicCurve apply(Matrix4 mat) {
        return new CubicCurve(
                mat.apply(this.p1),
                mat.apply(this.ctrl1),
                mat.apply(this.ctrl2),
                mat.apply(this.p2)
        );
    }

    public Pair<CubicCurve, CubicCurve> split(double t) {
        return Pair.of(this.truncate(t), this.reverse().truncate(1 - t));
    }

    public CubicCurve truncate(double t) {
        Vec3d midpoint = this.ctrl1.add(this.ctrl2).scale(t);
        Vec3d ctrl1 = this.p1.add(this.ctrl1).scale(t);
        Vec3d ctrl2 = this.p2.add(this.ctrl2).scale(t);

        Vec3d temp = ctrl2.add(midpoint).scale(t);
        ctrl2 = ctrl1.add(midpoint).scale(t);
        midpoint = ctrl2.add(temp).scale(t);
        return new CubicCurve(
                this.p1,
                ctrl1,
                ctrl2,
                midpoint
        );
    }

    public CubicCurve reverse() {
        return new CubicCurve(this.p2, this.ctrl2, this.ctrl1, this.p1);
    }

    public List<Vec3d> toList(double stepSize) {
        List<Vec3d> res = new ArrayList<>();
        List<Vec3d> resRev = new ArrayList<>();
        res.add(this.p1);
        if (this.p1.equals(this.p2)) {
            return res;
        }

        resRev.add(this.p2);
        double precision = 5;

        double t = 0;
        while (t <= 0.5) {
            for (double i = 1; i < precision; i++) {
                Vec3d prev = res.get(res.size() - 1);

                double delta = (Math.pow(10, -i));

                for (; t < 1 + delta; t += delta) {
                    Vec3d pos = this.position(t);
                    if (pos.distanceTo(prev) > stepSize) {
                        // We passed it, just barely
                        t -= delta;
                        break;
                    }
                }
            }
            res.add(this.position(t));
        }

        double lt = t;
        t = 1;

        while (t > lt) {
            for (double i = 1; i < precision; i++) {
                Vec3d prev = resRev.get(resRev.size() - 1);

                double delta = (Math.pow(10, -i));

                for (; t > lt - delta; t -= delta) {
                    Vec3d pos = this.position(t);
                    if (pos.distanceTo(prev) > stepSize) {
                        // We passed it, just barely
                        t += delta;
                        break;
                    }
                }
            }
            if (t > lt) {
                resRev.add(this.position(t));
            }
        }
        Collections.reverse(resRev);
        res.addAll(resRev);
        return res;
    }

    public Vec3d position(double t) {
        Vec3d pt = Vec3d.ZERO;
        pt = pt.add(this.p1.scale(1 * Math.pow(1 - t, 3) * Math.pow(t, 0)));
        pt = pt.add(this.ctrl1.scale(3 * Math.pow(1 - t, 2) * Math.pow(t, 1)));
        pt = pt.add(this.ctrl2.scale(3 * Math.pow(1 - t, 1) * Math.pow(t, 2)));
        pt = pt.add(this.p2.scale(1 * Math.pow(1 - t, 0) * Math.pow(t, 3)));
        return pt;
    }

    public float angleStop() {
        return VecUtil.toYaw(this.p2.subtract(this.ctrl2));
    }

    public float angleStart() {
        return VecUtil.toYaw(this.p1.subtract(this.ctrl1)) + 180;
    }

    public List<CubicCurve> subsplit(int maxSize) {
        List<CubicCurve> res = new ArrayList<>();
        if (this.p1.distanceTo(this.p2) <= maxSize) {
            res.add(this);
        } else {
            res.addAll(this.truncate(0.5).subsplit(maxSize));
            res.addAll(this.reverse().truncate(0.5).reverse().subsplit(maxSize));
        }
        return res;
    }


    public CubicCurve linearize(TrackSmoothing smoothing) {
        double start = this.p1.distanceTo(this.ctrl1);
        double middle = this.ctrl1.distanceTo(this.ctrl2);
        double end = this.ctrl2.distanceTo(this.p2);

        double lengthGuess = start + middle + end;
        double height = this.p2.y - this.p1.y;

        switch (smoothing) {
            case NEITHER:
                return new CubicCurve(
                        this.p1,
                        this.ctrl1.add(0, (start / lengthGuess) * height, 0),
                        this.ctrl2.add(0, -(end / lengthGuess) * height, 0),
                        this.p2
                );
            case NEAR:
                return new CubicCurve(
                        this.p1,
                        this.ctrl1,
                        this.ctrl2.add(0, -(end / (middle + end)) * height, 0),
                        this.p2
                );
            case FAR:
                return new CubicCurve(
                        this.p1,
                        this.ctrl1.add(0, (start / (start + middle)) * height, 0),
                        this.ctrl2,
                        this.p2
                );
            case BOTH:
            default:
                return this;
        }
    }
}
