package org.dce.ed.logreader.event;

/**
 * One material stack from a journal Materials / inventory event.
 */
public final class MaterialStack {
    private final String name;
    private final String nameLocalised;
    private final int count;

    public MaterialStack(String name, String nameLocalised, int count) {
        this.name = name != null ? name : "";
        this.nameLocalised = nameLocalised != null ? nameLocalised : "";
        this.count = Math.max(0, count);
    }

    public String getName() {
        return name;
    }

    public String getNameLocalised() {
        return nameLocalised;
    }

    public int getCount() {
        return count;
    }
}
