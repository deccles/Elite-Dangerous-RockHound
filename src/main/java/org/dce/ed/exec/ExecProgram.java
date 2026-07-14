package org.dce.ed.exec;

/** Named executable / JAR registered for Exec bindings. */
public final class ExecProgram {

    private String name = "";
    private String path = "";

    public ExecProgram() {
    }

    public ExecProgram(String name, String path) {
        setName(name);
        setPath(path);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : "";
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path != null ? path.trim() : "";
    }

    public ExecProgram copy() {
        return new ExecProgram(name, path);
    }
}
