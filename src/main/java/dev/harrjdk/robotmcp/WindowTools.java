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

    @Tool(description = "Get the title of the currently active (foreground) window.")
    public String getActiveWindowTitle() {
        HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null) {
            return "No active window";
        }
        char[] buf = new char[512];
        User32.INSTANCE.GetWindowText(hwnd, buf, buf.length);
        return Native.toString(buf);
    }

    @Tool(description = "List all visible top-level windows and their titles.")
    public List<String> listWindows() {
        List<String> titles = new ArrayList<>();
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            if (User32.INSTANCE.IsWindowVisible(hwnd)) {
                char[] buf = new char[512];
                User32.INSTANCE.GetWindowText(hwnd, buf, buf.length);
                String title = Native.toString(buf);
                if (!title.isEmpty()) {
                    titles.add(title);
                }
            }
            return true;
        }, null);
        return titles;
    }

    @Tool(description = "Bring the first visible window whose title contains the given substring to the foreground. Returns the full title of the focused window, or an error message if not found.")
    public String focusWindow(String titleSubstring) {
        String[] matched = {null};
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            if (User32.INSTANCE.IsWindowVisible(hwnd)) {
                char[] buf = new char[512];
                User32.INSTANCE.GetWindowText(hwnd, buf, buf.length);
                String title = Native.toString(buf);
                if (title.toLowerCase().contains(titleSubstring.toLowerCase())) {
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
                    matched[0] = title;
                    return false; // stop enumeration
                }
            }
            return true;
        }, null);
        if (matched[0] == null) {
            return "No window found matching: " + titleSubstring;
        }
        return "Focused: " + matched[0];
    }

    @Tool(description = "Get the screen bounds (x, y, width, height) of the first visible window whose title contains the given substring.")
    public String getWindowBounds(String titleSubstring) {
        String[] result = {null};
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            if (User32.INSTANCE.IsWindowVisible(hwnd)) {
                char[] buf = new char[512];
                User32.INSTANCE.GetWindowText(hwnd, buf, buf.length);
                String title = Native.toString(buf);
                if (title.toLowerCase().contains(titleSubstring.toLowerCase())) {
                    RECT rect = new RECT();
                    User32.INSTANCE.GetWindowRect(hwnd, rect);
                    int w = rect.right - rect.left;
                    int h = rect.bottom - rect.top;
                    result[0] = String.format("x=%d, y=%d, width=%d, height=%d",
                            rect.left, rect.top, w, h);
                    return false;
                }
            }
            return true;
        }, null);
        if (result[0] == null) {
            return "No window found matching: " + titleSubstring;
        }
        return result[0];
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
    // Helpers
    // -------------------------------------------------------------------------

    private String showWindowByTitle(String titleSubstring, int showCmd, String verb) {
        String[] matched = {null};
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            if (User32.INSTANCE.IsWindowVisible(hwnd)) {
                char[] buf = new char[512];
                User32.INSTANCE.GetWindowText(hwnd, buf, buf.length);
                String title = Native.toString(buf);
                if (title.toLowerCase().contains(titleSubstring.toLowerCase())) {
                    User32.INSTANCE.ShowWindow(hwnd, showCmd);
                    matched[0] = title;
                    return false;
                }
            }
            return true;
        }, null);
        if (matched[0] == null) {
            return "No window found matching: " + titleSubstring;
        }
        return verb + ": " + matched[0];
    }
}
