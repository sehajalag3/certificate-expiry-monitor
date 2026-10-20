package com.example.certmonitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Failure-drill repair. This class exists only on the
 * {@code test/deliberate-failure} branch, where its first version failed on
 * purpose so the Jenkins job {@code Certificate-Expiry-Monitor-FailureDrill}
 * could prove that the pipeline goes red before any packaging, push or deploy
 * stage can run (build #1: {@code Tests run: 38, Failures: 1} and every
 * downstream stage reported "skipped due to earlier failure(s)").
 *
 * <p>The false assertion has been repaired. The test now verifies the property
 * the drill claimed, by reading the pipeline definition: the unit-test stage
 * must exist, must run the test gate command, and must be declared before
 * {@code Package}, so a failing test always blocks packaging. This branch must
 * never be merged into main.
 */
class DeliberateFailureDrillTest {

    private static final String UNIT_TEST_STAGE = "stage('Build and Unit Tests')";
    private static final String PACKAGE_STAGE = "stage('Package')";
    private static final String TEST_GATE = "mvnw.cmd -B clean test";

    private String jenkinsfile() throws IOException {
        Path path = Path.of("Jenkinsfile");
        assertTrue(
            Files.exists(path),
            "Jenkinsfile must be readable from the working directory: " + path.toAbsolutePath()
        );
        return Files.readString(path);
    }

    @Test
    @DisplayName("A failing test blocks packaging: unit-test stage runs before Package")
    void unitTestStagePrecedesPackageStage() throws IOException {
        String pipeline = jenkinsfile();

        int unitTestStage = pipeline.indexOf(UNIT_TEST_STAGE);
        int packageStage = pipeline.indexOf(PACKAGE_STAGE);

        assertTrue(unitTestStage >= 0, "pipeline must declare " + UNIT_TEST_STAGE);
        assertTrue(packageStage >= 0, "pipeline must declare " + PACKAGE_STAGE);
        assertTrue(
            unitTestStage < packageStage,
            UNIT_TEST_STAGE + " must be declared before " + PACKAGE_STAGE
        );
    }

    @Test
    @DisplayName("The unit-test stage runs the test gate command before packaging")
    void unitTestStageRunsTheTestGate() throws IOException {
        String pipeline = jenkinsfile();

        int unitTestStage = pipeline.indexOf(UNIT_TEST_STAGE);
        int packageStage = pipeline.indexOf(PACKAGE_STAGE);
        String unitTestStageBody = pipeline.substring(unitTestStage, packageStage);

        assertTrue(
            unitTestStageBody.contains(TEST_GATE),
            "unit-test stage must run '" + TEST_GATE + "' so a failing test stops the build"
        );
        assertEquals(
            1,
            countOccurrences(pipeline, UNIT_TEST_STAGE),
            "unit-test stage must be declared exactly once for the ordering check to hold"
        );
        assertEquals(
            1,
            countOccurrences(pipeline, PACKAGE_STAGE),
            "package stage must be declared exactly once for the ordering check to hold"
        );
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        for (int index = text.indexOf(token); index >= 0; index = text.indexOf(token, index + 1)) {
            count++;
        }
        return count;
    }
}
