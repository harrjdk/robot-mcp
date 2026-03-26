package dev.harrjdk.robotmcp;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.OleAuto;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.util.Set;

/**
 * Package-private thin wrapper around the Windows UI Automation COM API.
 *
 * Interfaces targeted (all vtable indices derived from UIAutomationClient.h, Windows SDK):
 *   IUIAutomation       CLSID FF48DBA4 / IID 30CBE57D  (UIA2, Vista+)
 *   IUIAutomationElement           IID D22108AA
 *   IUIAutomationTreeWalker        IID 4042C4F3
 *   IUIAutomationValuePattern      IID A94CD8B1
 */
class Uia implements UiaApi, AutoCloseable {

    // -------------------------------------------------------------------------
    // COM identity
    // -------------------------------------------------------------------------

    private static final Guid.GUID CLSID =
            new Guid.GUID("{FF48DBA4-60EF-4201-AA87-54103EEF594E}");
    private static final Guid.GUID IID =
            new Guid.GUID("{30CBE57D-D9D0-452A-AB13-7AC5AC4825EE}");
    private static final int CLSCTX_INPROC_SERVER = 1;

    // -------------------------------------------------------------------------
    // IUIAutomation vtable indices  (IUnknown occupies 0–2)
    // -------------------------------------------------------------------------

    private static final int AUTO_ElementFromHandle = 6;  // (HWND, IUIAutomationElement**)
    private static final int AUTO_ElementFromPoint  = 7;  // (POINT, IUIAutomationElement**)
    private static final int AUTO_GetFocusedElement = 8;  // (IUIAutomationElement**)
    private static final int AUTO_ControlViewWalker = 14; // (IUIAutomationTreeWalker**)

    // -------------------------------------------------------------------------
    // IUIAutomationElement vtable indices
    // -------------------------------------------------------------------------

    private static final int EL_GetCurrentPattern   = 16; // (PATTERNID, IUnknown**)
    private static final int EL_LocalizedControlType= 25; // get_CurrentLocalizedControlType (BSTR*)
    private static final int EL_Name               = 27; // get_CurrentName                 (BSTR*)
    private static final int EL_IsEnabled          = 37; // get_CurrentIsEnabled            (BOOL*)
    private static final int EL_AutomationId       = 39; // get_CurrentAutomationId         (BSTR*)
    private static final int EL_IsOffscreen        = 57; // get_CurrentIsOffscreen          (BOOL*)
    private static final int EL_BoundingRect       = 67; // get_CurrentBoundingRectangle    (RECT*)

    // -------------------------------------------------------------------------
    // IUIAutomationTreeWalker vtable indices
    // -------------------------------------------------------------------------

    private static final int WALKER_FirstChild  = 4; // GetFirstChildElement  (IUIAutomationElement*, IUIAutomationElement**)
    private static final int WALKER_NextSibling = 6; // GetNextSiblingElement (IUIAutomationElement*, IUIAutomationElement**)

    // -------------------------------------------------------------------------
    // IUIAutomationValuePattern vtable indices
    // -------------------------------------------------------------------------

    private static final int VALUE_CurrentValue = 4; // get_CurrentValue (BSTR*)

    // -------------------------------------------------------------------------
    // IUIAutomationExpandCollapsePattern vtable indices
    // -------------------------------------------------------------------------

    private static final int ECP_Collapse         = 3; // Collapse()
    private static final int ECP_Expand           = 4; // Expand()
    private static final int ECP_CurrentState     = 6; // get_CurrentExpandCollapseState (int*)

    // -------------------------------------------------------------------------
    // IUIAutomationTextPattern vtable indices
    // -------------------------------------------------------------------------

    private static final int TP_DocumentRange     = 7; // get_DocumentRange (IUIAutomationTextRange**)

    // -------------------------------------------------------------------------
    // IUIAutomationTextRange vtable indices
    // -------------------------------------------------------------------------

    private static final int TR_GetText           = 12; // GetText(int maxLength, BSTR*)

