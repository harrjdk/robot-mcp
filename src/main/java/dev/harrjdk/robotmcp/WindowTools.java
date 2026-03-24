package dev.harrjdk.robotmcp;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinUser;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WindowTools {

    private final RobotTools robotTools;
    private final ActionLog actionLog;

    public WindowTools(RobotTools robotTools, ActionLog actionLog) {
        this.robotTools = robotTools;
        this.actionLog = actionLog;
    }

    @Tool(description = "Get the title of the currently active (foreground) window.")
    public String getActiveWindowTitle() {
        HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null) {
            return logged("No active window");
        }
        return logged(getWindowTitle(hwnd));
    }

    @Tool(description = "List all visible top-level windows and their titles.")
    public List<String> listWindows() {
        List<String> titles = new ArrayList<>();
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            if (User32.INSTANCE.IsWindowVisible(hwnd)) {
                String title = getWindowTitle(hwnd);
                if (!title.isEmpty()) {
                    titles.add(title);
                }
            }
            return true;
        }, null);
        actionLog.add(String.format("Listed windows (%d found)", titles.size()));
        return titles;
    }

    @Tool(description = "Bring the first visible window whose title contains the given substring to the foreground. Returns the full title of the focused window, or an error message if not found.")
    public String focusWindow(String titleSubstring) {
        HWND hwnd = findVisibleHwnd(titleSubstring);
        if (hwnd == null) {
            return logged(String.format("No window found matching: %s", titleSubstring));
        }
        User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);

        // Attach only the calling thread to the foreground thread's input queue.
        // This is the minimal correct pattern — attaching the target thread or
        // using SetForegroundWindow alone are both insufficient on modern Windows.
        int callingThreadId    = Kernel32.INSTANCE.GetCurrentThreadId();
        int foregroundThreadId = User32.INSTANCE.GetWindowThreadProcessId(
                User32.INSTANCE.GetForegroundWindow(), null);
        boolean attached = callingThreadId != foregroundThreadId &&
                User32.INSTANCE.AttachThreadInput(
                        new DWORD(callingThreadId), new DWORD(foregroundThreadId), true);

        User32.INSTANCE.BringWindowToTop(hwnd);
        User32.INSTANCE.SetForegroundWindow(hwnd);
        User32.INSTANCE.SetFocus(hwnd);

        if (attached) {
            User32.INSTANCE.AttachThreadInput(
                    new DWORD(callingThreadId), new DWORD(foregroundThreadId), false);
        }
        return logged(String.format("Focused: %s", getWindowTitle(hwnd)));
    }

    @Tool(description = "Get the screen bounds (x, y, width, height) of the first visible window whose title contains the given substring.")
    public String getWindowBounds(String titleSubstring) {
        HWND hwnd = findVisibleHwnd(titleSubstring);
        if (hwnd == null) {
            return logged(String.format("No window found matching: %s", titleSubstring));
        }
        RECT rect = new RECT();
        User32.INSTANCE.GetWindowRect(hwnd, rect);
        return logged(String.format("x=%d, y=%d, width=%d, height=%d",
                rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top));
    }

    @Tool(description = "Minimize the first visible window whose title contains the given substring.")
    public String minimizeWindow(String titleSubstring) {
        return showWindowByTitle(titleSubstring, WinUser.SW_MINIMIZE, "Minimized");
    }

    @Tool(description = "Maximize the first visible window whose title contains the given substring.")
    public String maximizeWindow(String titleSubstring) {
        return showWindowByTitle(titleSubstring, WinUser.SW_MAXIMIZE, "Maximized");
    }

    @Tool(description = "Restore (un-minimize/un-maximize) the first visible window whose title contains the given substring.")
    public String restoreWindow(String titleSubstring) {
        return showWindowByTitle(titleSubstring, WinUser.SW_RESTORE, "Restored");
    }

    // -------------------------------------------------------------------------
    // Screen capture
    // -------------------------------------------------------------------------

    public String captureWindowBase64(String titleSubstring) throws Exception {
        HWND hwnd = findVisibleHwnd(titleSubstring);
        if (hwnd == null) {
            throw new Exception(String.format("No window found matching: %s", titleSubstring));
        }
        RECT rect = new RECT();
        User32.INSTANCE.GetWindowRect(hwnd, rect);
        // Minimized windows are parked at (-32000, -32000) — restore first
        if (rect.left == -32000 || rect.top == -32000) {
            User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);
            Thread.sleep(200);
            User32.INSTANCE.GetWindowRect(hwnd, rect);
        }
        actionLog.add(String.format("Captured window: %s", getWindowTitle(hwnd)));
        return robotTools.captureRegionBase64(rect.left, rect.top,
                rect.right - rect.left, rect.bottom - rect.top);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String logged(String result) {
        actionLog.add(result);
        return result;
    }

    private HWND findVisibleHwnd(String titleSubstring) {
        HWND[] found = {null};
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            if (User32.INSTANCE.IsWindowVisible(hwnd)) {
                if (getWindowTitle(hwnd).toLowerCase().contains(titleSubstring.toLowerCase())) {
                    found[0] = hwnd;
                    return false;
                }
            }
            return true;
        }, null);
        return found[0];
    }

    private String getWindowTitle(HWND hwnd) {
        char[] buf = new char[512];
        User32.INSTANCE.GetWindowText(hwnd, buf, buf.length);
        return Native.toString(buf);
    }

    private String showWindowByTitle(String titleSubstring, int showCmd, String verb) {
        HWND hwnd = findVisibleHwnd(titleSubstring);
        if (hwnd == null) {
            return logged(String.format("No window found matching: %s", titleSubstring));
        }
        User32.INSTANCE.ShowWindow(hwnd, showCmd);
        return logged(String.format("%s: %s", verb, getWindowTitle(hwnd)));
    }
}
