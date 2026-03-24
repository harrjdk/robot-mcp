package dev.harrjdk.robotmcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

@Component
public class RobotTools {

    private final Robot robot;
    private final ActionLog actionLog;
    private final int keyHoldMs;
    private final int mouseClickHoldMs;

    @Autowired
    public RobotTools(ActionLog actionLog,
                      @Value("${robot.keyHoldMs:30}") int keyHoldMs,
                      @Value("${robot.mouseClickHoldMs:30}") int mouseClickHoldMs) throws AWTException {
        this.robot = new Robot();
        this.robot.setAutoDelay(10);
        this.robot.setAutoWaitForIdle(true);
        this.actionLog = actionLog;
        this.keyHoldMs = keyHoldMs;
        this.mouseClickHoldMs = mouseClickHoldMs;
    }

    /** Package-private constructor for unit tests — accepts a pre-built (or mocked) Robot. */
    RobotTools(Robot robot) {
        this.robot = robot;
        this.actionLog = null;
        this.keyHoldMs = 0;
        this.mouseClickHoldMs = 0;
    }

    // -------------------------------------------------------------------------
    // Mouse
    // -------------------------------------------------------------------------

    @Tool(description = "Move the mouse cursor to the specified screen coordinates.")
    public String mouseMove(int x, int y) {
        moveMouse(x, y);
        return logged(String.format("Mouse moved to (%d, %d)", x, y));
    }

