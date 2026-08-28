package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ExecBindingOrderTest {

    @Test
    void movesRowUpToInsertionPoint() {
        List<String> rows = new ArrayList<>(List.of("A", "B", "C", "D"));

        int selectedRow = ExecBindingOrder.move(rows, 3, 1);

        assertEquals(List.of("A", "D", "B", "C"), rows);
        assertEquals(1, selectedRow);
    }

    @Test
    void movesRowDownToInsertionPoint() {
        List<String> rows = new ArrayList<>(List.of("A", "B", "C", "D"));

        int selectedRow = ExecBindingOrder.move(rows, 1, 4);

        assertEquals(List.of("A", "C", "D", "B"), rows);
        assertEquals(3, selectedRow);
    }

    @Test
    void droppingAtExistingBoundaryDoesNotMoveRow() {
        List<String> rows = new ArrayList<>(List.of("A", "B", "C"));

        int selectedRow = ExecBindingOrder.move(rows, 1, 2);

        assertEquals(List.of("A", "B", "C"), rows);
        assertEquals(1, selectedRow);
    }
}
