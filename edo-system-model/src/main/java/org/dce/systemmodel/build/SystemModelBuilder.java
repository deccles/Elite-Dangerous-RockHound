package org.dce.systemmodel.build;

import org.dce.systemmodel.designation.DesignationParser;
import org.dce.systemmodel.journal.JournalRecord;
import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.NullParentRefs;
import org.dce.systemmodel.journal.ScanBaryCentreRecord;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.model.BarycentreNode;
import org.dce.systemmodel.model.BodyKind;
import org.dce.systemmodel.model.BodyNode;
import org.dce.systemmodel.model.HierarchyGraph;
import org.dce.systemmodel.model.HierarchyKeys;
import org.dce.systemmodel.model.SystemModel;
import org.dce.systemmodel.position.PositionEngine;
import org.dce.systemmodel.validate.SystemModelValidator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Instant;

public final class SystemModelBuilder {

    private String systemName = "";
    private long systemAddress;
    private final List<JournalRecord> records = new ArrayList<>();
    private final Map<Integer, ScanRecord> scans = new LinkedHashMap<>();
    private final Map<Integer, ScanBaryCentreRecord> barycentres = new LinkedHashMap<>();
    private final Map<Integer, Integer> baryHostByNullId = new HashMap<>();
    private final List<String> incompleteReasons = new ArrayList<>();

    public SystemModelBuilder systemName(String name) {
        this.systemName = name != null ? name : "";
        return this;
    }

    public SystemModelBuilder systemAddress(long address) {
        this.systemAddress = address;
        return this;
    }

    public SystemModelBuilder add(JournalRecord record) {
        if (record == null) {
            return this;
        }
        records.add(record);
        if (record instanceof ScanRecord s) {
            scans.put(s.bodyId(), s);
        } else if (record instanceof ScanBaryCentreRecord b) {
            barycentres.put(b.bodyId(), b);
            linkBarycentreFromMembers(b.bodyId());
        }
        return this;
    }

    public SystemModelBuilder addAll(List<? extends JournalRecord> list) {
        if (list != null) {
            for (JournalRecord r : list) {
                add(r);
            }
        }
        return this;
    }

    public SystemModel build() {
        Built built = assemble(false);
        if (!built.incompleteReasons.isEmpty()) {
            throw new IncompleteSystemException(built.incompleteReasons);
        }
        SystemModelValidator.validateStrict(built.model);
        return built.model;
    }

    public SystemModel buildPartial() {
        Built built = assemble(true);
        return built.model;
    }

    public List<String> incompleteReasons() {
        Built built = assemble(true);
        return built.incompleteReasons;
    }

