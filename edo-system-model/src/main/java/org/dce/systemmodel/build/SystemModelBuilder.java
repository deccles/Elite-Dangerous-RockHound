package org.dce.systemmodel.build;

import org.dce.systemmodel.designation.DesignationParser;
import org.dce.systemmodel.journal.JournalRecord;
import org.dce.systemmodel.journal.ParentRef;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SystemModelBuilder {

    private String systemName = "";
    private long systemAddress;
    private final List<JournalRecord> records = new ArrayList<>();
    private final Map<Integer, ScanRecord> scans = new LinkedHashMap<>();
    private final Map<Integer, ScanBaryCentreRecord> barycentres = new LinkedHashMap<>();
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
        Map<Integer, List<Integer>> membersByNullId = indexMembersByImmediateNullParent();
        Set<Integer> nullReferencedAsParent = nullIdsReferencedFromScansAndBary(barycentres, scans);

        for (ScanBaryCentreRecord b : barycentres.values()) {
            List<Integer> members = membersByNullId.getOrDefault(b.bodyId(), List.of());
            boolean hasChildIds = b.childBodyIds() != null && !b.childBodyIds().isEmpty();
            boolean hasJournalParents = b.parents() != null && !b.parents().isEmpty();
            if (members.isEmpty() && !hasChildIds && !nullReferencedAsParent.contains(b.bodyId())
                    && !hasJournalParents) {
                continue;
            }
            List<Integer> memberList = members.isEmpty() && b.childBodyIds() != null
                    ? List.copyOf(b.childBodyIds())
                    : List.copyOf(members);
            ParentRef orbitParent = JournalParentChain.immediateOrbitParent(b.parents());
            if (orbitParent == null && !memberList.isEmpty()) {
                orbitParent = BarycentreOrbitParentResolver.fromMemberChains(b.bodyId(), memberList, scans);
            }
            baryNodes.put(
                    b.bodyId(),
                    new BarycentreNode(
                            b.bodyId(),
                            b.bodyName(),
                            orbitParent,
                            b.parents() != null ? List.copyOf(b.parents()) : List.of(),
                            memberList,
                            b.orbit()));
            if (orbitParent == null) {
                incompleteReasons.add("barycentre " + b.bodyId() + " missing orbit parent");
            }
        }

        Set<Integer> knownNullIds = new HashSet<>(baryNodes.keySet());

        for (ScanRecord scan : scans.values()) {
            BodyKind kind = classify(scan);
            boolean arrivalRoot = ArrivalStarRoot.isJournalArrivalStar(scan);
            ParentRef orbitParent = JournalParentChain.immediateOrbitParent(scan.parents());
            boolean definitive = orbitParent != null || arrivalRoot;
            if (orbitParent == null && !arrivalRoot) {
                incompleteReasons.add("body " + scan.bodyId() + " missing parents");
            } else if (orbitParent != null && orbitParent.type() == ParentRef.ParentType.NULL
                    && orbitParent.bodyId() > 0
                    && !knownNullIds.contains(orbitParent.bodyId())) {
                incompleteReasons.add("body " + scan.bodyId() + " awaits ScanBaryCentre for Null:"
                        + orbitParent.bodyId());
                definitive = false;
            }
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
            if (!b.definitive() || b.orbitParent() == null || b.orbitParent().bodyId() == b.bodyId()) {
                continue;
            }
            hg.addEdge(hierarchyParentKey(b.orbitParent()), b.bodyId());
        }
        for (BarycentreNode bc : baryNodes.values()) {
            if (bc.orbitParent() == null || bc.orbitParent().bodyId() == bc.bodyId()) {
                continue;
            }
            hg.addEdge(hierarchyParentKey(bc.orbitParent()), HierarchyKeys.baryMapKey(bc.bodyId()));
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

    private Map<Integer, List<Integer>> indexMembersByImmediateNullParent() {
        Map<Integer, List<Integer>> members = new LinkedHashMap<>();
        for (ScanRecord scan : scans.values()) {
            ParentRef p = JournalParentChain.immediateOrbitParent(scan.parents());
            if (p != null && p.type() == ParentRef.ParentType.NULL && p.bodyId() > 0) {
                members.computeIfAbsent(p.bodyId(), k -> new ArrayList<>()).add(scan.bodyId());
            }
        }
        Map<Integer, List<Integer>> frozen = new LinkedHashMap<>();
        for (var e : members.entrySet()) {
            frozen.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return frozen;
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
            ParentRef p = JournalParentChain.immediateOrbitParent(scan.parents());
            if (p != null && p.type() == ParentRef.ParentType.NULL && p.bodyId() > 0) {
                refs.add(p.bodyId());
            }
        }
        return refs;
    }

    private static BodyKind classify(ScanRecord scan) {
        if (isStellar(scan)) {
            return BodyKind.STAR;
        }
        if (DesignationParser.hasMoonLetterSuffix(scan.bodyName())) {
            return BodyKind.MOON;
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
