package dev.harrjdk.robotmcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pure unit tests for McpConfig — verifies tool registration without a Spring context.
 */
class McpConfigTest {

    private final McpConfig config = new McpConfig();
    private final RobotTools robotTools = mock(RobotTools.class);

    @Test
    void screenCaptureTools_registersThreeTools() {
        List<McpServerFeatures.SyncToolSpecification> tools = config.screenCaptureTools(robotTools);
        assertThat(tools).hasSize(3);
    }

    @Test
    void screenCaptureTools_hasExpectedNames() {
        List<McpServerFeatures.SyncToolSpecification> tools = config.screenCaptureTools(robotTools);
        assertThat(tools)
                .extracting(s -> s.tool().name())
                .containsExactlyInAnyOrder("captureScreen", "captureRegion", "captureMonitor");
    }

    @Test
    void screenCaptureTools_captureScreenHasNoRequiredParams() {
        McpServerFeatures.SyncToolSpecification captureScreen = config.screenCaptureTools(robotTools)
                .stream()
                .filter(s -> "captureScreen".equals(s.tool().name()))
                .findFirst()
                .orElseThrow();

        assertThat(captureScreen.tool().inputSchema().required()).isEmpty();
        assertThat(captureScreen.tool().inputSchema().properties()).isEmpty();
    }

    @Test
    void screenCaptureTools_captureRegionRequiresFourParams() {
        McpServerFeatures.SyncToolSpecification captureRegion = config.screenCaptureTools(robotTools)
                .stream()
                .filter(s -> "captureRegion".equals(s.tool().name()))
                .findFirst()
                .orElseThrow();

        assertThat(captureRegion.tool().inputSchema().required())
                .containsExactlyInAnyOrder("x", "y", "width", "height");
        assertThat(captureRegion.tool().inputSchema().properties())
                .containsKeys("x", "y", "width", "height");
    }

    @Test
    void screenCaptureTools_captureMonitorRequiresMonitorIndex() {
        McpServerFeatures.SyncToolSpecification captureMonitor = config.screenCaptureTools(robotTools)
                .stream()
                .filter(s -> "captureMonitor".equals(s.tool().name()))
                .findFirst()
                .orElseThrow();

        assertThat(captureMonitor.tool().inputSchema().required())
                .containsExactly("monitorIndex");
        assertThat(captureMonitor.tool().inputSchema().properties())
                .containsKey("monitorIndex");
    }

    @Test
    void screenCaptureTools_allToolsHaveDescriptions() {
        List<McpServerFeatures.SyncToolSpecification> tools = config.screenCaptureTools(robotTools);
        for (McpServerFeatures.SyncToolSpecification spec : tools) {
            assertThat(spec.tool().description())
                    .as("Tool '%s' should have a description", spec.tool().name())
                    .isNotBlank();
        }
    }
}
