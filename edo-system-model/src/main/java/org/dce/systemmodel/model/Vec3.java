package org.dce.systemmodel.model;

public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 plus(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    public double[] asArray() {
        return new double[] {x, y, z};
    }
}
