package org.dce.systemmodel.journal;

public record ParentRef(ParentType type, int bodyId) {

    public enum ParentType {
        NULL, PLANET, STAR;

        public static ParentType fromJournalKey(String key) {
            if (key == null) {
                return STAR;
            }
            return switch (key.toLowerCase()) {
                case "null" -> NULL;
                case "planet" -> PLANET;
                case "star" -> STAR;
                default -> STAR;
            };
        }

        public String journalKey() {
            return switch (this) {
                case NULL -> "Null";
                case PLANET -> "Planet";
                case STAR -> "Star";
            };
        }
    }

    public String format() {
        return type.journalKey() + ":" + bodyId;
    }
}