    private Built assemble(boolean allowIncomplete) {
        incompleteReasons.clear();
        Map<Integer, BodyNode> bodyNodes = new LinkedHashMap<>();
        Map<Integer, BarycentreNode> baryNodes = new LinkedHashMap<>();
        Set<Integer> knownNullIds = new HashSet<>(barycentres.keySet());
        Map<Integer, List<Integer>> membersByNullId = indexNullBarycentreMembers();
        refreshAllPlanetHostedBarycentreLinks(membersByNullId);
        Set<Integer> nullReferencedAsParent = nullIdsReferencedFromScansAndBary(barycentres, scans);

        for (ScanBaryCentreRecord b : barycentres.values()) {
            List<Integer> members = membersByNullId.getOrDefault(b.bodyId(), List.of());
            boolean hasChildIds = b.childBodyIds() != null && !b.childBodyIds().isEmpty();
            boolean hasJournalParents = b.parents() != null && !b.parents().isEmpty();
            if (members.isEmpty() && !hasChildIds && !nullReferencedAsParent.contains(b.bodyId())
                    && !hasJournalParents) {
                continue;
            }
            ParentRef orbitParent = resolveBarycentreOrbitParent(b, members, knownNullIds, membersByNullId);
            baryNodes.put(
                    b.bodyId(),
                    new BarycentreNode(
                            b.bodyId(),
                            b.bodyName(),
                            orbitParent,
                            b.parents(),
                            members.isEmpty() && b.childBodyIds() != null
                                    ? List.copyOf(b.childBodyIds())
                                    : List.copyOf(members),
                            b.orbit()));
            if (orbitParent == null) {
                incompleteReasons.add("barycentre " + b.bodyId() + " missing orbit parent");
            }
        }

        synthesizeSharedNullBarycentres(baryNodes, membersByNullId, knownNullIds);
        knownNullIds = new HashSet<>(baryNodes.keySet());

        for (ScanRecord scan : scans.values()) {
            BodyKind kind = classify(scan);
            boolean moon = kind == BodyKind.MOON;
            boolean definitive = true;
            int pendingNullId = NullParentRefs.anyNullParentIdInChain(scan);
            if (moon && pendingNullId > 0 && !knownNullIds.contains(pendingNullId)) {
                incompleteReasons.add("moon " + scan.bodyId() + " awaits ScanBaryCentre for Null:"
                        + pendingNullId);
                definitive = false;
            }
            ParentRef orbitParent = OrbitParentSelector.select(
                    kind, scan, scan.parents(), knownNullIds, membersByNullId, scans);
            bodyNodes.put(
                    scan.bodyId(),
                    new BodyNode(
                            scan.bodyId(),
                            scan.bodyName(),
                            kind,
                            scan.bodyType(),
                            scan.subType(),
                            scan.distanceFromArrivalLs(),
                            orbitParent,
                            scan.parents() != null ? List.copyOf(scan.parents()) : List.of(),
                            scan.orbit(),
                            definitive));
        }

        HierarchyGraph.Builder hg = HierarchyGraph.builder();
        for (BodyNode b : bodyNodes.values()) {
            if (b.orbitParent() != null && b.orbitParent().bodyId() != b.bodyId()) {
                hg.addEdge(hierarchyParentKey(b.orbitParent()), b.bodyId());
            }
        }
        for (BarycentreNode bc : baryNodes.values()) {
            if (bc.orbitParent() != null && bc.orbitParent().bodyId() != bc.bodyId()) {
                hg.addEdge(hierarchyParentKey(bc.orbitParent()), HierarchyKeys.baryMapKey(bc.bodyId()));
            }
        }

        HierarchyGraph graph = hg.build();
        PositionEngine pe = new PositionEngine(bodyNodes, baryNodes, graph);
        Instant refEpoch = computeReferenceEpoch(scans, barycentres);
        SystemModel model = new SystemModel(systemName, systemAddress, refEpoch, bodyNodes, baryNodes, graph, pe);
        if (!allowIncomplete && !incompleteReasons.isEmpty()) {
            throw new IncompleteSystemException(incompleteReasons);
        }
        return new Built(model, List.copyOf(incompleteReasons));
    }

    private ParentRef resolveBarycentreOrbitParent(
            ScanBaryCentreRecord b,
            List<Integer> memberIds,
            Set<Integer> knownNullIds,
            Map<Integer, List<Integer>> membersByNullId) {
        if (memberIds != null
                && OrbitParentSelector.isCoOrbitMajorHub(b.bodyId(), membersByNullId, scans)) {
            ParentRef starParent = inferCoOrbitBaryOrbitParent(memberIds);
            if (starParent != null) {
                return starParent;
            }
        }
        Integer host = baryHostByNullId.get(b.bodyId());
        if (host != null && host >= 0) {
            return new ParentRef(ParentRef.ParentType.PLANET, host);
        }
        if (memberIds != null && !memberIds.isEmpty()) {
            int planetHost = inferPlanetHostFromMembers(memberIds);
            if (planetHost >= 0) {
                return new ParentRef(ParentRef.ParentType.PLANET, planetHost);
            }
        }
        if (hasStellarMembers(memberIds)) {
            ParentRef starParent = inferCoOrbitBaryOrbitParent(memberIds);
            if (starParent != null) {
                return starParent;
            }
        }
        if (b.parents() != null && !b.parents().isEmpty()) {
            ParentRef fromJournal = OrbitParentSelector.select(
                    BodyKind.BARYCENTRE, null, b.parents(), knownNullIds, membersByNullId, scans);
            if (fromJournal != null) {
                return fromJournal;
            }
        }
        /*
         * ScanBaryCentre journal lines carry heliocentric orbital elements only (no Parents[]). When members are
         * stellar or elements exist, the barycentre orbits the arrival star.
         */
        if (b.orbit() != null || hasStellarMembers(memberIds)) {
            int starId = primaryStarId();
            if (starId >= 0) {
                return new ParentRef(ParentRef.ParentType.STAR, starId);
            }
        }
        return null;
    }

