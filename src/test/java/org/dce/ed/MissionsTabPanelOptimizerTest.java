package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;

import org.dce.ed.mission.TransportLocation;
import org.dce.ed.mission.TransportPlanAction;
import org.dce.ed.mission.TransportPlanStop;
import org.dce.ed.mission.TransportPlanProblem;
import org.dce.ed.mission.TransportRoutePlan;
import org.dce.ed.logreader.event.LocationEvent;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class MissionsTabPanelOptimizerTest {
    @Test
    void transportTabProvidesOptimizeStopsButton() {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, systems -> { });

        assertNotNull(findButton(panel, "Optimize Stops"));
    }

    @Test
    void transportNavigationStartsWithAllAndDisabledOptimizedPlanTabs() {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, systems -> { });

        assertNotNull(findButton(panel, "Transport Missions"));
        JButton optimizedPlan = findButton(panel, "Optimized Plan");
        assertNotNull(optimizedPlan);
        assertFalse(optimizedPlan.isEnabled());
        assertNull(findButton(panel, "Cargo"));
        assertNull(findButton(panel, "Courier"));
        assertNull(findButton(panel, "Passenger"));
    }

    @Test
    void completedPlanEnablesAndOpensPlanTabThenInvalidationReturnsToAll() {
        List<List<String>> applied = new ArrayList<>();
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, applied::add);
        TransportLocation lave = new TransportLocation("Lave", "Lave Station", 10, 0, 0);
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(lave, List.of(new TransportPlanAction(
                        TransportPlanAction.Kind.PICK_UP, 1L, "Gold", 8)), 8)), 10.0, true);

        invoke(panel, "displayOptimizedPlan", new Class<?>[] { TransportRoutePlan.class }, plan);

        JButton optimizedPlan = findButton(panel, "Optimized Plan");
        assertTrue(optimizedPlan.isEnabled());
        assertNotNull(findButton(panel, "Optimize Plan"));
        assertTrue(findNamed(panel, "optimizedPlanContent").isVisible());
        JButton apply = findButton(panel, "Apply to Route");
        assertNotNull(apply);
        apply.doClick();
        assertEquals(List.of(List.of("Sol", "Lave")), applied);

        invoke(panel, "invalidateOptimizedPlan", new Class<?>[0]);

        assertFalse(optimizedPlan.isEnabled());
        assertFalse(findNamed(panel, "optimizedPlanContent").isVisible());
        assertTrue(findNamed(panel, "allMissionsContent").isVisible());
    }

    @Test
    void changingCurrentLocationKeepsTheActivePlan() {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, systems -> { });
        TransportLocation lave = new TransportLocation("Lave", "Lave Station", 10, 0, 0);
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(lave, List.of(new TransportPlanAction(
                        TransportPlanAction.Kind.VISIT, 1L, "Courier", 0)), 0)), 10.0, true);
        invoke(panel, "displayOptimizedPlan", new Class<?>[] { TransportRoutePlan.class }, plan);

        panel.handleLogEvent(new LocationEvent(Instant.now(), new JsonObject(),
                false, false, false, "Achenar", 2L, new double[] { 20, 0, 0 }, null, 0, null));

        assertTrue(findButton(panel, "Optimized Plan").isEnabled());
        assertTrue(findNamed(panel, "optimizedPlanContent").isVisible());
    }

    @Test
    void optimizedPlanDisplaysMissingPickupWarningsInsideTheTab() {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, systems -> { });
        TransportLocation achenar = new TransportLocation("Achenar", "Dawes Hub", 20, 0, 0);
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(achenar, List.of(new TransportPlanAction(
                        TransportPlanAction.Kind.VISIT, 5L, "Mining", 0)), 0)), 20.0, true);
        List<TransportPlanProblem> warnings = List.of(new TransportPlanProblem(
                TransportPlanProblem.Code.SOURCE_REQUIRED, 5L,
                "Pickup not planned: 12 t Bromellite source has not been set."));

        invoke(panel, "displayOptimizedPlan",
                new Class<?>[] { TransportRoutePlan.class, List.class }, plan, warnings);

        assertNotNull(findLabelContaining(panel, "Pickup not planned"));
        assertNotNull(findLabelContaining(panel, "Bromellite"));
    }

    private static JButton findButton(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return button;
            if (child instanceof Container container) {
                JButton found = findButton(container, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JComponent findNamed(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (child instanceof JComponent component && name.equals(component.getName())) return component;
            if (child instanceof Container container) {
                JComponent found = findNamed(container, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JLabel findLabelContaining(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label && label.getText() != null
                    && label.getText().contains(text)) return label;
            if (child instanceof Container container) {
                JLabel found = findLabelContaining(container, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            method.invoke(target, args);
        } catch (ReflectiveOperationException ex) {
            fail("Expected UI behavior method " + name, ex);
        }
    }
}
