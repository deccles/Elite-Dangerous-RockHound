package org.dce.ed;

import java.util.List;

final class ExecBindingOrder {

    private ExecBindingOrder() {
    }

    static <T> int move(List<T> rows, int sourceIndex, int insertionIndex) {
        if (rows == null || sourceIndex < 0 || sourceIndex >= rows.size()
                || insertionIndex < 0 || insertionIndex > rows.size()) {
            return sourceIndex;
        }
        int destinationIndex = insertionIndex > sourceIndex ? insertionIndex - 1 : insertionIndex;
        if (destinationIndex == sourceIndex) {
            return sourceIndex;
        }
        T row = rows.remove(sourceIndex);
        rows.add(destinationIndex, row);
        return destinationIndex;
    }
}
