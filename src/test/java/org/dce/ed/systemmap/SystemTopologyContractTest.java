package org.dce.ed.systemmap;



import static org.junit.jupiter.api.Assertions.assertTrue;



import java.io.IOException;

import java.time.Instant;

import java.util.stream.Stream;



import org.dce.ed.systemmap.SystemMapHierarchyBuilder.Graph;

import org.dce.ed.systemmap.SystemMapHierarchyBuilder.Node;

import org.junit.jupiter.params.ParameterizedTest;

import org.junit.jupiter.params.provider.Arguments;

import org.junit.jupiter.params.provider.MethodSource;



/** Hierarchy graph and {@link ModelMapTranscriber} must share the same parent edges from {@link org.dce.systemmodel.model.SystemModel}. */

class SystemTopologyContractTest {



    @ParameterizedTest(name = "{0}")

    @MethodSource("topologyFixtures")

    void modelTopology_matchesHierarchyGraph(String fixtureName, TopologyExpect expect) throws IOException {

        SystemMapFixture fx = SystemMapFixtureLoader.loadClasspath(fixtureName);

        SystemSession session = SystemTopologyParity.openSession(fx);

        assertTrue(session.hasModel(), "model from " + fixtureName);



        Graph graph = SystemModelHierarchyBuilder.buildForSession(session);

        assertTrue(SystemModelHierarchyBuilder.isUsableHierarchy(graph));

        SystemTopologyParity.assertNoOrphanStars(session, graph);



        var bodies = fx.toBodies();

        var mapParents = ModelMapTranscriber.hierarchyResolvedParents(session.model(), bodies);

        SystemMapModel mapModel = SystemMapPipeline.build(fx.name, bodies, Instant.EPOCH, false, session);



        for (ParentExpect pe : expect.parents()) {

            SystemTopologyParity.assertParentAligned(

                    graph, mapParents, mapModel,

                    fx.bodyIdByLabel(pe.bodyLabel()),

                    pe.expectedParentKey(fx));

        }

        for (StructureExpect se : expect.structures()) {

            se.verify(graph, fx);

        }

    }



    static Stream<Arguments> topologyFixtures() {

        return Stream.of(

                Arguments.of(

                        "eol-prou-up-n-d7-288.json",

                        TopologyExpect.of()

                                .parent("B", ctx -> SystemTopologyParity.baryKey(2))

                                .parent("C", ctx -> -1)

                                .structure("A", "Null:2")

                                .structure("Null:2", "B")),

                Arguments.of(

                        "eol-prou-tv-a-c15-43.json",

                        TopologyExpect.of()

                                .parent("A", ctx -> SystemTopologyParity.baryKey(1))

                                .parent("B", ctx -> SystemTopologyParity.baryKey(1))

                                .parent("C", ctx -> -1)

                                .structure("Null:1", "A")

                                .structure("Null:1", "B")));

    }



    record ParentExpect(String bodyLabel, java.util.function.Function<SystemMapFixture, Integer> parentKeyFn) {

        int expectedParentKey(SystemMapFixture fx) {

            return parentKeyFn.apply(fx).intValue();

        }

    }



    record StructureExpect(String parentLabel, String childLabel) {

        void verify(Graph graph, SystemMapFixture fx) {

            int parentKey = resolveKey(parentLabel, fx);

            int childKey = resolveKey(childLabel, fx);

            Node parent = graph.nodeByKey.get(parentKey);

            assertTrue(parent != null, "graph contains " + parentLabel);

            assertTrue(parent.children.stream().anyMatch(n -> n.mapKey == childKey),

                    parentLabel + " has child " + childLabel);

        }



        private static int resolveKey(String label, SystemMapFixture fx) {

            if (label.startsWith("Null:")) {

                return SystemTopologyParity.baryKey(Integer.parseInt(label.substring(5)));

            }

            return fx.bodyIdByLabel(label);

        }

    }



    static final class TopologyExpect {

        private final java.util.List<ParentExpect> parents = new java.util.ArrayList<>();

        private final java.util.List<StructureExpect> structures = new java.util.ArrayList<>();



        static TopologyExpect of() {

            return new TopologyExpect();

        }



        TopologyExpect parent(String body, java.util.function.Function<SystemMapFixture, Integer> keyFn) {

            parents.add(new ParentExpect(body, keyFn));

            return this;

        }



        TopologyExpect structure(String parent, String child) {

            structures.add(new StructureExpect(parent, child));

            return this;

        }



        java.util.List<ParentExpect> parents() {

            return parents;

        }



        java.util.List<StructureExpect> structures() {

            return structures;

        }

    }

}


