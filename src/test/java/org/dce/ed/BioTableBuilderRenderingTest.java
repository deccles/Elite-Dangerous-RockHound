package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dce.ed.exobiology.ExobiologyData.BioCandidate;
import org.dce.ed.exobiology.ExobiologyData.SpeciesConstraint;
import org.dce.ed.state.BodyInfo;
import org.junit.jupiter.api.Test;

class BioTableBuilderRenderingTest {

    @Test
    void estimatedCreditsDoesNotWaitForNetworkOnCacheMiss() {
        ProxySelector original = ProxySelector.getDefault();
        ProxySelector.setDefault(new BlockingProxySelector(original));
        try {
            BodyInfo body = new BodyInfo();
            body.setStarSystem("Rendering Test System");
            body.setBodyName("Rendering Test Body");
            body.setHasBio(true);
            body.setPredictions(new ArrayList<>(List.of(candidate())));

            assertTimeoutPreemptively(Duration.ofMillis(500),
                    () -> BioTableBuilder.getMaxBioEstimatedCredits(body));
        } finally {
            ProxySelector.setDefault(original);
        }
    }

    private static BioCandidate candidate() {
        SpeciesConstraint constraint = new SpeciesConstraint(
                "Bacterium", "Acies", 1_000_000L, Collections.emptyList());
        return new BioCandidate(constraint, 0.5, null);
    }

    private static final class BlockingProxySelector extends ProxySelector {
        private final ProxySelector delegate;

        private BlockingProxySelector(ProxySelector delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<Proxy> select(URI uri) {
            if (uri != null && uri.toString().contains("Rendering")) {
                try {
                    Thread.sleep(5_000L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            return delegate != null ? delegate.select(uri) : List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            if (delegate != null) {
                delegate.connectFailed(uri, sa, ioe);
            }
        }
    }
}
