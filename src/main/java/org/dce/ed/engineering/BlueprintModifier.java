package org.dce.ed.engineering;

/**
 * Stat change from an engineering blueprint grade or experimental effect.
 */
public final class BlueprintModifier {
    private final String property;
    private final String effect;
    private final boolean good;

    public BlueprintModifier(String property, String effect, boolean good) {
        this.property = property != null ? property : "";
        this.effect = effect != null ? effect : "";
        this.good = good;
    }

    public String getProperty() {
        return property;
    }

    public String getEffect() {
        return effect;
    }

    public boolean isGood() {
        return good;
    }

    public String summary() {
        return property + " " + effect;
    }
}