    private void refreshAllPlanetHostedBarycentreLinks(Map<Integer, List<Integer>> membersByNullId) {
        Set<Integer> nullIds = new HashSet<>(barycentres.keySet());
        if (membersByNullId != null) {
            nullIds.addAll(membersByNullId.keySet());
        }
        for (int nullId : nullIds) {
            linkBarycentreFromMembers(nullId);
        }
    }

    private Map<Integer, List<Integer>> indexNullBarycentreMembers() {
        Map<Integer, List<Integer>> members = new HashMap<>();
        for (ScanRecord scan : scans.values()) {
            int nullId = NullParentRefs.innermostNullParentId(scan);
            if (nullId > 0) {
                members.computeIfAbsent(nullId, k -> new ArrayList<>()).add(scan.bodyId());
            }
        }
        Map<Integer, List<Integer>> frozen = new HashMap<>();
        for (var e : members.entrySet()) {
            frozen.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return frozen;
    }

    private int inferPlanetHostFromMembers(List<Integer> memberIds) {
        for (int id : memberIds) {
            ScanRecord scan = scans.get(id);
            if (scan == null || !isPlanetHostedMoon(scan)) {
                continue;
            }
            int host = journalPlanetHost(scan);
            if (host >= 0) {
                return host;
            }
        }
        return -1;
    }

    private boolean hasStellarMembers(List<Integer> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return false;
        }
        for (int id : memberIds) {
            ScanRecord scan = scans.get(id);
            if (scan != null && isStellar(scan)) {
                return true;
            }
        }
        return false;
    }

    private int primaryStarId() {
        if (scans.containsKey(0)) {
            return 0;
        }
        for (ScanRecord scan : scans.values()) {
            if (isStellar(scan)) {
                return scan.bodyId();
            }
        }
        return -1;
    }

    private static int hierarchyParentKey(ParentRef orbitParent) {
        if (orbitParent == null) {
            return 0;
        }
        return switch (orbitParent.type()) {
            case NULL -> HierarchyKeys.baryMapKey(orbitParent.bodyId());
            case PLANET, STAR -> orbitParent.bodyId();
        };
    }

    private static Set<Integer> nullIdsReferencedAsBaryParent(Map<Integer, ScanBaryCentreRecord> barycentres) {
        Set<Integer> refs = new HashSet<>();
        for (ScanBaryCentreRecord b : barycentres.values()) {
            if (b.parents() == null) {
                continue;
            }
            for (ParentRef p : b.parents()) {
                if (p.type() == ParentRef.ParentType.NULL && p.bodyId() > 0) {
                    refs.add(p.bodyId());
                }
            }
        }
        return refs;
    }

    private static Set<Integer> nullIdsReferencedFromScansAndBary(
            Map<Integer, ScanBaryCentreRecord> barycentres, Map<Integer, ScanRecord> scans) {
        Set<Integer> refs = nullIdsReferencedAsBaryParent(barycentres);
        for (ScanRecord scan : scans.values()) {
            int nullId = NullParentRefs.anyNullParentIdInChain(scan);
            if (nullId > 0) {
                refs.add(nullId);
            }
        }
        return refs;
    }

    private void synthesizeSharedNullBarycentres(
            Map<Integer, BarycentreNode> baryNodes,
            Map<Integer, List<Integer>> membersByNullId,
            Set<Integer> knownNullIds) {
        for (var e : membersByNullId.entrySet()) {
            int nullId = e.getKey();
            if (baryNodes.containsKey(nullId)) {
                continue;
            }
            List<Integer> members = e.getValue();
            if (!OrbitParentSelector.sharedNullHub(nullId, membersByNullId, scans)) {
                continue;
            }
            ParentRef orbitParent = inferSyntheticBaryOrbitParent(nullId, members, membersByNullId);
            baryNodes.put(
                    nullId,
                    new BarycentreNode(
                            nullId,
                            systemName + " barycentre " + nullId,
                            orbitParent,
                            List.of(),
                            List.copyOf(members),
                            null));
            knownNullIds.add(nullId);
            if (orbitParent == null) {
                incompleteReasons.add("synthetic barycentre " + nullId + " missing orbit parent");
            }
        }
    }

