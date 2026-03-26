package dev.harrjdk.robotmcp;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * MCP tools that use the Windows UI Automation API (via JNA COM vtable dispatch)
 * to read application structure and state without OCR or screen capture.
 */
@Component
public class UiaTools {

    private final ActionLog actionLog;
    private final Callable<UiaApi> uiaSupplier;
    private final Function<String, Long> hwndLookup;

    public UiaTools(ActionLog actionLog) {
        this.actionLog = actionLog;
        this.hwndLookup = this::requireHwnd;
        this.uiaSupplier = this::acquireUia;
    }

    /** Package-private for unit tests — accepts a pre-built UiaApi and hwnd resolver. */
    UiaTools(ActionLog actionLog, UiaApi uia, Function<String, Long> hwndLookup) {
        this.actionLog = actionLog;
        this.hwndLookup = hwndLookup;
        this.uiaSupplier = () -> uia;
    }

    // =========================================================================
    // Category 1 – Discovery / Tree Inspection
    // =========================================================================

    @Tool(description = """
            Dump the full UI Automation control tree of the first visible window whose title \
            contains the given substring. Each line shows the element's control type, name, \
            AutomationId, ValuePattern value (if supported), enabled state, and bounding \
            rectangle {x,y,width,height}. Indentation reflects nesting depth. \
            Use this to understand an application's UI structure before interacting with it.\
            """)
    public String dumpWindowTree(String titleSubstring) throws Exception {
        return withRoot(titleSubstring, (u, root) -> {
            StringBuilder sb = new StringBuilder();
            appendTree(u, root, sb, "", 0, 10);
            return logged("Dumped UIA tree: " + titleSubstring, sb.toString().stripTrailing());
        });
    }

    @Tool(description = """
            List all interactive UI elements — buttons, edits, checkboxes, combo boxes, \
            menu items, list items, radio buttons, tab items, spinners, hyperlinks, and \
            tree items — in the first visible window whose title contains the given substring. \
            Each line shows the element's control type, name, AutomationId, current value \
            (if applicable), enabled state, and bounding rectangle {x,y,width,height}. \
            Less verbose than dumpWindowTree; use this to discover what the model can act on.\
            """)
    public String listInteractiveElements(String titleSubstring) throws Exception {
        return withRoot(titleSubstring, (u, root) -> {
            StringBuilder sb = new StringBuilder();
            collectInteractive(u, root, sb);
            String result = sb.toString().stripTrailing();
            if (result.isEmpty()) result = "(no interactive elements found)";
            return logged("Listed interactive elements: " + titleSubstring, result);
        });
    }

    @Tool(description = """
            Return the name, control type, AutomationId, value, and bounding rectangle of \
            the UI element that currently holds keyboard focus, regardless of which window \
            it belongs to.\
            """)
    public String getFocusedElement() throws Exception {
        UiaApi u = uiaSupplier.call();
        Pointer el = u.focusedElement();
        if (el == null) return logged("No focused element");
        try {
            return logged("Got focused element", formatElement(u, el, "").strip());
        } finally {
            u.release(el);
        }
    }

    @Tool(description = """
            Return the UI Automation element at the given screen coordinates. Shows the \
            element's control type, name, AutomationId, value, enabled state, and bounding \
            rectangle. Useful for identifying what element a screenshot region corresponds to \
            without relying on OCR.\
            """)
    public String getElementAtPoint(int x, int y) throws Exception {
        UiaApi u = uiaSupplier.call();
        Pointer el = u.elementFromPoint(x, y);
        if (el == null) return logged("No element at (%d, %d)".formatted(x, y));
        try {
            return logged("Got element at (%d, %d)".formatted(x, y), formatElement(u, el, "").strip());
        } finally {
            u.release(el);
        }
    }

    // =========================================================================
    // Category 2 – Element lookup & state
    // =========================================================================

    @Tool(description = """
            Find the first UI element matching the given locator in the target window and \
            return its full state: control type, name, AutomationId, value, enabled state, \
            and bounding rectangle. \
            Locator: AutomationId (e.g. 'btn_ok'), Name (e.g. 'OK'), \
            or ControlType:Name (e.g. 'button:OK') to disambiguate duplicates.\
            """)
    public String findElement(String titleSubstring, String locator) throws Exception {
        return withElement(titleSubstring, locator, (u, el) ->
                logged("Found element '%s' in '%s'".formatted(locator, titleSubstring),
                       formatElement(u, el, "").strip()));
    }