    // -------------------------------------------------------------------------
    // UIA pattern IDs
    // -------------------------------------------------------------------------

    static final int UIA_ValuePatternId           = 10002;
    static final int UIA_ExpandCollapsePatternId  = 10005;
    static final int UIA_TextPatternId            = 10014;

    // -------------------------------------------------------------------------
    // Interactive control types (LocalizedControlType strings, en-US Windows)
    // -------------------------------------------------------------------------

    static final Set<String> INTERACTIVE_TYPES = Set.of(
            "button", "check box", "combo box", "edit", "hyperlink",
            "list item", "menu item", "radio button", "spinner",
            "tab item", "tree item"
    );

    // -------------------------------------------------------------------------
    // COM interface pointers
    // -------------------------------------------------------------------------

    private final Pointer pAuto;
    private final Pointer pWalker;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    Uia() throws Exception {
        // Declare this thread as part of the multi-threaded apartment.
        // S_FALSE (1) means already initialised on this thread — both are fine.
        int coInitHr = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED).intValue();
        if (coInitHr < 0)
            throw new Exception("CoInitializeEx failed: 0x" + Integer.toHexString(coInitHr));

        PointerByReference ppv = new PointerByReference();
        HRESULT hr = Ole32.INSTANCE.CoCreateInstance(CLSID, null, CLSCTX_INPROC_SERVER, IID, ppv);
        checkHr(hr, "CoCreateInstance(IUIAutomation)");
        pAuto = ppv.getValue();
        if (pAuto == null)
            throw new Exception("IUIAutomation pointer is null after CoCreateInstance");

