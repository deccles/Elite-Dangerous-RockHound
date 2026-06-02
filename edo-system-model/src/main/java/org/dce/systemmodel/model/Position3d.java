package org.dce.systemmodel.model;

/** Metres in system frame (XYZ). Immutable. */
public record Position3d(double x, double y, double z) {

    public static final Position3d ZERO = new Position3d(0, 0, 0);

    public static Position3d from(Vec3 v) {
        return new Position3d(v.x(), v.y(), v.z());
    }

    public Vec3 toVec3() {
        return new Vec3(x, y, z);
    }

    public double[] asArray() {
        return new double[] {x, y, z};
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double distanceTo(Position3d other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
