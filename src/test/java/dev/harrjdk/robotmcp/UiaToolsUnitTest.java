package dev.harrjdk.robotmcp;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.RECT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for UiaTools logic.
 * Uses the package-private constructor to inject a mocked UiaApi and a hwnd-lookup
 * lambda, so no Windows COM server, running windows, or UIA installation is required.
 */
class UiaToolsUnitTest {

    // Fake native pointers — non-zero longs so null-checks in production code pass.
    private static final long   FAKE_HWND = 12345L;
    private static final Pointer ROOT     = new Pointer(1L);
    private static final Pointer EL       = new Pointer(2L);

    private UiaApi    mockUia;
    private ActionLog actionLog;
    private UiaTools  tools;   // hwndLookup always returns FAKE_HWND

    @BeforeEach
    void setUp() {
        mockUia    = mock(UiaApi.class);
        actionLog  = new ActionLog();
        tools      = new UiaTools(actionLog, mockUia, title -> FAKE_HWND);

        // Shared lenient stubs — element property accessors
        RECT rect = new RECT();
        lenient().when(mockUia.elementFromHwnd(FAKE_HWND)).thenReturn(ROOT);
        lenient().when(mockUia.firstChild(any())).thenReturn(null);
        lenient().when(mockUia.nextSibling(any())).thenReturn(null);
        lenient().when(mockUia.getName(any())).thenReturn("TestElement");
        lenient().when(mockUia.getLocalizedControlType(any())).thenReturn("pane");
        lenient().when(mockUia.getAutomationId(any())).thenReturn("testId");
        lenient().when(mockUia.isEnabled(any())).thenReturn(true);
        lenient().when(mockUia.getBoundingRect(any())).thenReturn(rect);
        lenient().when(mockUia.getValue(any())).thenReturn(null);
    }

    // -------------------------------------------------------------------------
    // dumpWindowTree
    // -------------------------------------------------------------------------

    @Test
    void dumpWindowTree_noWindow_returnsMessage() throws Exception {
        UiaTools noWindow = new UiaTools(actionLog, mockUia, title -> 0L);
        assertThat(noWindow.dumpWindowTree("Missing")).contains("No window found matching");
    }

    @Test
    void dumpWindowTree_uiaElementUnavailable_returnsMessage() throws Exception {
        when(mockUia.elementFromHwnd(FAKE_HWND)).thenReturn(null);
        assertThat(tools.dumpWindowTree("App")).contains("UIA element not available");
    }

    @Test
    void dumpWindowTree_rootOnly_containsFormattedElement() throws Exception {
        when(mockUia.getName(ROOT)).thenReturn("MainWindow");
        when(mockUia.getLocalizedControlType(ROOT)).thenReturn("window");
        when(mockUia.getAutomationId(ROOT)).thenReturn("mainWnd");

        String result = tools.dumpWindowTree("App");

        assertThat(result).contains("[window]").contains("\"MainWindow\"").contains("id=mainWnd");
    }

    @Test
    void dumpWindowTree_logsEntry() throws Exception {
        tools.dumpWindowTree("MyApp");
        assertThat(actionLog.getEntries()).anyMatch(e -> e.contains("Dumped UIA tree: MyApp"));
    }

    @Test
    void dumpWindowTree_childElement_appearsIndented() throws Exception {
        when(mockUia.firstChild(ROOT)).thenReturn(EL);
        when(mockUia.firstChild(EL)).thenReturn(null);
        when(mockUia.getLocalizedControlType(EL)).thenReturn("button");
        when(mockUia.getName(EL)).thenReturn("OK");

        String result = tools.dumpWindowTree("App");

        // Child must appear indented (two leading spaces) relative to root
        assertThat(result).contains("  [button]").contains("\"OK\"");
    }

    // -------------------------------------------------------------------------
    // listInteractiveElements
    // -------------------------------------------------------------------------

