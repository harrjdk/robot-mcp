package dev.harrjdk.robotmcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes all available tools as an OpenAI-compatible function-calling schema.
 *
 * GET /api/tools  →  array of {"type":"function","function":{name, description, parameters}}
 *
 * Pass the response directly as the `tools` parameter in an OpenAI API call.
 */
@RestController
@RequestMapping("/api")
public class ToolDiscoveryController {

    private final ToolCallbackProvider toolCallbackProvider;
    private final List<McpServerFeatures.SyncToolSpecification> syncTools;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ToolDiscoveryController(ToolCallbackProvider toolCallbackProvider,
                                   List<McpServerFeatures.SyncToolSpecification> syncTools) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.syncTools = syncTools;
    }

    @GetMapping("/tools")
    public List<Map<String, Object>> getTools() {
        List<Map<String, Object>> tools = new ArrayList<>();

        // Tools from @Tool-annotated methods — schema already serialized as JSON string
        for (ToolCallback callback : toolCallbackProvider.getToolCallbacks()) {
            var def = callback.getToolDefinition();
            try {
                Map<String, Object> schema = objectMapper.readValue(
                        def.inputSchema(),
                        objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));
                tools.add(openAiTool(def.name(), def.description(), schema));
            } catch (Exception e) {
                // skip any tool whose schema cannot be parsed
            }
        }

        // Tools registered as SyncToolSpecification (captureScreen, captureRegion)
        for (McpServerFeatures.SyncToolSpecification spec : syncTools) {
            McpSchema.Tool tool = spec.tool();
            tools.add(openAiTool(tool.name(), tool.description(), toSchemaMap(tool.inputSchema())));
        }

        return tools;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Map<String, Object> openAiTool(String name, String description,
                                                   Map<String, Object> parameters) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parameters
                )
        );
    }

    private static Map<String, Object> toSchemaMap(McpSchema.JsonSchema schema) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", schema.type());
        if (schema.properties() != null && !schema.properties().isEmpty()) {
            map.put("properties", schema.properties());
        }
        if (schema.required() != null && !schema.required().isEmpty()) {
            map.put("required", schema.required());
        }
        return map;
    }
}
