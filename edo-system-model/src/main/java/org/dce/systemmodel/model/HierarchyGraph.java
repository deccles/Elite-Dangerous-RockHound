package org.dce.systemmodel.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HierarchyGraph {

    private final Map<Integer, Integer> parentByChild;
    private final Map<Integer, List<Integer>> childrenByParent;

    public HierarchyGraph(Map<Integer, Integer> parentByChild, Map<Integer, List<Integer>> childrenByParent) {
        this.parentByChild = Map.copyOf(parentByChild);
        Map<Integer, List<Integer>> frozen = new HashMap<>();
        for (var e : childrenByParent.entrySet()) {
            frozen.put(e.getKey(), List.copyOf(e.getValue()));
        }
        this.childrenByParent = Collections.unmodifiableMap(frozen);
    }

    public Integer parentOf(int bodyId) {
        return parentByChild.get(bodyId);
    }

    public List<Integer> childrenOf(int parentId) {
        return childrenByParent.getOrDefault(parentId, List.of());
    }

    public Map<Integer, Integer> parentByChild() {
        return parentByChild;
    }

    public Map<Integer, List<Integer>> childrenByParent() {
        return childrenByParent;
    }

    public Set<Integer> definitiveBodyIds() {
        return Collections.unmodifiableSet(new HashSet<>(parentByChild.keySet()));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<Integer, Integer> parentByChild = new HashMap<>();
        private final Map<Integer, List<Integer>> childrenByParent = new HashMap<>();

        public Builder addEdge(int parentId, int childId) {
            parentByChild.put(childId, parentId);
            childrenByParent.computeIfAbsent(parentId, k -> new java.util.ArrayList<>()).add(childId);
            return this;
        }

        public HierarchyGraph build() {
            Map<Integer, List<Integer>> frozen = new HashMap<>();
            for (var e : childrenByParent.entrySet()) {
                frozen.put(e.getKey(), List.copyOf(e.getValue()));
            }
            return new HierarchyGraph(parentByChild, frozen);
        }
    }
}