    @Test
    void listInteractiveElements_noneFound_returnsEmptyMessage() throws Exception {
        // Root is a "pane" (not interactive); no children
        assertThat(tools.listInteractiveElements("App"))
                .isEqualTo("(no interactive elements found)");
    }

    @Test
    void listInteractiveElements_buttonChild_returnsElement() throws Exception {
        when(mockUia.firstChild(ROOT)).thenReturn(EL);
        when(mockUia.firstChild(EL)).thenReturn(null);
        when(mockUia.getLocalizedControlType(EL)).thenReturn("button");
        when(mockUia.getName(EL)).thenReturn("Submit");

        String result = tools.listInteractiveElements("App");

        assertThat(result).contains("[button]").contains("\"Submit\"");
    }

    @Test
    void listInteractiveElements_logsEntry() throws Exception {
        tools.listInteractiveElements("MyApp");
        assertThat(actionLog.getEntries())
                .anyMatch(e -> e.contains("Listed interactive elements: MyApp"));
    }

    // -------------------------------------------------------------------------
    // getFocusedElement
    // -------------------------------------------------------------------------

    @Test
    void getFocusedElement_noFocus_returnsMessage() throws Exception {
        when(mockUia.focusedElement()).thenReturn(null);
        assertThat(tools.getFocusedElement()).isEqualTo("No focused element");
    }

    @Test
    void getFocusedElement_hasFocus_returnsFormattedElement() throws Exception {
        when(mockUia.focusedElement()).thenReturn(EL);
        when(mockUia.getName(EL)).thenReturn("SearchBox");
        when(mockUia.getLocalizedControlType(EL)).thenReturn("edit");

        String result = tools.getFocusedElement();

        assertThat(result).contains("[edit]").contains("\"SearchBox\"");
    }

    @Test
    void getFocusedElement_releasesElementAfterUse() throws Exception {
        when(mockUia.focusedElement()).thenReturn(EL);
        tools.getFocusedElement();
        verify(mockUia).release(EL);
    }

    @Test
    void getFocusedElement_logsEntry() throws Exception {
        when(mockUia.focusedElement()).thenReturn(EL);
        tools.getFocusedElement();
        assertThat(actionLog.getEntries()).anyMatch(e -> e.contains("Got focused element"));
    }

    // -------------------------------------------------------------------------
    // getElementAtPoint
    // -------------------------------------------------------------------------

    @Test
    void getElementAtPoint_noElement_returnsMessage() throws Exception {
        when(mockUia.elementFromPoint(100, 200)).thenReturn(null);
        assertThat(tools.getElementAtPoint(100, 200)).contains("No element at (100, 200)");
    }

    @Test
    void getElementAtPoint_hasElement_returnsFormattedElement() throws Exception {
        when(mockUia.elementFromPoint(50, 75)).thenReturn(EL);
        when(mockUia.getName(EL)).thenReturn("CloseButton");

        String result = tools.getElementAtPoint(50, 75);

        assertThat(result).contains("\"CloseButton\"");
    }

    @Test
    void getElementAtPoint_releasesElement() throws Exception {
        when(mockUia.elementFromPoint(0, 0)).thenReturn(EL);
        tools.getElementAtPoint(0, 0);
        verify(mockUia).release(EL);
    }

    // -------------------------------------------------------------------------
    // findElement
    // -------------------------------------------------------------------------

    @Test
    void findElement_windowNotFound_returnsMessage() throws Exception {
        UiaTools noWindow = new UiaTools(actionLog, mockUia, title -> 0L);
        assertThat(noWindow.findElement("Missing", "btn_ok")).contains("No window found matching");
    }

    @Test
    void findElement_elementNotFound_returnsMessage() throws Exception {
        when(mockUia.findByLocator(ROOT, "btn_ok")).thenReturn(null);
        assertThat(tools.findElement("App", "btn_ok")).contains("Element not found");
    }

