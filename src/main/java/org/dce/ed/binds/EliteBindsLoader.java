package org.dce.ed.binds;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;

import org.dce.ed.CombatTabCommands;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Loads Elite Dangerous {@code .binds} keyboard mappings from the Options/Bindings folder.
 */
public final class EliteBindsLoader {

    public static final String[] FIGHTER_ORDER_BINDINGS = CombatTabCommands.fighterBindNames();

    public static final String[] TARGETING_BINDINGS = CombatTabCommands.targetingBindNames();

    private EliteBindsLoader() {
    }

    /** Default Windows bindings directory. */
    public static Path defaultBindingsDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) {
            return null;
        }
        return Path.of(localAppData, "Frontier Developments", "Elite Dangerous", "Options", "Bindings");
    }

    /**
     * Resolves the active binds file under {@code bindingsDir} using {@code StartPreset*} start files
     * when present; otherwise picks the newest {@code *.binds}.
     */
    public static Optional<Path> resolveActiveBindsFile(Path bindingsDir) throws IOException {
        if (bindingsDir == null || !Files.isDirectory(bindingsDir)) {
            return Optional.empty();
        }
        Optional<Path> fromPreset = resolveFromStartPreset(bindingsDir);
        if (fromPreset.isPresent()) {
            return fromPreset;
        }
        Path newest = null;
        long newestMtime = Long.MIN_VALUE;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(bindingsDir, "*.binds")) {
            for (Path p : stream) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                long m = Files.getLastModifiedTime(p).toMillis();
                if (newest == null || m > newestMtime) {
                    newest = p;
                    newestMtime = m;
                }
            }
        }
        return Optional.ofNullable(newest);
    }

    private static Optional<Path> resolveFromStartPreset(Path bindingsDir) throws IOException {
        String[] startNames = {
                "StartPreset.start",
                "StartPreset.ShipControls.start",
                "StartPreset.binds.start"
        };
        for (String name : startNames) {
            Path start = bindingsDir.resolve(name);
            if (!Files.isRegularFile(start)) {
                continue;
            }
            String content = Files.readString(start, StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                continue;
            }
            String preset = content.lines()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !s.startsWith("<"))
                    .findFirst()
                    .orElse(content.split("\\R", 2)[0].trim());
            if (preset.isEmpty() || preset.startsWith("<")) {
                continue;
            }
            Path binds = bindingsDir.resolve(preset + ".binds");
            if (Files.isRegularFile(binds)) {
                return Optional.of(binds);
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(bindingsDir, preset + "*.binds")) {
                Path best = null;
                long bestM = Long.MIN_VALUE;
                for (Path p : stream) {
                    long m = Files.getLastModifiedTime(p).toMillis();
                    if (best == null || m > bestM) {
                        best = p;
                        bestM = m;
                    }
                }
                if (best != null) {
                    return Optional.of(best);
                }
            }
        }
        return Optional.empty();
    }

    public static Map<String, EliteKeyBinding> loadKeyboardBindings(Path bindsFile) throws Exception {
        if (bindsFile == null || !Files.isRegularFile(bindsFile)) {
            return Map.of();
        }
        String xml = Files.readString(bindsFile, StandardCharsets.UTF_8);
        return parseKeyboardBindings(xml);
    }

    public static Map<String, EliteKeyBinding> parseKeyboardBindings(String xml) throws Exception {
        if (xml == null || xml.isBlank()) {
            return Map.of();
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc;
        try (Reader reader = new StringReader(xml)) {
            doc = factory.newDocumentBuilder().parse(new InputSource(reader));
        }
        Element root = doc.getDocumentElement();
        if (root == null) {
            return Map.of();
        }
        Map<String, EliteKeyBinding> out = new LinkedHashMap<>();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) node;
            String bindName = el.getTagName();
            EliteKeyBinding binding = resolveKeyboardBinding(el);
            if (binding != null) {
                out.put(bindName, binding);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /** Loads fighter order bindings from the default Elite bindings directory when available. */
    public static Map<String, EliteKeyBinding> loadFighterOrderBindings() {
        return loadNamedKeyboardBindings(FIGHTER_ORDER_BINDINGS);
    }

    /** Loads ship targeting bindings from the default Elite bindings directory when available. */
    public static Map<String, EliteKeyBinding> loadTargetingBindings() {
        return loadNamedKeyboardBindings(TARGETING_BINDINGS);
    }

    public static Map<String, EliteKeyBinding> loadNamedKeyboardBindings(String[] bindNames) {
        if (bindNames == null || bindNames.length == 0) {
            return Map.of();
        }
        try {
            Path dir = defaultBindingsDirectory();
            Optional<Path> file = resolveActiveBindsFile(dir);
            if (file.isEmpty()) {
                return Map.of();
            }
            Map<String, EliteKeyBinding> all = loadKeyboardBindings(file.get());
            Map<String, EliteKeyBinding> selected = new LinkedHashMap<>();
            for (String name : bindNames) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                EliteKeyBinding b = all.get(name);
                if (b != null) {
                    selected.put(name, b);
                }
            }
            return Collections.unmodifiableMap(selected);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static EliteKeyBinding resolveKeyboardBinding(Element bindElement) {
        EliteKeyBinding primary = parseDeviceSlot(bindElement, "Primary");
        if (primary != null) {
            return primary;
        }
        return parseDeviceSlot(bindElement, "Secondary");
    }

    private static EliteKeyBinding parseDeviceSlot(Element bindElement, String slotName) {
        NodeList slots = bindElement.getElementsByTagName(slotName);
        if (slots.getLength() == 0) {
            return null;
        }
        Element slot = (Element) slots.item(0);
        String device = slot.getAttribute("Device");
        String key = slot.getAttribute("Key");
        if (device == null || !"Keyboard".equalsIgnoreCase(device.trim())) {
            return null;
        }
        if (key == null || key.isBlank()) {
            return null;
        }
        EliteKeyCodeMapper.MappedKey main = EliteKeyCodeMapper.mapKeyToken(key);
        if (main == null || EliteKeyCodeMapper.isModifierToken(key)) {
            return null;
        }
        List<Integer> modifiers = new ArrayList<>();
        List<String> modLabels = new ArrayList<>();
        NodeList children = slot.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) n;
            if (!"modifier".equalsIgnoreCase(child.getTagName())) {
                continue;
            }
            String modDevice = child.getAttribute("Device");
            String modKey = child.getAttribute("Key");
            if (modDevice != null && !modDevice.isBlank()
                    && !"{NoDevice}".equalsIgnoreCase(modDevice)
                    && !"Keyboard".equalsIgnoreCase(modDevice.trim())) {
                return null;
            }
            EliteKeyCodeMapper.MappedKey mapped = EliteKeyCodeMapper.mapKeyToken(modKey);
            if (mapped == null) {
                return null;
            }
            modifiers.add(Integer.valueOf(mapped.getVirtualKey()));
            modLabels.add(mapped.getDisplayLabel());
        }
        String label = EliteKeyCodeMapper.formatChord(modLabels, main.getDisplayLabel());
        return new EliteKeyBinding(main.getVirtualKey(), modifiers, label);
    }
}
