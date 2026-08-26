package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class EdsmClientHttpStatusTest {

    @Test
    void jsonRateLimitResponseIsStillTreatedAsFailure() {
        EdsmRequestGate.Response limited = new EdsmRequestGate.Response(
                429, "application/json", "{\"message\":\"limited\"}");

        assertThrows(IOException.class,
                () -> EdsmClient.requireSuccessfulResponse(limited, "https://edsm.test/bodies"));
    }

    @Test
    void successfulJsonResponseIsAccepted() {
        EdsmRequestGate.Response success = new EdsmRequestGate.Response(200, "application/json", "{}");

        assertDoesNotThrow(() -> EdsmClient.requireSuccessfulResponse(success, "https://edsm.test/bodies"));
    }
}