    private ParentRef inferSyntheticBaryOrbitParent(
            int nullId, List<Integer> members, Map<Integer, List<Integer>> membersByNullId) {
        if (OrbitParentSelector.isCoOrbitMajorHub(nullId, membersByNullId, scans)) {
            ParentRef starParent = inferCoOrbitBaryOrbitParent(members);
            if (starParent != null) {
                return starParent;
            }
        }
        Integer host = baryHostByNullId.get(nullId);
        if (host != null && host >= 0) {
            return new ParentRef(ParentRef.ParentType.PLANET, host);
        }
        int planetHost = inferPlanetHostFromMembers(members);
        if (planetHost >= 0) {
            return new ParentRef(ParentRef.ParentType.PLANET, planetHost);
        }
        return inferCoOrbitBaryOrbitParent(members);
    }

    private ParentRef inferCoOrbitBaryOrbitParent(List<Integer> memberIds) {
        for (int id : memberIds) {
            ScanRecord scan = scans.get(id);
            if (scan == null || scan.parents() == null) {
                continue;
            }
            for (ParentRef p : scan.parents()) {
                if (p.type() == ParentRef.ParentType.STAR) {
                    return p;
                }
            }
        }
        int starId = primaryStarId();
        return starId >= 0 ? new ParentRef(ParentRef.ParentType.STAR, starId) : null;
    }

    private void linkBarycentreFromMembers(int nullId) {
        List<Integer> members = new ArrayList<>();
        for (ScanRecord scan : scans.values()) {
            if (referencesNull(scan, nullId)) {
                members.add(scan.bodyId());
            }
        }
        if (!members.isEmpty()
                && OrbitParentSelector.isCoOrbitMajorHub(nullId, Map.of(nullId, members), scans)) {
            return;
        }
        int planetHostId = -1;
        for (ScanRecord moon : scans.values()) {
            if (!referencesNull(moon, nullId)) {
                continue;
            }
            if (!isPlanetHostedMoon(moon)) {
                continue;
            }
            int host = journalPlanetHost(moon);
            if (host >= 0) {
                planetHostId = host;
                break;
            }
        }
        if (planetHostId >= 0) {
            baryHostByNullId.put(nullId, planetHostId);
        }
    }

    private static int journalPlanetHost(ScanRecord moon) {
        if (moon.parents() == null) {
            return -1;
        }
        for (ParentRef p : moon.parents()) {
            if (p.type() == ParentRef.ParentType.PLANET) {
                return p.bodyId();
            }
        }
        return -1;
    }

    private static boolean referencesNull(ScanRecord scan, int nullId) {
        if (scan.parents() == null) {
            return false;
        }
        for (ParentRef p : scan.parents()) {
            if (p.type() == ParentRef.ParentType.NULL && p.bodyId() == nullId) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPlanetHostedMoon(ScanRecord scan) {
        if (scan == null || classify(scan) != BodyKind.MOON) {
            return false;
        }
        return DesignationParser.hasMoonLetterSuffix(scan.bodyName());
    }

    private static BodyKind classify(ScanRecord scan) {
        if (isStellar(scan)) {
            return BodyKind.STAR;
        }
        if (DesignationParser.hasMoonLetterSuffix(scan.bodyName())) {
            return BodyKind.MOON;
        }
        String pc = scan.subType();
        if (pc != null && !pc.isBlank()) {
            return BodyKind.PLANET;
        }
        return BodyKind.PLANET;
    }

    private static boolean isStellar(ScanRecord scan) {
        if ("Star".equalsIgnoreCase(scan.bodyType())) {
            return true;
        }
        return scan.bodyId() == 0;
    }

    private static Instant computeReferenceEpoch(
            Map<Integer, ScanRecord> scans, Map<Integer, ScanBaryCentreRecord> barycentres) {
        Instant earliest = null;
        for (ScanRecord s : scans.values()) {
            if (s.timestamp() != null && (earliest == null || s.timestamp().isBefore(earliest))) {
                earliest = s.timestamp();
            }
        }
        for (ScanBaryCentreRecord b : barycentres.values()) {
            if (b.timestamp() != null && (earliest == null || b.timestamp().isBefore(earliest))) {
                earliest = b.timestamp();
            }
        }
        return earliest != null ? earliest : Instant.EPOCH;
    }

    private record Built(SystemModel model, List<String> incompleteReasons) {
    }
}