    @Test
    void findElement_found_returnsFormattedElement() throws Exception {
        when(mockUia.findByLocator(ROOT, "btn_ok")).thenReturn(EL);
        when(mockUia.getName(EL)).thenReturn("OK");
        when(mockUia.getLocalizedControlType(EL)).thenReturn("button");

        String result = tools.findElement("App", "btn_ok");

        assertThat(result).contains("[button]").contains("\"OK\"");
    }

    @Test
    void findElement_releasesRootAndElement() throws Exception {
        when(mockUia.findByLocator(ROOT, "btn_ok")).thenReturn(EL);
        tools.findElement("App", "btn_ok");
        verify(mockUia).release(EL);
        verify(mockUia).release(ROOT);
    }

    // -------------------------------------------------------------------------
    // isElementPresent
    // -------------------------------------------------------------------------

    @Test
    void isElementPresent_windowNotFound_returnsFalse() throws Exception {
        UiaTools noWindow = new UiaTools(actionLog, mockUia, title -> 0L);
        assertThat(noWindow.isElementPresent("Missing", "btn_ok")).isFalse();
    }

    @Test
    void isElementPresent_uiaUnavailable_returnsFalse() throws Exception {
        when(mockUia.elementFromHwnd(FAKE_HWND)).thenReturn(null);
        assertThat(tools.isElementPresent("App", "btn_ok")).isFalse();
    }

    @Test
    void isElementPresent_elementNotFound_returnsFalse() throws Exception {
        when(mockUia.findByLocator(ROOT, "btn_ok")).thenReturn(null);
        assertThat(tools.isElementPresent("App", "btn_ok")).isFalse();
    }

    @Test
    void isElementPresent_found_returnsTrue() throws Exception {
        when(mockUia.findByLocator(ROOT, "btn_ok")).thenReturn(EL);
        assertThat(tools.isElementPresent("App", "btn_ok")).isTrue();
    }

    @Test
    void isElementPresent_found_releasesElementAndRoot() throws Exception {
        when(mockUia.findByLocator(ROOT, "btn_ok")).thenReturn(EL);
        tools.isElementPresent("App", "btn_ok");
        verify(mockUia).release(EL);
        verify(mockUia).release(ROOT);
    }

    @Test
    void isElementPresent_logsResult() throws Exception {
        when(mockUia.findByLocator(ROOT, "btn_ok")).thenReturn(EL);
        tools.isElementPresent("App", "btn_ok");
        assertThat(actionLog.getEntries())
                .anyMatch(e -> e.contains("isElementPresent 'btn_ok' in 'App': true"));
    }

    // -------------------------------------------------------------------------
    // waitForElement
    // -------------------------------------------------------------------------

    @Test
    void waitForElement_windowNotFound_returnsMessage() throws Exception {
        UiaTools noWindow = new UiaTools(actionLog, mockUia, title -> 0L);
        assertThat(noWindow.waitForElement("Missing", "btn_ok", 5000))
                .contains("No window found matching");
    }

    @Test
    void waitForElement_foundImmediately_returnsElement() throws Exception {
        when(mockUia.elementFromHwnd(FAKE_HWND)).thenReturn(ROOT);
        when(mockUia.findByLocator(ROOT, "btn_ok")).thenReturn(EL);
        when(mockUia.getName(EL)).thenReturn("OK");
        when(mockUia.getLocalizedControlType(EL)).thenReturn("button");

        String result = tools.waitForElement("App", "btn_ok", 5000);

        assertThat(result).contains("[button]").contains("\"OK\"");
    }

    @Test
    void waitForElement_timeout_returnsTimeoutMessage() throws Exception {
        // timeoutMs=0 → deadline=now, loop condition false immediately
        when(mockUia.elementFromHwnd(anyLong())).thenReturn(null);
        String result = tools.waitForElement("App", "btn_ok", 0);
        assertThat(result).contains("Timeout").contains("btn_ok").contains("App");
    }

