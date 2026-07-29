package org.dce.ed.exobiology;




public final class RegionResolver {

    // Constants from RegionMap.py
    private static final double X0 = -49985;
    private static final double Z0 = -24105;
    private static final int GRID_SIZE = 83;
    private static final int SCALE = 4096;

    // From RegionMapData.py
    // regionmap[z] = List of (runLength, regionId)
    private static final String[] REGIONS = RegionMapData.REGIONS;

    private RegionResolver() {}

    /**
     * @return regionId, or -1 if outside region map
     */
    public static int findRegionId(double x, double z) {

        int px = (int) ((x - X0) * GRID_SIZE / SCALE);
        int pz = (int) ((z - Z0) * GRID_SIZE / SCALE);

//        if (px < 0 || pz < 0 || pz >= REGION_MAP.length) {
//            return -1;
//        }

        int[][] row = RegionMapData.getRegionMap()[pz];

        int rx = 0;

        for (int[] entry : row) {
            int runLength = entry[0];
            int regionId = entry[1];

            if (px < rx + runLength) {
                return regionId;
            }
            rx += runLength;
        }

        return -1;
    }

    /**
     * @return region name or null if outside map
     */
    public static String findRegionName(double x, double z) {
        int id = findRegionId(x, z);
        return id > 0 ? REGIONS[id] : null;
    }
}
