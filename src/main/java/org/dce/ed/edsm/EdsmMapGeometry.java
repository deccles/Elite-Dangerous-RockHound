package org.dce.ed.edsm;

/** Validates that an EDSM response can stand on its own as an orbital map. */
public final class EdsmMapGeometry {
    private EdsmMapGeometry() {
    }

    public static boolean isComplete(BodiesResponse response) {
        if (response == null || response.bodies == null || response.bodies.size() < 2) {
            return false;
        }
        int orbitalBodies = 0;
        for (BodiesResponse.Body body : response.bodies) {
            if (body == null || body.name == null || body.name.isBlank()) {
                return false;
            }
            if (isArrivalStar(body)) {
                continue;
            }
            orbitalBodies++;
            if (!positiveFinite(body.semiMajorAxis)
                    || !finiteBetween(body.orbitalEccentricity, 0.0, 1.0)
                    || !finite(body.orbitalInclination)
                    || !finite(body.argOfPeriapsis)
                    || body.parents == null || body.parents.isEmpty()
                    || body.parents.stream().noneMatch(EdsmMapGeometry::hasParent)) {
                return false;
            }
        }
        return orbitalBodies > 0;
    }

    private static boolean isArrivalStar(BodiesResponse.Body body) {
        return "Star".equalsIgnoreCase(body.type)
                && (Boolean.TRUE.equals(body.isMainStar)
                        || (body.distanceToArrival != null && body.distanceToArrival.doubleValue() == 0.0));
    }

    private static boolean hasParent(BodiesResponse.ParentRef parent) {
        return parent != null && (parent.Star != null || parent.Planet != null || parent.Null != null);
    }

    private static boolean positiveFinite(Double value) {
        return finite(value) && value.doubleValue() > 0.0;
    }

    private static boolean finiteBetween(Double value, double minInclusive, double maxExclusive) {
        return finite(value) && value.doubleValue() >= minInclusive && value.doubleValue() < maxExclusive;
    }

    private static boolean finite(Double value) {
        return value != null && Double.isFinite(value.doubleValue());
    }
}
