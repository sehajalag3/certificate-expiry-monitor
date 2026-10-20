package com.example.certmonitor;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Deliberate failure drill. This class exists only on the
 * {@code test/deliberate-failure} branch so the Jenkins job
 * {@code Certificate-Expiry-Monitor-FailureDrill} can prove that the pipeline
 * goes red when a test fails. This branch must never be merged into main.
 */
class DeliberateFailureDrillTest {

    @Test
    @DisplayName("Intentionally failing assertion for the Jenkins failure drill")
    void deliberateFailureDrill() {
        fail("""
            DELIBERATE FAILURE DRILL (branch test/deliberate-failure):
            this assertion is intentionally false so the failure-drill pipeline
            proves that a broken test stops the build before any packaging,
            push or deploy stage can run.
            """);
    }
}
