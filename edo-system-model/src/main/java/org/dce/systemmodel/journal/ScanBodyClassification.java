package org.dce.systemmodel.journal;

/**
 * Shared {@link ScanRecord} body classification for model build and orbit-parent rules.
 */
public final class ScanBodyClassification {

    private ScanBodyClassification() {
    }

    public static boolean isRing(ScanRecord scan) {
        if (scan == null) {
            return false;
        }
        if ("Ring".equalsIgnoreCase(scan.bodyType())) {
            return true;
        }
        return isRingLikeNameOrClass(scan.bodyName()) || isRingLikeNameOrClass(scan.subType());
    }

    private static boolean isRingLikeNameOrClass(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String n = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (n.contains("planetaryring") || n.contains("planetary ring")) {
            return true;
        }
        if (n.contains("belt cluster") || n.contains("belt ")) {
            return true;
        }
        return n.contains(" ring") || n.endsWith("ring");
    }
}