    @Test
    void waitForElement_logsFoundEntry() throws Exception {
        when(mockUia.elementFromHwnd(FAKE_HWND)).thenReturn(ROOT);
        when(mockUia.findByLocator(ROOT, "btn_ok")).thenReturn(EL);
        tools.waitForElement("App", "btn_ok", 5000);
        assertThat(actionLog.getEntries())
                .anyMatch(e -> e.contains("Found 'btn_ok' in 'App'"));
    }

    // -------------------------------------------------------------------------
    // expandElement / collapseElement
    // -------------------------------------------------------------------------

    @Test
    void expandElement_elementNotFound_returnsMessage() throws Exception {
        when(mockUia.findByLocator(ROOT, "tree_node")).thenReturn(null);
        assertThat(tools.expandElement("App", "tree_node")).contains("Element not found");
    }

    @Test
    void expandElement_found_delegatesToExpandCollapse() throws Exception {
        when(mockUia.findByLocator(ROOT, "tree_node")).thenReturn(EL);
        when(mockUia.expandCollapse(EL, true)).thenReturn("Expanded: \"Node\"");

        String result = tools.expandElement("App", "tree_node");

        assertThat(result).isEqualTo("Expanded: \"Node\"");
        verify(mockUia).expandCollapse(EL, true);
    }

    @Test
    void collapseElement_found_delegatesToExpandCollapse() throws Exception {
        when(mockUia.findByLocator(ROOT, "tree_node")).thenReturn(EL);
        when(mockUia.expandCollapse(EL, false)).thenReturn("Collapsed: \"Node\"");

        String result = tools.collapseElement("App", "tree_node");

        assertThat(result).isEqualTo("Collapsed: \"Node\"");
        verify(mockUia).expandCollapse(EL, false);
    }

    // -------------------------------------------------------------------------
    // getTextContent
    // -------------------------------------------------------------------------

    @Test
    void getTextContent_blankLocator_usesRoot() throws Exception {
        when(mockUia.getTextContent(ROOT)).thenReturn("Hello world");

        String result = tools.getTextContent("App", "");

        assertThat(result).isEqualTo("Hello world");
        verify(mockUia, never()).findByLocator(any(), any());
    }

    @Test
    void getTextContent_withLocator_findsElement() throws Exception {
        when(mockUia.findByLocator(ROOT, "editor")).thenReturn(EL);
        when(mockUia.getTextContent(EL)).thenReturn("Editor text");

        assertThat(tools.getTextContent("App", "editor")).isEqualTo("Editor text");
    }

    @Test
    void getTextContent_elementNotFound_returnsMessage() throws Exception {
        when(mockUia.findByLocator(ROOT, "editor")).thenReturn(null);
        assertThat(tools.getTextContent("App", "editor")).contains("Element not found");
    }

    @Test
    void getTextContent_noTextPattern_returnsMessage() throws Exception {
        when(mockUia.findByLocator(ROOT, "editor")).thenReturn(EL);
        when(mockUia.getTextContent(EL)).thenReturn(null);

        assertThat(tools.getTextContent("App", "editor"))
                .contains("does not support TextPattern");
    }

    @Test
    void getTextContent_blankLocator_logsWindowLabel() throws Exception {
        when(mockUia.getTextContent(ROOT)).thenReturn("some text");
        tools.getTextContent("App", "");
        assertThat(actionLog.getEntries())
                .anyMatch(e -> e.contains("(window)") && e.contains("App"));
    }

    @Test
    void getTextContent_withLocator_logsLocatorLabel() throws Exception {
        when(mockUia.findByLocator(ROOT, "editor")).thenReturn(EL);
        when(mockUia.getTextContent(EL)).thenReturn("text");
        tools.getTextContent("App", "editor");
        assertThat(actionLog.getEntries())
                .anyMatch(e -> e.contains("editor") && e.contains("App"));
    }
}
