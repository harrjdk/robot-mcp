package dev.harrjdk.robotmcp;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.RECT;

/**
 * Abstraction over the Windows UI Automation COM wrapper used by UiaTools.
 * Exists primarily to allow UiaTools to be unit-tested without a live COM server.
 */
interface UiaApi {
    Pointer elementFromHwnd(long hwnd);
    Pointer focusedElement();
    Pointer elementFromPoint(int x, int y);
    Pointer firstChild(Pointer el);
    Pointer nextSibling(Pointer el);
    String getName(Pointer el);
    String getLocalizedControlType(Pointer el);
    String getAutomationId(Pointer el);
    boolean isEnabled(Pointer el);
    RECT getBoundingRect(Pointer el);
    String getValue(Pointer el);
    Pointer findByLocator(Pointer root, String locator);
    String expandCollapse(Pointer el, boolean expand);
    String getTextContent(Pointer el);
    void release(Pointer p);
}
