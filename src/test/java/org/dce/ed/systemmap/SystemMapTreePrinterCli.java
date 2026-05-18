package org.dce.ed.systemmap;

/**
 * CLI hook: {@code java -cp ... org.dce.ed.systemmap.SystemMapTreePrinterCli "System Name"}
 */
public final class SystemMapTreePrinterCli {

    private SystemMapTreePrinterCli() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: SystemMapTreePrinterCli <systemName>");
            System.exit(1);
        }
        SystemMapTreePrinter.printTree(args[0]);
    }
}
