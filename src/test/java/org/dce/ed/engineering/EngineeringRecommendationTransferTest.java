package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineeringRecommendationTransferTest {

    @TempDir
    Path tempDir;

    @Test
    void readsCopiedJsonText() throws Exception {
        assertEquals("{\"data\":{}}",
                EngineeringRecommendationTransfer.read(new StringSelection("{\"data\":{}}")));
    }

    @Test
    void readsDroppedJsonFile() throws Exception {
        Path file = tempDir.resolve("recommendation.slef.json");
        Files.writeString(file, "{\"header\":{}}", StandardCharsets.UTF_8);
        Transferable transferable = new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[] { DataFlavor.javaFileListFlavor };
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.javaFileListFlavor.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) {
                return List.of(file.toFile());
            }
        };

        assertEquals("{\"header\":{}}", EngineeringRecommendationTransfer.read(transferable));
    }
}
