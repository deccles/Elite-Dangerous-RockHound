package org.dce.ed.edsm;

public class SystemResponse {
    public long id;
    /** Elite system address when EDSM returns {@code showId=1}. */
    public Long id64;
    public String name;
    public Coordinates coords;
    public String permit;
    public Information information;
    public PrimaryStar primaryStar;

    public static class Coordinates {
        public double x;
        public double y;
        public double z;
    }

    public static class Information {
        public String allegiance;
        public String government;
        public String economy;
        public String security;
        public long population;
        public String faction;
    }

    public static class PrimaryStar {
        public String name;
        public String type;
        public Boolean isScoopable;
    }
}
