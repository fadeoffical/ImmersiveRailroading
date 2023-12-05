package cam72cam.immersiverailroading.util;

public class MathUtil {

    public static double gradeToDegrees(double grade) {
        return Math.toDegrees(gradeToRadians(grade));
    }

    public static double gradeToRadians(double grade) {
        return Math.atan2(grade, 100);
    }

    // Java's built-in modulus gives negative results on negative input for some reason
    // this results in screwey behavior when the coder is expecting the true math modulus,
    // so I implemented that here
    public static double trueModulus(double val, double mod) {
        double modulus = Math.abs(mod);
        double result = val % modulus;
        if (result != 0 && val < 0) {
            result += modulus;
        }
        return result;
    }

    public static double deltaAngle(double source, double target) {
        return deltaMod(source, target, 360.0d);
    }

    public static double deltaMod(double source, double target, double mod) {
        double delta = target - source;
        delta -= delta > mod / 2 ? mod : 0;
        delta += delta < -mod ? mod : 0;
        return delta;
    }

    public static int deltaMod(int source, int target, int mod) {
        int delta = target - source;
        delta -= delta > mod / 2 ? mod : 0;
        delta += delta < -mod ? mod : 0;
        return delta;
    }
}
