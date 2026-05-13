package org.dce.ed.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.dce.ed.cache.CachedBody;
import org.dce.ed.cache.CachedSystem;
import org.dce.ed.exobiology.BodyAttributes;
import org.dce.ed.exobiology.ExobiologyData;
import org.dce.ed.exobiology.ExobiologyData.BioCandidate;
import org.dce.ed.state.BodyInfo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public final class BioPredictFromCacheMain {

    private static final String CACHE_FILE_NAME = ".edOverlaySystems.json";
    private static final Preferences PREFS = Preferences.userNodeForPackage(BioPredictFromCacheMain.class);

    private static final String PREF_BODY_NAME = "bodyName";
    private static final String PREF_FILTER_ENABLED = "filterEnabled";

    // Single pocket list selection stored as pipe-delimited values:
    //   "G: <Genus>" and "S: <Genus Species>"
    private static final String PREF_SELECTED_POCKET = "selectedPocket";

    // Back-compat keys (older versions stored separate lists)
    private static final String PREF_SELECTED_GENUS = "selectedGenus";
    private static final String PREF_SELECTED_SPECIES = "selectedSpecies";

    public static void main(String[] args) throws Exception {
        showDialog();
    }

        private static void showDialog() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String lastBody = PREFS.get(PREF_BODY_NAME, "Sifi WK-C c27-5 C 5");
            boolean lastEnabled = PREFS.getBoolean(PREF_FILTER_ENABLED, false);

            Set<String> lastPocket = splitPipe(PREFS.get(PREF_SELECTED_POCKET, ""));
            if (lastPocket.isEmpty()) {
                Set<String> lastGenus = splitPipe(PREFS.get(PREF_SELECTED_GENUS, ""));
                Set<String> lastSpecies = splitPipe(PREFS.get(PREF_SELECTED_SPECIES, ""));

                lastPocket = new LinkedHashSet<>();
                for (String g : lastGenus) {
                    lastPocket.add(pocketGenus(g));
                }
                for (String s : lastSpecies) {
                    lastPocket.add(pocketSpecies(s));
                }
            }

            SpeciesIndex index = SpeciesIndex.buildBestEffort();

            JDialog dlg = new JDialog();
            dlg.setTitle("Predict Exobiology From Cache");
            dlg.setModal(true);

            JPanel root = new JPanel(new java.awt.BorderLayout(10, 10));
            root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // ---- Top controls (do not stretch the body field) ----
            JPanel top = new JPanel();
            top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

            JPanel bodyRow = new JPanel();
            bodyRow.setLayout(new BoxLayout(bodyRow, BoxLayout.X_AXIS));
            bodyRow.add(new JLabel("Body name:"));
            bodyRow.add(Box.createHorizontalStrut(8));

            JTextField bodyField = new JTextField(lastBody, 40);
            bodyField.setMaximumSize(bodyField.getPreferredSize()); // keep fixed width
            bodyRow.add(bodyField);
            bodyRow.add(Box.createHorizontalGlue()); // soak up extra horizontal space

            top.add(bodyRow);
            top.add(Box.createVerticalStrut(10));

            JCheckBox filterEnabled = new JCheckBox("Limit rules to selected Genus/Species pocket (pre-filter)");
            filterEnabled.setSelected(lastEnabled);
            top.add(filterEnabled);
            top.add(Box.createVerticalStrut(8));

            JPanel pocketPanel = new JPanel();
            pocketPanel.setLayout(new BoxLayout(pocketPanel, BoxLayout.Y_AXIS));
            pocketPanel.add(new JLabel("Genus / Species pocket"));

            JList<String> pocketList = new JList<>(index.pocketEntries.toArray(new String[0]));
            pocketList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            selectListItems(pocketList, lastPocket);

            JScrollPane pocketScroll = new JScrollPane(pocketList);
            pocketScroll.setPreferredSize(new java.awt.Dimension(700, 160));
            pocketPanel.add(pocketScroll);

            top.add(pocketPanel);
            top.add(Box.createVerticalStrut(8));

            JLabel hint = new JLabel("Pick one or more Genus and/or Species. The union becomes the rule whitelist.");
            hint.setHorizontalAlignment(SwingConstants.LEFT);
            top.add(hint);

            if (index.pocketEntries.isEmpty()) {
                JLabel warn = new JLabel("No genus/species list found (constraints not introspectable). Filter will be ignored.");
                warn.setHorizontalAlignment(SwingConstants.LEFT);
                top.add(Box.createVerticalStrut(6));
                top.add(warn);
            }

            root.add(top, java.awt.BorderLayout.NORTH);

            // ---- Results (this should grow) ----
            JTextArea resultsArea = new JTextArea(16, 80);
            resultsArea.setEditable(false);
            resultsArea.setLineWrap(false);

            JPanel resultsPanel = new JPanel(new java.awt.BorderLayout(0, 6));
            resultsPanel.add(new JLabel("Results"), java.awt.BorderLayout.NORTH);

            JScrollPane resultsScroll = new JScrollPane(resultsArea);
            resultsPanel.add(resultsScroll, java.awt.BorderLayout.CENTER);

            root.add(resultsPanel, java.awt.BorderLayout.CENTER);

            // ---- Buttons ----
            JPanel buttons = new JPanel();
            buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
            JButton ok = new JButton("Run");
            JButton cancel = new JButton("Cancel");
            buttons.add(Box.createHorizontalGlue());
            buttons.add(ok);
            buttons.add(Box.createHorizontalStrut(10));
            buttons.add(cancel);

            root.add(buttons, java.awt.BorderLayout.SOUTH);

            ok.addActionListener(evt -> {
                String body = bodyField.getText() == null ? "" : bodyField.getText().trim();
                if (body.isEmpty()) {
                    JOptionPane.showMessageDialog(dlg, "Body name is required.");
                    return;
                }

                Set<String> selPocket = new LinkedHashSet<>(pocketList.getSelectedValuesList());
                UiSelection ui = UiSelection.fromPocket(body, filterEnabled.isSelected(), selPocket);

                PREFS.put(PREF_BODY_NAME, ui.bodyName);
                PREFS.putBoolean(PREF_FILTER_ENABLED, ui.filterEnabled);
                PREFS.put(PREF_SELECTED_POCKET, joinPipe(selPocket));

                // keep old keys in sync (handy if you run older versions)
                PREFS.put(PREF_SELECTED_GENUS, joinPipe(ui.selectedGenus));
                PREFS.put(PREF_SELECTED_SPECIES, joinPipe(ui.selectedSpecies));

                ok.setEnabled(false);
                resultsArea.setText("Running...\n");

                new Thread(() -> {
                    String text;
                    try {
                        text = runPredictionToText(ui);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        text = "Prediction failed: " + ex.getMessage();
                    }

                    final String outText = text;
                    SwingUtilities.invokeLater(() -> {
                        resultsArea.setText(outText);
                        resultsArea.setCaretPosition(0);
                        ok.setEnabled(true);
                    });
                }, "BioPredictFromCacheMain-Run").start();
            });

            cancel.addActionListener(evt -> dlg.dispose());

            Runnable applyEnabled = () -> {
                boolean en = filterEnabled.isSelected();

                if (index.pocketEntries.isEmpty()) {
                    en = false;
                }

                pocketList.setEnabled(en);
            };
            filterEnabled.addActionListener(evt -> applyEnabled.run());
            applyEnabled.run();

            dlg.setContentPane(root);
            dlg.setMinimumSize(new java.awt.Dimension(780, 640));
            dlg.pack();
            dlg.setLocationRelativeTo(null);
            dlg.setVisible(true);
        });
    }


    private static String runPredictionToText(UiSelection ui) throws Exception {
        String bodyName = ui == null ? null : ui.bodyName;
        if (bodyName == null || bodyName.trim().isEmpty()) {
            return "No body name entered.";
        }

        bodyName = bodyName.trim();

        StringBuilder sb = new StringBuilder();

        Path cachePath = defaultCachePath();
        sb.append("Using cache: ").append(cachePath.toAbsolutePath()).append('\n');

        if (!Files.isRegularFile(cachePath)) {
            sb.append("Cache file not found: ").append(cachePath.toAbsolutePath()).append('\n');
            return sb.toString();
        }

        CachedBodyMatch match = findBodyInCache(cachePath, bodyName);
        if (match == null) {
            sb.append("Body not found in cache: '").append(bodyName).append("'\n");
            return sb.toString();
        }

        BodyInfo info = toBodyInfo(match.system, match.body);

        BodyAttributes attrs = info.buildBodyAttributes();
        if (attrs == null) {
            sb.append("BodyInfo.buildBodyAttributes() returned null (insufficient data).\n");
            return sb.toString();
        }

        List<BioCandidate> candidates = predictWithOptionalFilter(attrs, ui);

        sb.append("Predictions: ").append(candidates == null ? 0 : candidates.size()).append('\n');
        if (candidates == null || candidates.isEmpty()) {
            sb.append("  <none>\n");
            return sb.toString();
        }

        for (int i = 0; i < candidates.size(); i++) {
            BioCandidate c = candidates.get(i);
            sb.append(String.format(Locale.US,
                    "  %2d) %-12s %-16s  score=%.6f  baseValue=%d\n",
                    i + 1,
                    safe(c.genus),
                    safe(c.species),
                    c.getScore(),
                    c.baseValue));
        }
        
        sb.append('\n');
        sb.append("Found body:\n");
        sb.append("  System: ").append(info.getStarSystem()).append('\n');
        sb.append("  Body:   ").append(info.getBodyName()).append('\n');
        sb.append("  BodyId: ").append(info.getBodyId()).append('\n');
        sb.append("  HasBio: ").append(info.hasBio()).append('\n');
        sb.append("  Planet: ").append(nullToEmpty(info.getPlanetClass())).append('\n');
        sb.append("  Atmo:   ").append(nullToEmpty(info.getAtmosphere())).append('\n');
        sb.append("  Atmo2:  ").append(nullToEmpty(info.getAtmoOrType())).append('\n');
        sb.append("  TempK:  ").append(info.getSurfaceTempK() == null ? "" : String.format(Locale.US, "%.3f", info.getSurfaceTempK())).append('\n');
        sb.append("  Press:  ").append(info.getSurfacePressure() == null ? "" : String.format(Locale.US, "%.6f", info.getSurfacePressure())).append('\n');
        sb.append("  GravMS: ").append(info.getGravityMS() == null ? "" : String.format(Locale.US, "%.6f", info.getGravityMS())).append('\n');
        sb.append("  StarPos:").append(info.getStarPos() == null ? " <null>" :
                String.format(Locale.US, " [%.3f, %.3f, %.3f]", info.getStarPos()[0], info.getStarPos()[1], info.getStarPos()[2])).append('\n');
        sb.append('\n');

        return sb.toString();
    }

    private static List<BioCandidate> predictWithOptionalFilter(BodyAttributes attrs, UiSelection ui) {
        if (ui == null) {
            return ExobiologyData.predict(attrs);
        }

        if (!ui.filterEnabled) {
            return ExobiologyData.predict(attrs);
        }

        Set<String> allowedKeys = buildAllowedKeys(ui);
        if (allowedKeys.isEmpty()) {
            return ExobiologyData.predict(attrs);
        }

        Method m = findPredictWithWhitelistMethod();
        if (m == null) {
            throw new IllegalStateException(
                    "Filter is enabled, but ExobiologyData does not expose a whitelist predict(...) overload. " +
                    "Add predict(BodyAttributes, Set<String>) (or similar) so this tool can pre-filter rule execution.");
        }

        try {
            Object result = m.invoke(null, attrs, allowedKeys);
            @SuppressWarnings("unchecked")
            List<BioCandidate> out = (List<BioCandidate>) result;
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed invoking filtered ExobiologyData prediction method: " + m, e);
        }
    }

    private static Method findPredictWithWhitelistMethod() {
        for (Method m : ExobiologyData.class.getDeclaredMethods()) {
            if (!m.getName().equals("predict")) {
                continue;
            }

            Class<?>[] p = m.getParameterTypes();
            if (p.length != 2) {
                continue;
            }

            if (!BodyAttributes.class.isAssignableFrom(p[0])) {
                continue;
            }

            if (Set.class.isAssignableFrom(p[1])) {
                m.setAccessible(true);
                return m;
            }
        }

        for (String name : Arrays.asList("predictFiltered", "predictWithWhitelist", "predictWithFilter")) {
            for (Method m : ExobiologyData.class.getDeclaredMethods()) {
                if (!m.getName().equals(name)) {
                    continue;
                }

                Class<?>[] p = m.getParameterTypes();
                if (p.length != 2) {
                    continue;
                }

                if (!BodyAttributes.class.isAssignableFrom(p[0])) {
                    continue;
                }

                if (Set.class.isAssignableFrom(p[1])) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }

        return null;
    }

    private static Set<String> buildAllowedKeys(UiSelection ui) {
        Set<String> allowed = new LinkedHashSet<>();

        if (ui.selectedSpecies != null && !ui.selectedSpecies.isEmpty()) {
            allowed.addAll(ui.selectedSpecies);
        }

        if (ui.selectedGenus != null && !ui.selectedGenus.isEmpty()) {
            SpeciesIndex index = SpeciesIndex.buildBestEffort();
            for (String genus : ui.selectedGenus) {
                Set<String> species = index.genusToSpecies.get(genus);
                if (species != null && !species.isEmpty()) {
                    allowed.addAll(species);
                }
            }
        }

        return allowed;
    }

    private static String pocketGenus(String genus) {
        if (genus == null) {
            return "G: ";
        }
        return "G: " + genus.trim();
    }

    private static String pocketSpecies(String genusSpeciesKey) {
        if (genusSpeciesKey == null) {
            return "S: ";
        }
        return "S: " + genusSpeciesKey.trim();
    }

    private static void selectListItems(JList<String> list, Set<String> selected) {
        if (list == null || selected == null || selected.isEmpty()) {
            return;
        }

        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < list.getModel().getSize(); i++) {
            String v = list.getModel().getElementAt(i);
            if (selected.contains(v)) {
                idx.add(i);
            }
        }

        if (idx.isEmpty()) {
            return;
        }

        int[] out = new int[idx.size()];
        for (int i = 0; i < idx.size(); i++) {
            out[i] = idx.get(i);
        }

        list.setSelectedIndices(out);
        list.ensureIndexIsVisible(out[0]);
    }

    private static String joinPipe(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join("|", values);
    }

    private static Set<String> splitPipe(String s) {
        if (s == null || s.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String part : s.split("\\|")) {
            String t = part == null ? "" : part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static final class UiSelection {
        private final String bodyName;
        private final boolean filterEnabled;
        private final Set<String> selectedGenus;
        private final Set<String> selectedSpecies;

        private UiSelection(String bodyName, boolean filterEnabled, Set<String> selectedGenus, Set<String> selectedSpecies) {
            this.bodyName = bodyName;
            this.filterEnabled = filterEnabled;
            this.selectedGenus = selectedGenus == null ? Collections.emptySet() : selectedGenus;
            this.selectedSpecies = selectedSpecies == null ? Collections.emptySet() : selectedSpecies;
        }

        private static UiSelection fromPocket(String bodyName, boolean filterEnabled, Set<String> selPocket) {
            Set<String> genus = new LinkedHashSet<>();
            Set<String> species = new LinkedHashSet<>();

            if (selPocket != null) {
                for (String v : selPocket) {
                    if (v == null) {
                        continue;
                    }

                    if (v.startsWith("G: ")) {
                        String g = v.substring(3).trim();
                        if (!g.isEmpty()) {
                            genus.add(g);
                        }
                    } else if (v.startsWith("S: ")) {
                        String s = v.substring(3).trim();
                        if (!s.isEmpty()) {
                            species.add(s);
                        }
                    }
                }
            }

            return new UiSelection(bodyName, filterEnabled, genus, species);
        }
    }

    /**
     * Best-effort index of genus/species from your constraint data.
     */
    private static final class SpeciesIndex {
        private final Map<String, Set<String>> genusToSpecies;
        private final List<String> pocketEntries;

        private SpeciesIndex(Map<String, Set<String>> genusToSpecies, List<String> pocketEntries) {
            this.genusToSpecies = genusToSpecies;
            this.pocketEntries = pocketEntries;
        }

        private static SpeciesIndex buildBestEffort() {
            try {
                return buildFromConstraints();
            } catch (Exception e) {
                return new SpeciesIndex(Collections.emptyMap(), Collections.emptyList());
            }
        }

        private static SpeciesIndex buildFromConstraints() throws Exception {
            List<?> constraints = loadConstraintsObjects();
            if (constraints == null || constraints.isEmpty()) {
                return new SpeciesIndex(Collections.emptyMap(), Collections.emptyList());
            }

            Map<String, Set<String>> genusToSpecies = new LinkedHashMap<>();
            Set<String> genusSet = new LinkedHashSet<>();
            Set<String> speciesSet = new LinkedHashSet<>();

            for (Object sc : constraints) {
                if (sc == null) {
                    continue;
                }

                String genus = readStringProperty(sc, "getGenus", "genus", "genusName", "getGenusName");
                String species = readStringProperty(sc, "getSpecies", "species", "speciesName", "getSpeciesName");

                if (genus == null) {
                    continue;
                }
                genus = genus.trim();
                if (genus.isEmpty()) {
                    continue;
                }

                genusSet.add(genus);

                if (species != null) {
                    species = species.trim();
                    if (!species.isEmpty()) {
                        String key = genus + " " + species;
                        speciesSet.add(key);
                        genusToSpecies.computeIfAbsent(genus, k -> new LinkedHashSet<>()).add(key);
                    }
                }
            }

            List<String> genusList = new ArrayList<>(genusSet);
            Collections.sort(genusList);

            List<String> speciesList = new ArrayList<>(speciesSet);
            Collections.sort(speciesList);

            List<String> pocket = new ArrayList<>(genusList.size() + speciesList.size());
            for (String g : genusList) {
                pocket.add(pocketGenus(g));
            }
            for (String s : speciesList) {
                pocket.add(pocketSpecies(s));
            }

            return new SpeciesIndex(genusToSpecies, pocket);
        }

        private static List<?> loadConstraintsObjects() throws Exception {
            Class<?> c = Class.forName("org.dce.ed.exobiology.ExobiologyDataConstraints");

            // Newer generated constraint files expose initConstraints(Map<String, SpeciesConstraint>)
            // rather than any getters/fields. Prefer that when present.
            try {
                Method init = c.getDeclaredMethod("initConstraints", Map.class);
                init.setAccessible(true);
                Map<String, Object> map = new LinkedHashMap<>();
                init.invoke(null, map);
                if (!map.isEmpty()) {
                    return new ArrayList<>(map.values());
                }
            } catch (NoSuchMethodException ignored) {
                // continue
            }

            // Known method names first
            for (String methodName : Arrays.asList("getSpeciesConstraints", "getConstraints", "allSpeciesConstraints", "allConstraints")) {
                try {
                    Method m = c.getDeclaredMethod(methodName);
                    m.setAccessible(true);
                    Object v = m.invoke(null);
                    if (v instanceof List) {
                        return (List<?>) v;
                    }
                } catch (NoSuchMethodException ignored) {
                    // continue
                }
            }

            // Any no-arg static method returning List
            for (Method m : c.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                if (m.getParameterCount() != 0) {
                    continue;
                }
                if (!List.class.isAssignableFrom(m.getReturnType())) {
                    continue;
                }

                m.setAccessible(true);
                Object v = m.invoke(null);
                if (v instanceof List) {
                    return (List<?>) v;
                }
            }

            // Known field name first
            try {
                Field f = c.getDeclaredField("CONSTRAINTS");
                f.setAccessible(true);
                Object v = f.get(null);
                if (v instanceof List) {
                    return (List<?>) v;
                }
            } catch (NoSuchFieldException ignored) {
                // continue
            }

            // Any static field that is a List
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (!List.class.isAssignableFrom(f.getType())) {
                    continue;
                }

                f.setAccessible(true);
                Object v = f.get(null);
                if (v instanceof List) {
                    return (List<?>) v;
                }
            }

            return Collections.emptyList();
        }
    }

    private static String readStringProperty(Object obj, String... candidates) {
        Objects.requireNonNull(obj, "obj");

        for (String name : candidates) {
            if (name == null || name.trim().isEmpty()) {
                continue;
            }

            // Try getter
            try {
                Method m = obj.getClass().getMethod(name);
                Object v = m.invoke(obj);
                if (v != null) {
                    return v.toString();
                }
            } catch (Exception ignored) {
                // fall through
            }

            // Try field
            try {
                Field f = obj.getClass().getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v != null) {
                    return v.toString();
                }
            } catch (Exception ignored) {
                // fall through
            }
        }

        return null;
    }

    private static Path defaultCachePath() {
        String home = System.getProperty("user.home");
        if (home == null || home.isEmpty()) {
            home = ".";
        }
        return Paths.get(home, CACHE_FILE_NAME);
    }

    private static CachedBodyMatch findBodyInCache(Path cachePath, String bodyName) throws IOException {

        Gson gson = new GsonBuilder()
                .serializeSpecialFloatingPointValues()
                .create();

        Type type = new TypeToken<List<CachedSystem>>() {}.getType();

        try (BufferedReader reader = Files.newBufferedReader(cachePath, StandardCharsets.UTF_8)) {

            List<CachedSystem> systems = gson.fromJson(reader, type);
            if (systems == null || systems.isEmpty()) {
                return null;
            }

            for (CachedSystem cs : systems) {
                if (cs == null || cs.bodies == null) {
                    continue;
                }

                for (CachedBody cb : cs.bodies) {
                    if (cb == null || cb.name == null) {
                        continue;
                    }

                    if (cb.name.equalsIgnoreCase(bodyName)) {
                        return new CachedBodyMatch(cs, cb);
                    }
                }
            }
        }

        return null;
    }

    private static BodyInfo toBodyInfo(CachedSystem cs, CachedBody cb) {
        BodyInfo info = new BodyInfo();

        info.setBodyName(cb.name);
        info.setStarSystem(cb.starSystem);

        info.setStarPos(cs.starPos);

        info.setBodyId(cb.bodyId);
        info.setDistanceLs(cb.distanceLs);
        info.setGravityMS(cb.gravityMS);
        info.setSurfacePressure(cb.surfacePressure);

        info.setLandable(cb.landable);
        info.setHasBio(cb.hasBio);
        info.setHasGeo(cb.hasGeo);
        info.setHighValue(cb.highValue);
        info.setValuableBodyExplorationCredits(cb.valuableBodyExplorationCredits);
        info.setTerraformState(cb.terraformState);
        info.setMassEm(cb.massEm);

        info.setAtmoOrType(cb.atmoOrType);
        info.setPlanetClass(cb.planetClass);
        info.setAtmosphere(cb.atmosphere);

        info.setSurfaceTempK(cb.surfaceTempK);
        info.setOrbitalPeriod(cb.orbitalPeriod);
        info.setSemiMajorAxisM(cb.semiMajorAxisM);
        info.setEccentricity(cb.eccentricity);
        info.setOrbitalInclination(cb.orbitalInclination);
        info.setPeriapsis(cb.periapsis);
        info.setAscendingNode(cb.ascendingNode);
        info.setMeanAnomaly(cb.meanAnomaly);
        info.setOrbitalEpochMillis(cb.orbitalEpochMillis);
        info.setVolcanism(cb.volcanism);
        info.setStarType(cb.starType);
        
        info.setNumberOfBioSignals(cb.getNumberOfBioSignals());
        info.setDiscoveryCommander(cb.discoveryCommander);

        info.setNebula(cb.nebula);
        info.setParentStar(cb.parentStar);
        info.setImmediateParentBodyId(cb.immediateParentBodyId);

        if (cb.observedGenusPrefixes != null && !cb.observedGenusPrefixes.isEmpty()) {
            info.setObservedGenusPrefixes(new java.util.HashSet<>(cb.observedGenusPrefixes));
        }
        if (cb.observedBioDisplayNames != null && !cb.observedBioDisplayNames.isEmpty()) {
            info.setObservedBioDisplayNames(new java.util.HashSet<>(cb.observedBioDisplayNames));
        }

        if (cb.predictions != null && !cb.predictions.isEmpty()) {
            info.setPredictions(new java.util.ArrayList<>(cb.predictions));
        }
        if (info.isPlanetaryBodyForRingDisplay()
                && cb.ringReserveHumanized != null
                && !cb.ringReserveHumanized.isBlank()) {
            info.setRingReserveHumanized(cb.ringReserveHumanized);
        }
        if (info.isPlanetaryBodyForRingDisplay()
                && cb.ringTypes != null
                && !cb.ringTypes.isEmpty()) {
            info.setRingSummaryLines(new java.util.ArrayList<>(cb.ringTypes));
        }

        return info;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static final class CachedBodyMatch {
        private final CachedSystem system;
        private final CachedBody body;

        private CachedBodyMatch(CachedSystem system, CachedBody body) {
            this.system = system;
            this.body = body;
        }
    }

    private BioPredictFromCacheMain() {
    }
}
