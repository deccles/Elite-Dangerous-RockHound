package org.dce.ed.ui.tabdock;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Objects;

/** DnD payload for moving an overlay tab between docks. */
public final class OverlayTabTransferable implements Transferable {

    public static final DataFlavor TAB_FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + OverlayTabTransferData.class.getName(),
            "EDO Overlay Tab");

    private final OverlayTabTransferData data;

    public OverlayTabTransferable(OverlayTabTransferData data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] { TAB_FLAVOR };
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return TAB_FLAVOR.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        return data;
    }

    public record OverlayTabTransferData(String cardName, String sourceDockId) {
        public OverlayTabTransferData {
            Objects.requireNonNull(cardName, "cardName");
            Objects.requireNonNull(sourceDockId, "sourceDockId");
        }
    }
}