    @Tool(description = "Left-click at the specified screen coordinates.")
    public String leftClick(int x, int y) {
        moveMouse(x, y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        if (mouseClickHoldMs > 0) robot.delay(mouseClickHoldMs);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        return logged(String.format("Left clicked at (%d, %d)", x, y));
    }

    @Tool(description = "Double-click at the specified screen coordinates.")
    public String doubleClick(int x, int y) {
        moveMouse(x, y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        if (mouseClickHoldMs > 0) robot.delay(mouseClickHoldMs);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        if (mouseClickHoldMs > 0) robot.delay(mouseClickHoldMs);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        return logged(String.format("Double-clicked at (%d, %d)", x, y));
    }

    @Tool(description = "Right-click at the specified screen coordinates.")
    public String rightClick(int x, int y) {
        moveMouse(x, y);
        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        if (mouseClickHoldMs > 0) robot.delay(mouseClickHoldMs);
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
        return logged(String.format("Right clicked at (%d, %d)", x, y));
    }

    @Tool(description = "Middle-click at the specified screen coordinates. Useful for opening links in a new tab or closing tabs.")
    public String middleClick(int x, int y) {
        moveMouse(x, y);
        robot.mousePress(InputEvent.BUTTON2_DOWN_MASK);
        if (mouseClickHoldMs > 0) robot.delay(mouseClickHoldMs);
        robot.mouseRelease(InputEvent.BUTTON2_DOWN_MASK);
        return logged(String.format("Middle-clicked at (%d, %d)", x, y));
    }

    @Tool(description = "Scroll the mouse wheel at the specified coordinates. Positive amount scrolls down, negative scrolls up.")
    public String mouseScroll(int x, int y, int amount) {
        moveMouse(x, y);
        robot.mouseWheel(amount);
        return logged(String.format("Scrolled %d at (%d, %d)", amount, x, y));
    }

    @Tool(description = "Drag from (x1, y1) to (x2, y2) holding the left mouse button. Moves in smooth steps.")
    public String mouseDrag(int x1, int y1, int x2, int y2) {
        moveMouse(x1, y1);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        int steps = 20;
        for (int i = 1; i <= steps; i++) {
            robot.mouseMove(x1 + (x2 - x1) * i / steps, y1 + (y2 - y1) * i / steps);
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        return logged(String.format("Dragged from (%d, %d) to (%d, %d)", x1, y1, x2, y2));
    }

    // -------------------------------------------------------------------------
    // Keyboard
    // -------------------------------------------------------------------------

    @Tool(description = "Type a string as keyboard input. Handles uppercase letters and common special characters on a US keyboard layout.")
    public String typeText(String text) {
        List<Character> skipped = new ArrayList<>();
        for (char c : text.toCharArray()) {
            if (!typeChar(c)) {
                skipped.add(c);
            }
        }
        if (skipped.isEmpty()) {
            return logged(String.format("Typed: %s", text));
        }
        String skippedDesc = skipped.stream()
                .map(c -> String.format("'%c'", c))
                .collect(Collectors.joining(", "));
        return logged(String.format("Typed: %s (skipped %d unmappable character(s): %s)", text, skipped.size(), skippedDesc));
    }

    @Tool(description = """
            Press a single named key. Supported names: ENTER, TAB, SPACE, BACKSPACE, DELETE, ESCAPE, \
            UP, DOWN, LEFT, RIGHT, HOME, END, PAGE_UP, PAGE_DOWN, INSERT, PRINT_SCREEN, \
            CAPS_LOCK, NUM_LOCK, F1–F12, CTRL, ALT, SHIFT, WIN.\
            """)
    public String pressKey(String keyName) {
        Integer keyCode = resolveKeyCode(keyName.toUpperCase().trim());
        if (keyCode == null) {
            return logged(String.format("Unknown key: %s", keyName));
        }
        robot.keyPress(keyCode);
        if (keyHoldMs > 0) robot.delay(keyHoldMs);
        robot.keyRelease(keyCode);
        return logged(String.format("Pressed key: %s", keyName));
    }

    @Tool(description = """
            Press a key combination such as CTRL+C or CTRL+SHIFT+T. \
            Pass each key separated by '+'. Keys are pressed in order and released in reverse order. \
            Modifier names: CTRL, ALT, SHIFT, WIN.\
            """)
    public String pressKeyCombination(String combination) {
        String[] parts = combination.toUpperCase().split("\\+");
        int[] keyCodes = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            Integer kc = resolveKeyCode(parts[i].trim());
            if (kc == null) {
                return logged(String.format("Unknown key: %s", parts[i].trim()));
            }
            keyCodes[i] = kc;
        }
        for (int kc : keyCodes) {
            robot.keyPress(kc);
        }
        if (keyHoldMs > 0) robot.delay(keyHoldMs);
        for (int i = keyCodes.length - 1; i >= 0; i--) {
            robot.keyRelease(keyCodes[i]);
        }
        return logged(String.format("Pressed combination: %s", combination));
    }

    // -------------------------------------------------------------------------
    // Screen
    // -------------------------------------------------------------------------

    public String captureScreenBase64() throws Exception {
        Rectangle virtualBounds = new Rectangle();
        for (GraphicsDevice screen : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            virtualBounds = virtualBounds.union(screen.getDefaultConfiguration().getBounds());
        }
        BufferedImage image = robot.createScreenCapture(virtualBounds);
        if (actionLog != null) actionLog.add("Captured full screen");
        return encodeImage(image);
    }

    public String captureRegionBase64(int x, int y, int width, int height) throws Exception {
        BufferedImage image = robot.createScreenCapture(new Rectangle(x, y, width, height));
        if (actionLog != null) actionLog.add(String.format("Captured region (%d, %d, %dx%d)", x, y, width, height));
        return encodeImage(image);
    }

    public String captureMonitorBase64(int monitorIndex) throws Exception {
        GraphicsDevice[] screens = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        if (monitorIndex < 0 || monitorIndex >= screens.length) {
            throw new IllegalArgumentException(
                    String.format("Monitor index %d out of range (0–%d)", monitorIndex, screens.length - 1));
        }
        Rectangle bounds = screens[monitorIndex].getDefaultConfiguration().getBounds();
        BufferedImage image = robot.createScreenCapture(bounds);
        if (actionLog != null) actionLog.add(String.format("Captured monitor %d", monitorIndex));
        return encodeImage(image);
    }

    @Tool(description = "List all connected monitors with their index, resolution, and position.")
    public String listMonitors() {
        GraphicsDevice[] screens = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        GraphicsDevice primary = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < screens.length; i++) {
            Rectangle b = screens[i].getDefaultConfiguration().getBounds();
            boolean isPrimary = screens[i].getIDstring().equals(primary.getIDstring());
            sb.append(String.format("Monitor %d: %dx%d at (%d,%d)%s%n",
                    i, b.width, b.height, b.x, b.y, isPrimary ? " [primary]" : ""));
        }
        return logged(String.format("Listed %d monitor(s)", screens.length));
    }

    @Tool(description = "Get the color of the pixel at the specified screen coordinates. Returns the color as a hex string like #RRGGBB.")
    public String findPixelColor(int x, int y) {
        return logged(colorToHex(robot.getPixelColor(x, y)));
    }

    @Tool(description = """
            Wait until the pixel at (x, y) matches the target hex color (e.g. #FF0000 or FF0000), \
            or until timeoutMs elapses. Returns the outcome and the final pixel color.\
            """)
    public String waitForPixelColor(int x, int y, String hexColor, int timeoutMs) {
        String hex = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;
        Color target;
        try {
            target = new Color(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            return logged(String.format("Invalid color: %s", hexColor));
        }
        boolean matched = pollUntil(() -> robot.getPixelColor(x, y).getRGB() == target.getRGB(), timeoutMs);
        if (matched) {
            return logged(String.format("Matched %s at (%d, %d)", hexColor, x, y));
        }
        return logged(String.format("Timeout: pixel at (%d, %d) is %s, expected %s",
                x, y, colorToHex(robot.getPixelColor(x, y)), hexColor));
    }

    @Tool(description = """
            Wait up to timeoutMs milliseconds for any pixel change within the region (x, y, width, height). \
            Polls every 100ms. Returns when a change is detected or the timeout elapses.\
            """)
    public String waitForScreenChange(int x, int y, int width, int height, int timeoutMs) {
        BufferedImage before = robot.createScreenCapture(new Rectangle(x, y, width, height));
        boolean changed = pollUntil(
                () -> !imagesEqual(before, robot.createScreenCapture(new Rectangle(x, y, width, height))),
                timeoutMs);
        if (changed) {
            return logged(String.format("Screen changed in region (%d, %d, %dx%d)", x, y, width, height));
        }
        return logged(String.format("Timeout: no change detected in region (%d, %d, %dx%d)", x, y, width, height));
    }

    // -------------------------------------------------------------------------
    // Timing
    // -------------------------------------------------------------------------

    @Tool(description = "Pause for the specified number of milliseconds. Maximum 30000ms (30 seconds).")
    public String sleep(int ms) {
        int capped = Math.min(ms, 30_000);
        try {
            Thread.sleep(capped);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return logged(String.format("Slept %dms", capped));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String logged(String result) {
        if (actionLog != null) actionLog.add(result);
        return result;
    }

    private void moveMouse(int x, int y) {
        robot.mouseMove(x, y);
    }

    private static String colorToHex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private boolean pollUntil(BooleanSupplier condition, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return true;
            try { Thread.sleep(100); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private String encodeImage(BufferedImage image) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private boolean imagesEqual(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
        for (int i = 0; i < a.getWidth(); i++) {
            for (int j = 0; j < a.getHeight(); j++) {
                if (a.getRGB(i, j) != b.getRGB(i, j)) return false;
            }
        }
        return true;
    }

    private boolean typeChar(char c) {
        int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
        if (keyCode == KeyEvent.VK_UNDEFINED) {
            return false;
        }
        boolean needsShift = Character.isUpperCase(c)
                || "~!@#$%^&*()_+{}|:\"<>?".indexOf(c) >= 0;
        if (needsShift) {
            robot.keyPress(KeyEvent.VK_SHIFT);
        }
        robot.keyPress(keyCode);
        if (keyHoldMs > 0) robot.delay(keyHoldMs);
        robot.keyRelease(keyCode);
        if (needsShift) {
            robot.keyRelease(KeyEvent.VK_SHIFT);
        }
        return true;
    }

    private static final Map<String, Integer> KEY_MAP = Map.ofEntries(
            Map.entry("ENTER",        KeyEvent.VK_ENTER),
            Map.entry("TAB",          KeyEvent.VK_TAB),
            Map.entry("SPACE",        KeyEvent.VK_SPACE),
            Map.entry("BACKSPACE",    KeyEvent.VK_BACK_SPACE),
            Map.entry("DELETE",       KeyEvent.VK_DELETE),
            Map.entry("ESCAPE",       KeyEvent.VK_ESCAPE),
            Map.entry("ESC",          KeyEvent.VK_ESCAPE),
            Map.entry("UP",           KeyEvent.VK_UP),
            Map.entry("DOWN",         KeyEvent.VK_DOWN),
            Map.entry("LEFT",         KeyEvent.VK_LEFT),
            Map.entry("RIGHT",        KeyEvent.VK_RIGHT),
            Map.entry("HOME",         KeyEvent.VK_HOME),
            Map.entry("END",          KeyEvent.VK_END),
            Map.entry("PAGE_UP",      KeyEvent.VK_PAGE_UP),
            Map.entry("PAGE_DOWN",    KeyEvent.VK_PAGE_DOWN),
            Map.entry("INSERT",       KeyEvent.VK_INSERT),
            Map.entry("PRINT_SCREEN", KeyEvent.VK_PRINTSCREEN),
            Map.entry("CAPS_LOCK",    KeyEvent.VK_CAPS_LOCK),
            Map.entry("NUM_LOCK",     KeyEvent.VK_NUM_LOCK),
            Map.entry("CTRL",         KeyEvent.VK_CONTROL),
            Map.entry("ALT",          KeyEvent.VK_ALT),
            Map.entry("SHIFT",        KeyEvent.VK_SHIFT),
            Map.entry("WIN",          KeyEvent.VK_WINDOWS),
            Map.entry("F1",           KeyEvent.VK_F1),
            Map.entry("F2",           KeyEvent.VK_F2),
            Map.entry("F3",           KeyEvent.VK_F3),
            Map.entry("F4",           KeyEvent.VK_F4),
            Map.entry("F5",           KeyEvent.VK_F5),
            Map.entry("F6",           KeyEvent.VK_F6),
            Map.entry("F7",           KeyEvent.VK_F7),
            Map.entry("F8",           KeyEvent.VK_F8),
            Map.entry("F9",           KeyEvent.VK_F9),
            Map.entry("F10",          KeyEvent.VK_F10),
            Map.entry("F11",          KeyEvent.VK_F11),
            Map.entry("F12",          KeyEvent.VK_F12)
    );

    private Integer resolveKeyCode(String name) {
        if (KEY_MAP.containsKey(name)) {
            return KEY_MAP.get(name);
        }
        // Fall back to single character lookup (e.g. "A", "R", "1")
        // getExtendedKeyCodeForChar maps physical keys via lowercase, so lowercase before lookup
        if (name.length() == 1) {
            int code = KeyEvent.getExtendedKeyCodeForChar(Character.toLowerCase(name.charAt(0)));
            if (code != KeyEvent.VK_UNDEFINED) {
                return code;
            }
        }
        return null;
    }
}
