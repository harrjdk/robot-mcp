package dev.harrjdk.robotmcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider robotToolCallbacks(RobotTools robotTools,
                                                   WindowTools windowTools,
                                                   ClipboardTools clipboardTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(robotTools, windowTools, clipboardTools)
                .build();
    }

    @Bean
    public List<McpServerFeatures.SyncToolSpecification> screenCaptureTools(RobotTools robotTools,
                                                                             WindowTools windowTools) {
        var captureScreenTool = McpSchema.Tool.builder()
                .name("captureScreen")
                .description("Capture the full virtual desktop (all monitors) and return it as an image.")
                .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(), null, null, null))
                .build();

        var captureRegionTool = McpSchema.Tool.builder()
                .name("captureRegion")
                .description("Capture a region of the screen and return it as an image.")
                .inputSchema(new McpSchema.JsonSchema("object",
                        Map.of(
                                "x",      Map.of("type", "integer", "description", "Left edge of the region"),
                                "y",      Map.of("type", "integer", "description", "Top edge of the region"),
                                "width",  Map.of("type", "integer", "description", "Width of the region in pixels"),
                                "height", Map.of("type", "integer", "description", "Height of the region in pixels")
                        ),
                        List.of("x", "y", "width", "height"),
                        null, null, null))
                .build();

        var captureMonitorTool = McpSchema.Tool.builder()
                .name("captureMonitor")
                .description("Capture a specific monitor by index and return it as an image. Use listMonitors to get available indices.")
                .inputSchema(new McpSchema.JsonSchema("object",
                        Map.of("monitorIndex", Map.of("type", "integer", "description", "Zero-based monitor index")),
                        List.of("monitorIndex"),
                        null, null, null))
                .build();

        var captureWindowTool = McpSchema.Tool.builder()
                .name("captureWindow")
                .description("Capture a specific window by title and return it as an image. Matches the first visible window whose title contains the given substring (case-insensitive). Restores the window first if it is minimized.")
                .inputSchema(new McpSchema.JsonSchema("object",
                        Map.of("titleSubstring", Map.of("type", "string", "description", "Case-insensitive substring to match against window titles")),
                        List.of("titleSubstring"),
                        null, null, null))
                .build();

        return List.of(
                McpServerFeatures.SyncToolSpecification.builder()
                        .tool(captureScreenTool)
                        .callHandler((exchange, request) -> imageToolResult(
                                robotTools::captureScreenBase64))
                        .build(),

                McpServerFeatures.SyncToolSpecification.builder()
                        .tool(captureRegionTool)
                        .callHandler((exchange, request) -> {
                            Map<String, Object> args = request.arguments();
                            int x      = ((Number) args.get("x")).intValue();
                            int y      = ((Number) args.get("y")).intValue();
                            int width  = ((Number) args.get("width")).intValue();
                            int height = ((Number) args.get("height")).intValue();
                            return imageToolResult(() -> robotTools.captureRegionBase64(x, y, width, height));
                        })
                        .build(),

                McpServerFeatures.SyncToolSpecification.builder()
                        .tool(captureMonitorTool)
                        .callHandler((exchange, request) -> {
                            int monitorIndex = ((Number) request.arguments().get("monitorIndex")).intValue();
                            return imageToolResult(() -> robotTools.captureMonitorBase64(monitorIndex));
                        })
                        .build(),

                McpServerFeatures.SyncToolSpecification.builder()
                        .tool(captureWindowTool)
                        .callHandler((exchange, request) -> {
                            String titleSubstring = (String) request.arguments().get("titleSubstring");
                            return imageToolResult(() -> windowTools.captureWindowBase64(titleSubstring));
                        })
                        .build()
        );
    }

    private static McpSchema.CallToolResult imageToolResult(Callable<String> capture) {
        try {
            String base64 = capture.call();
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.ImageContent(null, base64, "image/png")))
                    .isError(false)
                    .build();
        } catch (Exception e) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(String.format("Capture failed: %s", e.getMessage()))))
                    .isError(true)
                    .build();
        }
    }
}
