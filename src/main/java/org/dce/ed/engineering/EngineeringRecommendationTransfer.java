package org.dce.ed.engineering;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Reads recommendation JSON from clipboard text or a dropped file. */
public final class EngineeringRecommendationTransfer {

    private EngineeringRecommendationTransfer() {
    }

    public static boolean supports(Transferable transferable) {
        return transferable != null
                && (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                        || transferable.isDataFlavorSupported(DataFlavor.stringFlavor));
    }

    public static String read(Transferable transferable) throws Exception {
        if (transferable == null) {
            throw new IllegalArgumentException("No recommendation content was supplied.");
        }
        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            @SuppressWarnings("unchecked")
            List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
            if (files == null || files.size() != 1 || files.get(0) == null) {
                throw new IllegalArgumentException("Drop exactly one SLEF JSON file.");
            }
            File file = files.get(0);
            String name = file.getName().toLowerCase();
            if (!name.endsWith(".json")) {
                throw new IllegalArgumentException("Engineering recommendations must be JSON files.");
            }
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        }
        if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            Object value = transferable.getTransferData(DataFlavor.stringFlavor);
            return value != null ? value.toString().trim() : "";
        }
        throw new IllegalArgumentException("Paste SLEF JSON or drop a JSON file.");
    }
}