        PointerByReference ppWalker = new PointerByReference();
        vtable(pAuto, AUTO_ControlViewWalker, ppWalker);
        pWalker = ppWalker.getValue();
        if (pWalker == null)
            throw new Exception("IUIAutomation::get_ControlViewWalker returned null");
    }

    // -------------------------------------------------------------------------
    // Element acquisition
    // -------------------------------------------------------------------------

    /** Returns the UIA element for the given native window handle value, or null. */
    public Pointer elementFromHwnd(long hwnd) {
        PointerByReference out = new PointerByReference();
        vtable(pAuto, AUTO_ElementFromHandle, new Pointer(hwnd), out);
        return out.getValue();
    }

    /** Returns the UIA element at the given screen point, or null. */
    public Pointer elementFromPoint(int x, int y) {
        // POINT is 8 bytes (two 32-bit ints). On x64 it is passed in a single
        // general-purpose register; JNA's ByValue annotation handles this correctly.
        POINT.ByValue pt = new POINT.ByValue();
        pt.x = x;
        pt.y = y;
        PointerByReference out = new PointerByReference();
        vtable(pAuto, AUTO_ElementFromPoint, pt, out);
        return out.getValue();
    }

    /** Returns the element that currently holds keyboard focus, or null. */
    public Pointer focusedElement() {
        PointerByReference out = new PointerByReference();
        vtable(pAuto, AUTO_GetFocusedElement, out);
        return out.getValue();
    }

    // -------------------------------------------------------------------------
    // Tree navigation  (caller must release returned pointers)
    // -------------------------------------------------------------------------

    public Pointer firstChild(Pointer el) {
        PointerByReference out = new PointerByReference();
        vtable(pWalker, WALKER_FirstChild, el, out);
        return out.getValue();
    }

    public Pointer nextSibling(Pointer el) {
        PointerByReference out = new PointerByReference();
        vtable(pWalker, WALKER_NextSibling, el, out);
        return out.getValue();
    }

    // -------------------------------------------------------------------------
    // Element properties
    // -------------------------------------------------------------------------

    public String getName(Pointer el)                 { return bstr(el, EL_Name); }
    public String getLocalizedControlType(Pointer el) { return bstr(el, EL_LocalizedControlType); }
    public String getAutomationId(Pointer el)         { return bstr(el, EL_AutomationId); }
    public boolean isEnabled(Pointer el)              { return bool(el, EL_IsEnabled); }
    boolean isOffscreen(Pointer el)                   { return bool(el, EL_IsOffscreen); }

    public RECT getBoundingRect(Pointer el) {
        RECT r = new RECT();
        // get_CurrentBoundingRectangle writes directly into the RECT structure.
        vtable(el, EL_BoundingRect, r);
        return r;
    }

    /**
     * Returns the ValuePattern's current value string, or null if the element
     * does not support IUIAutomationValuePattern.
     */
    public String getValue(Pointer el) {
        PointerByReference ppPattern = new PointerByReference();
        vtable(el, EL_GetCurrentPattern, UIA_ValuePatternId, ppPattern);
        Pointer pPattern = ppPattern.getValue();
        if (pPattern == null) return null;
        try {
            return bstr(pPattern, VALUE_CurrentValue);
        } finally {
            release(pPattern);
        }
    }

    // -------------------------------------------------------------------------
    // Locator-based search
    // -------------------------------------------------------------------------

    /**
     * Depth-first search of descendants of {@code root} for the first element
     * matching {@code locator}. Returns a new COM reference (caller must release),
     * or null if not found. Does not check root itself; does not release root.
     *
     * Locator formats:
     *   "Save"              — matches by Name, then AutomationId
     *   "btn_save"          — matches by AutomationId, then Name
     *   "button:Save"       — matches by LocalizedControlType + Name
     */
    public Pointer findByLocator(Pointer root, String locator) {
        Pointer child = firstChild(root);
        while (child != null) {
            Pointer next = nextSibling(child);
            if (matchesLocator(child, locator)) {
                releaseAll(next);  // discard remaining siblings
                return child;      // caller owns this reference
            }
            Pointer found = findByLocator(child, locator);
            release(child);
            if (found != null) {
                releaseAll(next);
                return found;
            }
            child = next;
        }
        return null;
    }

    boolean matchesLocator(Pointer el, String locator) {
        int colon = locator.indexOf(':');
        if (colon > 0) {
            String type = locator.substring(0, colon).trim().toLowerCase();
            String name = locator.substring(colon + 1).trim();
            return getLocalizedControlType(el).equalsIgnoreCase(type)
                && getName(el).equals(name);
        }
        // Try AutomationId first (stable), then Name (human-readable)
        String aid = getAutomationId(el);
        if (!aid.isEmpty() && aid.equals(locator)) return true;
        return getName(el).equals(locator);
    }

    // -------------------------------------------------------------------------
    // ExpandCollapse pattern
    // -------------------------------------------------------------------------

    /**
     * Expands or collapses the element. Returns a result string.
     * Returns an error message if the element does not support ExpandCollapsePattern.
     */
    public String expandCollapse(Pointer el, boolean expand) {
        PointerByReference ppPat = new PointerByReference();
        vtable(el, EL_GetCurrentPattern, UIA_ExpandCollapsePatternId, ppPat);
        Pointer pPat = ppPat.getValue();
        if (pPat == null)
            return "Element \"" + getName(el) + "\" does not support ExpandCollapse";
        try {
            vtable(pPat, expand ? ECP_Expand : ECP_Collapse);
            return (expand ? "Expanded: " : "Collapsed: ") + "\"" + getName(el) + "\"";
        } finally {
            release(pPat);
        }
    }

    /**
     * Returns the current expand/collapse state as a string:
     * "Collapsed", "Expanded", "PartiallyExpanded", "LeafNode", or "Unknown".
     */
    String getExpandCollapseState(Pointer el) {
        PointerByReference ppPat = new PointerByReference();
        vtable(el, EL_GetCurrentPattern, UIA_ExpandCollapsePatternId, ppPat);
        Pointer pPat = ppPat.getValue();
        if (pPat == null) return "Unknown";
        try {
            IntByReference state = new IntByReference();
            vtable(pPat, ECP_CurrentState, state);
            return switch (state.getValue()) {
                case 0 -> "Collapsed";
                case 1 -> "Expanded";
                case 2 -> "PartiallyExpanded";
                case 3 -> "LeafNode";
                default -> "Unknown(" + state.getValue() + ")";
            };
        } finally {
            release(pPat);
        }
    }

    // -------------------------------------------------------------------------
    // TextPattern — full text content
    // -------------------------------------------------------------------------

    /**
     * Returns the full text content of the element via IUIAutomationTextPattern,
     * or null if the element does not support TextPattern.
     * Uses maxLength = -1 (no limit).
     */
    public String getTextContent(Pointer el) {
        PointerByReference ppPat = new PointerByReference();
        vtable(el, EL_GetCurrentPattern, UIA_TextPatternId, ppPat);
        Pointer pPat = ppPat.getValue();
        if (pPat == null) return null;
        try {
            PointerByReference ppRange = new PointerByReference();
            vtable(pPat, TP_DocumentRange, ppRange);
            Pointer pRange = ppRange.getValue();
            if (pRange == null) return null;
            try {
                // GetText(int maxLength, BSTR* pRetVal) — pass -1 for unlimited
                PointerByReference pbr = new PointerByReference();
                vtable(pRange, TR_GetText, -1, pbr);
                Pointer raw = pbr.getValue();
                if (raw == null) return "";
                String text = raw.getWideString(0);
                OleAuto.INSTANCE.SysFreeString(new WTypes.BSTR(raw));
                return text != null ? text : "";
            } finally {
                release(pRange);
            }
        } finally {
            release(pPat);
        }
    }

    // -------------------------------------------------------------------------
    // Reference management
    // -------------------------------------------------------------------------

    /** Calls IUnknown::Release on the given interface pointer. */
    public void release(Pointer p) {
        if (p != null) vtableInt(p, 2);
    }

    /**
     * Releases {@code start} and every subsequent sibling reachable via nextSibling.
     * Used to clean up the tail of a sibling chain when returning early from a search.
     */
    private void releaseAll(Pointer start) {
        Pointer p = start;
        while (p != null) {
            Pointer next = nextSibling(p);
            release(p);
            p = next;
        }
    }

    @Override
    public void close() {
        release(pWalker);
        release(pAuto);
    }

    // -------------------------------------------------------------------------
    // Vtable dispatch helpers
    // -------------------------------------------------------------------------

    /**
     * Calls vtable[idx](pThis, args...).
     * JNA maps Java types to native types: Pointer→pointer, Integer→int32,
     * Structure→pointer-to-struct, PointerByReference→pointer-to-pointer.
     */
    static void vtable(Pointer pThis, int idx, Object... args) {
        vtableInt(pThis, idx, args);
    }

    static int vtableInt(Pointer pThis, int idx, Object... args) {
        Pointer fnPtr = pThis.getPointer(0)
                             .getPointer((long) idx * Native.POINTER_SIZE);
        Object[] all = new Object[args.length + 1];
        all[0] = pThis;
        System.arraycopy(args, 0, all, 1, args.length);
        return Function.getFunction(fnPtr).invokeInt(all);
    }

    // -------------------------------------------------------------------------
    // BSTR / BOOL property helpers
    // -------------------------------------------------------------------------

    /** Calls a vtable getter that returns BSTR*, reads and frees the string. */
    private static String bstr(Pointer el, int idx) {
        PointerByReference pbr = new PointerByReference();
        vtable(el, idx, pbr);
        Pointer raw = pbr.getValue();
        if (raw == null) return "";
        String s = raw.getWideString(0);
        OleAuto.INSTANCE.SysFreeString(new WTypes.BSTR(raw));
        return s != null ? s : "";
    }

    /** Calls a vtable getter that returns BOOL* (Win32 BOOL = 32-bit int). */
    private static boolean bool(Pointer el, int idx) {
        IntByReference pbr = new IntByReference();
        vtable(el, idx, pbr);
        return pbr.getValue() != 0;
    }

    private static void checkHr(HRESULT hr, String context) throws Exception {
        if (hr.intValue() < 0) // FAILED(hr): bit 31 set
            throw new Exception(context + " failed: 0x" + Integer.toHexString(hr.intValue()));
    }
}
