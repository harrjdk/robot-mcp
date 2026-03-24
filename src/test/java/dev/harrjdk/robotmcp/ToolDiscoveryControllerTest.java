package dev.harrjdk.robotmcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ToolDiscoveryController.class)
class ToolDiscoveryControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ToolCallbackProvider toolCallbackProvider;
    @MockitoBean List<McpServerFeatures.SyncToolSpecification> syncTools;
    @MockitoBean ActiveRequestTracker activeRequestTracker;

    @BeforeEach
    void allowRequests() throws Exception {
        when(activeRequestTracker.preHandle(
                any(HttpServletRequest.class), any(HttpServletResponse.class), any()))
                .thenReturn(true);
    }

    @Test
    void getTools_returnsOpenAiSchema() throws Exception {
        ToolCallback cb = stubToolCallback("mouseMove", "Move the mouse", """
                {"type":"object","properties":{"x":{"type":"integer"},"y":{"type":"integer"}}}""");
        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{cb});
        when(syncTools.iterator()).thenReturn(List.<McpServerFeatures.SyncToolSpecification>of().iterator());

        mvc.perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("function"))
                .andExpect(jsonPath("$[0].function.name").value("mouseMove"))
                .andExpect(jsonPath("$[0].function.description").value("Move the mouse"))
                .andExpect(jsonPath("$[0].function.parameters.type").value("object"));
    }

    @Test
    void getTools_includesSyncTools() throws Exception {
        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[0]);

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object", Map.of(), List.of(), null, null, null);
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("captureScreen")
                .description("Capture the full screen")
                .inputSchema(schema)
                .build();
        McpServerFeatures.SyncToolSpecification spec =
                McpServerFeatures.SyncToolSpecification.builder()
                        .tool(tool)
                        .callHandler((ex, req) -> null)
                        .build();
        when(syncTools.iterator()).thenReturn(List.of(spec).iterator());

        mvc.perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("function"))
                .andExpect(jsonPath("$[0].function.name").value("captureScreen"))
                .andExpect(jsonPath("$[0].function.description").value("Capture the full screen"));
    }

    @Test
    void getTools_multipleTools_allIncluded() throws Exception {
        ToolCallback cb1 = stubToolCallback("leftClick",  "Left click",  "{\"type\":\"object\"}");
        ToolCallback cb2 = stubToolCallback("rightClick", "Right click", "{\"type\":\"object\"}");
        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{cb1, cb2});
        when(syncTools.iterator()).thenReturn(List.<McpServerFeatures.SyncToolSpecification>of().iterator());

        mvc.perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].function.name").value("leftClick"))
                .andExpect(jsonPath("$[1].function.name").value("rightClick"));
    }

    @Test
    void getTools_emptyRegistry_returnsEmptyArray() throws Exception {
        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[0]);
        when(syncTools.iterator()).thenReturn(List.<McpServerFeatures.SyncToolSpecification>of().iterator());

        mvc.perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ToolCallback stubToolCallback(String name, String description, String schema) {
        ToolDefinition def = ToolDefinition.builder()
                .name(name)
                .description(description)
                .inputSchema(schema)
                .build();
        ToolCallback cb = mock(ToolCallback.class);
        when(cb.getToolDefinition()).thenReturn(def);
        return cb;
    }
}