    @Tool(description = """
            Return true if a UI element matching the given locator exists in the target window, \
            false otherwise. Faster than findElement when you only need to know whether an \
            element is present (e.g. to check if a dialog is open or a button has appeared). \
            Locator: AutomationId, Name, or ControlType:Name.\
            """)
    public boolean isElementPresent(String titleSubstring, String locator) throws Exception {
        long hwnd = hwndLookup.apply(titleSubstring);
        if (hwnd == 0) { logged("No window found matching: " + titleSubstring); return false; }
        UiaApi u = uiaSupplier.call();
        Pointer root = u.elementFromHwnd(hwnd);
        if (root == null) { logged("UIA element not available for: " + titleSubstring); return false; }
        try {
            Pointer el = u.findByLocator(root, locator);
            boolean present = el != null;
            if (present) u.release(el);
            logged("isElementPresent '%s' in '%s': %b".formatted(locator, titleSubstring, present));
            return present;
        } finally {
            u.release(root);
        }
    }

    @Tool(description = """
            Poll until a UI element matching the given locator appears in the target window, \
            or until timeoutMs elapses. Returns the element's full state when found, or a \
            timeout message. Use this after triggering an action that opens a dialog or \
            loads new content, instead of guessing with sleep(). \
            Locator: AutomationId, Name, or ControlType:Name.\
            """)
    public String waitForElement(String titleSubstring, String locator, int timeoutMs)
            throws Exception {
        long hwnd = hwndLookup.apply(titleSubstring);
        if (hwnd == 0) return logged("No window found matching: " + titleSubstring);
        UiaApi u = uiaSupplier.call();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Pointer root = u.elementFromHwnd(hwnd);
            if (root != null) {
                Pointer el = u.findByLocator(root, locator);
                u.release(root);
                if (el != null) {
                    String result = formatElement(u, el, "").strip();
                    u.release(el);
                    return logged("Found '%s' in '%s'".formatted(locator, titleSubstring), result);
                }
            }
            Thread.sleep(100);
        }
        return logged("Timeout: '%s' not found in '%s' after %dms"
                .formatted(locator, titleSubstring, timeoutMs));
    }

    // =========================================================================
    // Category 3 – ExpandCollapse
    // =========================================================================

    @Tool(description = """
            Expand a tree item, combo box, or other collapsible element in the target window. \
            Uses UIA ExpandCollapsePattern — more reliable than clicking the expand arrow by \
            coordinate. Locator: AutomationId, Name, or ControlType:Name.\
            """)
    public String expandElement(String titleSubstring, String locator) throws Exception {
        return withElement(titleSubstring, locator,
                (u, el) -> logged(u.expandCollapse(el, true)));
    }

    @Tool(description = """
            Collapse a tree item, combo box, or other expandable element in the target window. \
            Uses UIA ExpandCollapsePattern. \
            Locator: AutomationId, Name, or ControlType:Name.\
            """)
    public String collapseElement(String titleSubstring, String locator) throws Exception {
        return withElement(titleSubstring, locator,
                (u, el) -> logged(u.expandCollapse(el, false)));
    }

    // =========================================================================
    // Category 4 – Text content
    // =========================================================================

    @Tool(description = """
            Read the full text content of a UI element via UIA TextPattern. Works with \
            documents, rich text editors, terminals, log viewers, and other text containers \
            that expose more content than the simple value property. \
            Locator: AutomationId, Name, or ControlType:Name. \
            If the locator is empty, reads the entire window's text content.\
            """)
    public String getTextContent(String titleSubstring, String locator) throws Exception {
        return withRoot(titleSubstring, (u, root) -> {
            Pointer target = (locator == null || locator.isBlank())
                    ? root
                    : u.findByLocator(root, locator);
            if (target == null)
                return logged("Element not found: " + locator + " in " + titleSubstring);
            boolean releaseTarget = target != root;
            try {
                String text = u.getTextContent(target);
                if (text == null)
                    return logged("Element does not support TextPattern: " + locator);
                String label = (locator == null || locator.isBlank()) ? "(window)" : locator;
                return logged("Got text content of '%s' in '%s'".formatted(label, titleSubstring),
                              text);
            } finally {
                if (releaseTarget) u.release(target);
            }
        });
    }

    // =========================================================================
    // Tree walking
    // =========================================================================

    private void appendTree(UiaApi u, Pointer el, StringBuilder sb, String indent,
                            int depth, int maxDepth) {
        sb.append(formatElement(u, el, indent));
        if (depth >= maxDepth) {
            sb.append(indent).append("  [...]\n");
            return;
        }
        Pointer child = u.firstChild(el);
        while (child != null) {
            Pointer next = u.nextSibling(child);
            appendTree(u, child, sb, indent + "  ", depth + 1, maxDepth);
            u.release(child);
            child = next;
        }
    }

    private void collectInteractive(UiaApi u, Pointer el, StringBuilder sb) {
        if (Uia.INTERACTIVE_TYPES.contains(u.getLocalizedControlType(el).toLowerCase()))
            sb.append(formatElement(u, el, ""));
        Pointer child = u.firstChild(el);
        while (child != null) {
            Pointer next = u.nextSibling(child);
            collectInteractive(u, child, sb);
            u.release(child);
            child = next;
        }
    }

    // =========================================================================
    // Element formatting
    // =========================================================================

    private static String formatElement(UiaApi u, Pointer el, String indent) {
        String ct       = u.getLocalizedControlType(el);
        String name     = u.getName(el);
        String aid      = u.getAutomationId(el);
        boolean enabled = u.isEnabled(el);
        RECT r          = u.getBoundingRect(el);
        String value    = u.getValue(el);

        StringBuilder sb = new StringBuilder(indent)
                .append("[").append(ct).append("] ")
                .append("\"").append(name).append("\"")
                .append(" id=").append(aid);
        if (value != null)
            sb.append(" value='").append(value).append("'");
        return sb.append(" enabled=").append(enabled)
                 .append(" @ {").append(r.left).append(",").append(r.top).append(",")
                 .append(r.right - r.left).append(",").append(r.bottom - r.top).append("}\n")
                 .toString();
    }

    // =========================================================================
    // Boilerplate-eliminating helpers
    // =========================================================================

    @FunctionalInterface
    private interface RootAction {
        String apply(UiaApi u, Pointer root) throws Exception;
    }

    @FunctionalInterface
    private interface ElementAction {
        String apply(UiaApi u, Pointer el) throws Exception;
    }

    /** Acquires the root UIA element for the window and runs {@code action}, releasing on exit. */
    private String withRoot(String title, RootAction action) throws Exception {
        long hwnd = hwndLookup.apply(title);
        if (hwnd == 0) return logged("No window found matching: " + title);
        UiaApi u = uiaSupplier.call();
        Pointer root = u.elementFromHwnd(hwnd);
        if (root == null) return logged("UIA element not available for: " + title);
        try {
            return action.apply(u, root);
        } finally {
            u.release(root);
        }
    }

    /** Finds the element matching {@code locator} inside the window and runs {@code action}. */
    private String withElement(String title, String locator, ElementAction action)
            throws Exception {
        return withRoot(title, (u, root) -> {
            Pointer el = u.findByLocator(root, locator);
            if (el == null) return logged("Element not found: " + locator + " in " + title);
            try {
                return action.apply(u, el);
            } finally {
                u.release(el);
            }
        });
    }

    // =========================================================================
    // HWND lookup
    // =========================================================================

    private long requireHwnd(String titleSubstring) {
        HWND[] found = {null};
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            if (User32.INSTANCE.IsWindowVisible(hwnd)) {
                char[] buf = new char[512];
                User32.INSTANCE.GetWindowText(hwnd, buf, buf.length);
                if (Native.toString(buf).toLowerCase().contains(titleSubstring.toLowerCase())) {
                    found[0] = hwnd;
                    return false;
                }
            }
            return true;
        }, null);
        return found[0] == null ? 0 : Pointer.nativeValue(found[0].getPointer());
    }

    // =========================================================================
    // Uia lazy singleton (production only)
    // =========================================================================

    private volatile UiaApi _uia;

    private UiaApi acquireUia() throws Exception {
        if (_uia == null) {
            synchronized (this) {
                if (_uia == null) _uia = new Uia();
            }
        }
        // Ensure the calling thread is in the MTA — safe to call repeatedly.
        Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED);
        return _uia;
    }

    // =========================================================================
    // Logging
    // =========================================================================

    private String logged(String logEntry, String result) {
        actionLog.add(logEntry);
        return result;
    }

    private String logged(String message) {
        actionLog.add(message);
        return message;
    }
}
