package dev.harrjdk.robotmcp;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST fallback exposing the same capabilities as the MCP tools.
 * Useful for LLM clients that do not support MCP.
 *
 * All action endpoints return {"result": "<message>"}.
 * Image endpoints return {"data": "<base64>", "mediaType": "image/png"}.
 */
@RestController
@RequestMapping("/api")
public class RobotRestController {

    private final RobotTools robot;
    private final WindowTools windows;
    private final ClipboardTools clipboard;

    public RobotRestController(RobotTools robot, WindowTools windows, ClipboardTools clipboard) {
        this.robot = robot;
        this.windows = windows;
        this.clipboard = clipboard;
    }

    // -------------------------------------------------------------------------
    // Mouse
    // -------------------------------------------------------------------------

    @PostMapping("/mouse/move")
    public Map<String, String> mouseMove(@RequestBody Map<String, Integer> body) {
        return result(robot.mouseMove(body.get("x"), body.get("y")));
    }

    @PostMapping("/mouse/left-click")
    public Map<String, String> leftClick(@RequestBody Map<String, Integer> body) {
        return result(robot.leftClick(body.get("x"), body.get("y")));
    }

    @PostMapping("/mouse/double-click")
    public Map<String, String> doubleClick(@RequestBody Map<String, Integer> body) {
        return result(robot.doubleClick(body.get("x"), body.get("y")));
    }

    @PostMapping("/mouse/right-click")
    public Map<String, String> rightClick(@RequestBody Map<String, Integer> body) {
        return result(robot.rightClick(body.get("x"), body.get("y")));
    }

    @PostMapping("/mouse/middle-click")
    public Map<String, String> middleClick(@RequestBody Map<String, Integer> body) {
        return result(robot.middleClick(body.get("x"), body.get("y")));
    }

    @PostMapping("/mouse/scroll")
    public Map<String, String> mouseScroll(@RequestBody Map<String, Integer> body) {
        return result(robot.mouseScroll(body.get("x"), body.get("y"), body.get("amount")));
    }

    @PostMapping("/mouse/drag")
    public Map<String, String> mouseDrag(@RequestBody Map<String, Integer> body) {
        return result(robot.mouseDrag(body.get("x1"), body.get("y1"), body.get("x2"), body.get("y2")));
    }

    // -------------------------------------------------------------------------
    // Keyboard
    // -------------------------------------------------------------------------

    @PostMapping("/keyboard/type")
    public Map<String, String> typeText(@RequestBody Map<String, String> body) {
        return result(robot.typeText(body.get("text")));
    }

    @PostMapping("/keyboard/press-key")
    public Map<String, String> pressKey(@RequestBody Map<String, String> body) {
        return result(robot.pressKey(body.get("keyName")));
    }

    @PostMapping("/keyboard/press-combination")
    public Map<String, String> pressKeyCombination(@RequestBody Map<String, String> body) {
        return result(robot.pressKeyCombination(body.get("combination")));
    }

    // -------------------------------------------------------------------------
    // Screen
    // -------------------------------------------------------------------------

    @GetMapping("/screen/capture")
    public ResponseEntity<Map<String, String>> captureScreen() {
        try {
            return ResponseEntity.ok(image(robot.captureScreenBase64()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(result("Screen capture failed: " + e.getMessage()));
        }
    }

    @GetMapping("/screen/capture-region")
    public ResponseEntity<Map<String, String>> captureRegion(
            @RequestParam int x, @RequestParam int y,
            @RequestParam int width, @RequestParam int height) {
        try {
            return ResponseEntity.ok(image(robot.captureRegionBase64(x, y, width, height)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(result("Region capture failed: " + e.getMessage()));
        }
    }

    @GetMapping("/screen/pixel-color")
    public Map<String, String> pixelColor(@RequestParam int x, @RequestParam int y) {
        return result(robot.findPixelColor(x, y));
    }

    @PostMapping("/screen/wait-for-pixel-color")
    public Map<String, String> waitForPixelColor(@RequestBody Map<String, Object> body) {
        int x          = ((Number) body.get("x")).intValue();
        int y          = ((Number) body.get("y")).intValue();
        String color   = (String) body.get("hexColor");
        int timeoutMs  = ((Number) body.get("timeoutMs")).intValue();
        return result(robot.waitForPixelColor(x, y, color, timeoutMs));
    }

    @PostMapping("/screen/wait-for-change")
    public Map<String, String> waitForScreenChange(@RequestBody Map<String, Object> body) {
        int x         = ((Number) body.get("x")).intValue();
        int y         = ((Number) body.get("y")).intValue();
        int width     = ((Number) body.get("width")).intValue();
        int height    = ((Number) body.get("height")).intValue();
        int timeoutMs = ((Number) body.get("timeoutMs")).intValue();
        return result(robot.waitForScreenChange(x, y, width, height, timeoutMs));
    }

    // -------------------------------------------------------------------------
    // Clipboard
    // -------------------------------------------------------------------------

    @GetMapping(value = "/clipboard", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> getClipboard() {
        return result(clipboard.getClipboard());
    }

    @PostMapping("/clipboard")
    public Map<String, String> setClipboard(@RequestBody Map<String, String> body) {
        return result(clipboard.setClipboard(body.get("text")));
    }

    // -------------------------------------------------------------------------
    // Windows
    // -------------------------------------------------------------------------

    @GetMapping("/windows/active")
    public Map<String, String> getActiveWindowTitle() {
        return result(windows.getActiveWindowTitle());
    }

    @GetMapping("/windows")
    public List<String> listWindows() {
        return windows.listWindows();
    }

    @PostMapping("/windows/focus")
    public Map<String, String> focusWindow(@RequestBody Map<String, String> body) {
        return result(windows.focusWindow(body.get("titleSubstring")));
    }

    @GetMapping("/windows/bounds")
    public Map<String, String> getWindowBounds(@RequestParam String titleSubstring) {
        return result(windows.getWindowBounds(titleSubstring));
    }

    @PostMapping("/windows/minimize")
    public Map<String, String> minimizeWindow(@RequestBody Map<String, String> body) {
        return result(windows.minimizeWindow(body.get("titleSubstring")));
    }

    @PostMapping("/windows/maximize")
    public Map<String, String> maximizeWindow(@RequestBody Map<String, String> body) {
        return result(windows.maximizeWindow(body.get("titleSubstring")));
    }

    @PostMapping("/windows/restore")
    public Map<String, String> restoreWindow(@RequestBody Map<String, String> body) {
        return result(windows.restoreWindow(body.get("titleSubstring")));
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    @PostMapping("/util/sleep")
    public Map<String, String> sleep(@RequestBody Map<String, Integer> body) {
        return result(robot.sleep(body.get("ms")));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Map<String, String> result(String message) {
        return Map.of("result", message);
    }

    private static Map<String, String> image(String base64) {
        return Map.of("data", base64, "mediaType", "image/png");
    }
}
