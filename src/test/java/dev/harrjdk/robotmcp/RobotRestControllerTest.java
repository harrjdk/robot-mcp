package dev.harrjdk.robotmcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for RobotRestController — no OS interaction.
 * All tool beans are mocked so tests run in any environment.
 */
@WebMvcTest(controllers = {RobotRestController.class, RestExceptionHandler.class})
class RobotRestControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean RobotTools robot;
    @MockitoBean WindowTools windows;
    @MockitoBean ClipboardTools clipboard;
    @MockitoBean ActiveRequestTracker activeRequestTracker;

    @BeforeEach
    void allowRequests() throws Exception {
        // preHandle returns false by default on a mock, which would block every request.
        when(activeRequestTracker.preHandle(
                any(HttpServletRequest.class), any(HttpServletResponse.class), any()))
                .thenReturn(true);
    }

    // -------------------------------------------------------------------------
    // Mouse
    // -------------------------------------------------------------------------

    @Test
    void mouseMove_returnsResult() throws Exception {
        when(robot.mouseMove(100, 200)).thenReturn("Mouse moved to (100, 200)");

        mvc.perform(post("/api/mouse/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":100,\"y\":200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Mouse moved to (100, 200)"));
    }

    @Test
    void leftClick_returnsResult() throws Exception {
        when(robot.leftClick(10, 20)).thenReturn("Left clicked at (10, 20)");

        mvc.perform(post("/api/mouse/left-click")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":10,\"y\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Left clicked at (10, 20)"));
    }

    @Test
    void doubleClick_returnsResult() throws Exception {
        when(robot.doubleClick(5, 5)).thenReturn("Double-clicked at (5, 5)");

        mvc.perform(post("/api/mouse/double-click")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":5,\"y\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Double-clicked at (5, 5)"));
    }

    @Test
    void rightClick_returnsResult() throws Exception {
        when(robot.rightClick(0, 0)).thenReturn("Right clicked at (0, 0)");

        mvc.perform(post("/api/mouse/right-click")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":0,\"y\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Right clicked at (0, 0)"));
    }

    @Test
    void middleClick_returnsResult() throws Exception {
        when(robot.middleClick(1, 2)).thenReturn("Middle-clicked at (1, 2)");

        mvc.perform(post("/api/mouse/middle-click")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":1,\"y\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Middle-clicked at (1, 2)"));
    }

    @Test
    void mouseScroll_returnsResult() throws Exception {
        when(robot.mouseScroll(0, 0, 3)).thenReturn("Scrolled 3 at (0, 0)");

        mvc.perform(post("/api/mouse/scroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":0,\"y\":0,\"amount\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Scrolled 3 at (0, 0)"));
    }

    @Test
    void mouseDrag_returnsResult() throws Exception {
        when(robot.mouseDrag(0, 0, 100, 100)).thenReturn("Dragged from (0, 0) to (100, 100)");

        mvc.perform(post("/api/mouse/drag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x1\":0,\"y1\":0,\"x2\":100,\"y2\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Dragged from (0, 0) to (100, 100)"));
    }

    @Test
    void getMousePosition_returnsResult() throws Exception {
        when(robot.getMousePosition()).thenReturn("Mouse at (300, 400)");

        mvc.perform(get("/api/mouse/position"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Mouse at (300, 400)"));
    }

    @Test
    void scrollUntilPixelColor_returnsResult() throws Exception {
        when(robot.scrollUntilPixelColor(100, 200, 3, 50, 50, "#FF0000", 100, 5000))
                .thenReturn("Found #FF0000 at (50, 50) after 2 scroll step(s)");

        mvc.perform(post("/api/mouse/scroll-until-pixel-color")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scrollX\":100,\"scrollY\":200,\"scrollAmount\":3," +
                                 "\"watchX\":50,\"watchY\":50,\"hexColor\":\"#FF0000\"," +
                                 "\"stepDelayMs\":100,\"timeoutMs\":5000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Found #FF0000 at (50, 50) after 2 scroll step(s)"));
    }

    // -------------------------------------------------------------------------
    // Keyboard
    // -------------------------------------------------------------------------

    @Test
    void typeText_returnsResult() throws Exception {
        when(robot.typeText("hello")).thenReturn("Typed: hello");

        mvc.perform(post("/api/keyboard/type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Typed: hello"));
    }

    @Test
    void typeTextViaClipboard_returnsResult() throws Exception {
        when(robot.typeTextViaClipboard("hello")).thenReturn("Typed via clipboard: hello");

        mvc.perform(post("/api/keyboard/type-via-clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Typed via clipboard: hello"));
    }

    @Test
    void pressKey_returnsResult() throws Exception {
        when(robot.pressKey("ENTER")).thenReturn("Pressed key: ENTER");

        mvc.perform(post("/api/keyboard/press-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyName\":\"ENTER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Pressed key: ENTER"));
    }

    @Test
    void pressKeyCombination_returnsResult() throws Exception {
        when(robot.pressKeyCombination("CTRL+C")).thenReturn("Pressed combination: CTRL+C");

        mvc.perform(post("/api/keyboard/press-combination")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"combination\":\"CTRL+C\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Pressed combination: CTRL+C"));
    }

    // -------------------------------------------------------------------------
    // Screen
    // -------------------------------------------------------------------------

    @Test
    void captureScreen_returnsImageResponse() throws Exception {
        when(robot.captureScreenBase64()).thenReturn("AABBCC==");

        mvc.perform(get("/api/screen/capture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("AABBCC=="))
                .andExpect(jsonPath("$.mediaType").value("image/png"));
    }

    @Test
    void captureScreen_onException_returns500() throws Exception {
        when(robot.captureScreenBase64()).thenThrow(new RuntimeException("display error"));

        mvc.perform(get("/api/screen/capture"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.result").value("Screen capture failed: display error"));
    }

    @Test
    void captureRegion_returnsImageResponse() throws Exception {
        when(robot.captureRegionBase64(10, 20, 100, 200)).thenReturn("DDEEFF==");

        mvc.perform(get("/api/screen/capture-region")
                        .param("x", "10").param("y", "20")
                        .param("width", "100").param("height", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("DDEEFF=="))
                .andExpect(jsonPath("$.mediaType").value("image/png"));
    }

    @Test
    void pixelColor_returnsResult() throws Exception {
        when(robot.findPixelColor(50, 60)).thenReturn("#FF0000");

        mvc.perform(get("/api/screen/pixel-color").param("x", "50").param("y", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("#FF0000"));
    }

    @Test
    void waitForPixelColor_returnsResult() throws Exception {
        when(robot.waitForPixelColor(0, 0, "#FF0000", 1000)).thenReturn("Matched #FF0000 at (0, 0)");

        mvc.perform(post("/api/screen/wait-for-pixel-color")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":0,\"y\":0,\"hexColor\":\"#FF0000\",\"timeoutMs\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Matched #FF0000 at (0, 0)"));
    }

    @Test
    void waitForScreenChange_returnsResult() throws Exception {
        when(robot.waitForScreenChange(0, 0, 200, 100, 500))
                .thenReturn("Screen changed in region (0, 0, 200x100)");

        mvc.perform(post("/api/screen/wait-for-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":0,\"y\":0,\"width\":200,\"height\":100,\"timeoutMs\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Screen changed in region (0, 0, 200x100)"));
    }

    @Test
    void waitForScreenStable_returnsResult() throws Exception {
        when(robot.waitForScreenStable(0, 0, 200, 100, 500))
                .thenReturn("Screen stable in region (0, 0, 200x100)");

        mvc.perform(post("/api/screen/wait-for-stable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":0,\"y\":0,\"width\":200,\"height\":100,\"timeoutMs\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Screen stable in region (0, 0, 200x100)"));
    }

    @Test
    void findPixelInRegion_returnsResult() throws Exception {
        when(robot.findPixelInRegion(10, 20, 100, 80, "#FF0000"))
                .thenReturn("Found #FF0000 at (53, 47)");

        mvc.perform(get("/api/screen/find-pixel")
                        .param("x", "10").param("y", "20")
                        .param("width", "100").param("height", "80")
                        .param("hexColor", "#FF0000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Found #FF0000 at (53, 47)"));
    }

    // -------------------------------------------------------------------------
    // Clipboard
    // -------------------------------------------------------------------------

    @Test
    void getClipboard_returnsResult() throws Exception {
        when(clipboard.getClipboard()).thenReturn("hello clipboard");

        mvc.perform(get("/api/clipboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("hello clipboard"));
    }

    @Test
    void setClipboard_returnsResult() throws Exception {
        when(clipboard.setClipboard("my text")).thenReturn("Clipboard set (7 chars)");

        mvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"my text\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Clipboard set (7 chars)"));
    }

    // -------------------------------------------------------------------------
    // Windows
    // -------------------------------------------------------------------------

    @Test
    void getActiveWindowTitle_returnsResult() throws Exception {
        when(windows.getActiveWindowTitle()).thenReturn("Notepad");

        mvc.perform(get("/api/windows/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Notepad"));
    }

    @Test
    void listWindows_returnsArray() throws Exception {
        when(windows.listWindows()).thenReturn(List.of("Notepad", "Chrome"));

        mvc.perform(get("/api/windows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Notepad"))
                .andExpect(jsonPath("$[1]").value("Chrome"));
    }

    @Test
    void focusWindow_returnsResult() throws Exception {
        when(windows.focusWindow("Notepad")).thenReturn("Focused: Notepad");

        mvc.perform(post("/api/windows/focus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleSubstring\":\"Notepad\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Focused: Notepad"));
    }

    @Test
    void getWindowBounds_returnsResult() throws Exception {
        when(windows.getWindowBounds("Notepad")).thenReturn("x=0, y=0, width=800, height=600");

        mvc.perform(get("/api/windows/bounds").param("titleSubstring", "Notepad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("x=0, y=0, width=800, height=600"));
    }

    @Test
    void minimizeWindow_returnsResult() throws Exception {
        when(windows.minimizeWindow("Notepad")).thenReturn("Minimized: Notepad");

        mvc.perform(post("/api/windows/minimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleSubstring\":\"Notepad\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Minimized: Notepad"));
    }

    @Test
    void maximizeWindow_returnsResult() throws Exception {
        when(windows.maximizeWindow("Notepad")).thenReturn("Maximized: Notepad");

        mvc.perform(post("/api/windows/maximize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleSubstring\":\"Notepad\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Maximized: Notepad"));
    }

    @Test
    void restoreWindow_returnsResult() throws Exception {
        when(windows.restoreWindow("Notepad")).thenReturn("Restored: Notepad");

        mvc.perform(post("/api/windows/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleSubstring\":\"Notepad\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Restored: Notepad"));
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    @Test
    void sleep_returnsResult() throws Exception {
        when(robot.sleep(500)).thenReturn("Slept 500ms");

        mvc.perform(post("/api/util/sleep")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ms\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Slept 500ms"));
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    @Test
    void missingBody_nullPointerException_returns400() throws Exception {
        // Passing a body that maps to null Integer values triggers NPE in the controller
        mvc.perform(post("/api/mouse/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":null,\"y\":null}"))
                .andExpect(status().isBadRequest());
    }
}
