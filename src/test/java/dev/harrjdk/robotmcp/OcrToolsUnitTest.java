package dev.harrjdk.robotmcp;

import net.sourceforge.tess4j.ITesseract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for OcrTools logic.
 * Uses the package-private constructor to inject mocked RobotTools, WindowTools,
 * and a mocked ITesseract factory, so no display, OS, or Tesseract install is required.
 */
class OcrToolsUnitTest {

    private RobotTools mockRobotTools;
    private WindowTools mockWindowTools;
    private ActionLog actionLog;
    private ITesseract mockTesseract;
    private OcrTools tools;

    private String tiny1x1Png;

    @BeforeEach
    void setUp() throws Exception {
        mockRobotTools = mock(RobotTools.class);
        mockWindowTools = mock(WindowTools.class);
        actionLog = new ActionLog();
        mockTesseract = mock(ITesseract.class);
        tools = new OcrTools(mockRobotTools, mockWindowTools, actionLog, () -> mockTesseract);

        // A valid 1×1 PNG encoded as base64 — returned by all capture mocks
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        tiny1x1Png = Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    // -------------------------------------------------------------------------
    // ocrScreen
    // -------------------------------------------------------------------------

    @Test
    void ocrScreen_returnsOcrText() throws Exception {
        when(mockRobotTools.captureScreenBase64()).thenReturn(tiny1x1Png);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn("hello world");

        assertThat(tools.ocrScreen()).isEqualTo("hello world");
    }

    @Test
    void ocrScreen_logsEntry() throws Exception {
        when(mockRobotTools.captureScreenBase64()).thenReturn(tiny1x1Png);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn("hello");

        tools.ocrScreen();

        assertThat(actionLog.getEntries()).anyMatch(e -> e.contains("OCR screen: hello"));
    }

    @Test
    void ocrScreen_blankText_logsNoText() throws Exception {
        when(mockRobotTools.captureScreenBase64()).thenReturn(tiny1x1Png);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn("   ");

        String result = tools.ocrScreen();

        assertThat(result).isBlank();
        assertThat(actionLog.getEntries()).anyMatch(e -> e.contains("OCR screen: (no text)"));
    }

    @Test
    void ocrScreen_ocrThrows_propagatesException() throws Exception {
        when(mockRobotTools.captureScreenBase64()).thenReturn(tiny1x1Png);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenThrow(new RuntimeException("native failure"));

        assertThatThrownBy(() -> tools.ocrScreen()).hasMessageContaining("native failure");
    }

    // -------------------------------------------------------------------------
    // ocrRegion
    // -------------------------------------------------------------------------

    @Test
    void ocrRegion_passesCorrectCoordinatesToCapture() throws Exception {
        when(mockRobotTools.captureRegionBase64(10, 20, 300, 400)).thenReturn(tiny1x1Png);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn("text");

        tools.ocrRegion(10, 20, 300, 400);

        verify(mockRobotTools).captureRegionBase64(10, 20, 300, 400);
    }

    @Test
    void ocrRegion_returnsOcrText() throws Exception {
        when(mockRobotTools.captureRegionBase64(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(tiny1x1Png);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn("region text");

        assertThat(tools.ocrRegion(0, 0, 100, 100)).isEqualTo("region text");
    }

    @Test
    void ocrRegion_logsCoordinates() throws Exception {
        when(mockRobotTools.captureRegionBase64(5, 10, 200, 150)).thenReturn(tiny1x1Png);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn("ok");

        tools.ocrRegion(5, 10, 200, 150);

        assertThat(actionLog.getEntries()).anyMatch(e -> e.contains("OCR region (5,10,200x150)"));
    }

    // -------------------------------------------------------------------------
    // ocrMonitor
    // -------------------------------------------------------------------------

    @Test
    void ocrMonitor_passesCorrectIndexToCapture() throws Exception {
        when(mockRobotTools.captureMonitorBase64(2)).thenReturn(tiny1x1Png);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn("monitor text");

        tools.ocrMonitor(2);

        verify(mockRobotTools).captureMonitorBase64(2);
    }

    @Test
    void ocrMonitor_returnsOcrText() throws Exception {
        when(mockRobotTools.captureMonitorBase64(0)).thenReturn(tiny1x1Png);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn("monitor text");

        assertThat(tools.ocrMonitor(0)).isEqualTo("monitor text");
    }

    @Test
    void ocrMonitor_logsMonitorIndex() throws Exception {
        when(mockRobotTools.captureMonitorBase64(1)).thenReturn(tiny1x1Png);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn("text");

        tools.ocrMonitor(1);

        assertThat(actionLog.getEntries()).anyMatch(e -> e.contains("OCR monitor 1:"));
    }

    // -------------------------------------------------------------------------
    // ocrWindow
    // -------------------------------------------------------------------------

    @Test
    void ocrWindow_passesCorrectTitleToCapture() throws Exception {
        when(mockWindowTools.captureWindowBase64("Notepad")).thenReturn(tiny1x1Png);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn("window text");

        tools.ocrWindow("Notepad");

        verify(mockWindowTools).captureWindowBase64("Notepad");
    }

    @Test
    void ocrWindow_returnsOcrText() throws Exception {
        when(mockWindowTools.captureWindowBase64(any())).thenReturn(tiny1x1Png);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn("window text");

        assertThat(tools.ocrWindow("Chrome")).isEqualTo("window text");
    }

    @Test
    void ocrWindow_logsTitle() throws Exception {
        when(mockWindowTools.captureWindowBase64("MyApp")).thenReturn(tiny1x1Png);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn("text");

        tools.ocrWindow("MyApp");

        assertThat(actionLog.getEntries()).anyMatch(e -> e.contains("OCR window 'MyApp'"));
    }

    // -------------------------------------------------------------------------
    // summarize — via log entries
    // -------------------------------------------------------------------------

    @Test
    void longText_fullTextReturnedButLogIsTruncated() throws Exception {
        String longText = "A".repeat(80);
        when(mockRobotTools.captureScreenBase64()).thenReturn(tiny1x1Png);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn(longText);

        String result = tools.ocrScreen();

        assertThat(result).isEqualTo(longText);
        assertThat(actionLog.getEntries()).anyMatch(e -> e.contains("..."));
    }

    @Test
    void exactlyMaxLengthText_notTruncatedInLog() throws Exception {
        String text = "A".repeat(60);
        when(mockRobotTools.captureScreenBase64()).thenReturn(tiny1x1Png);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn(text);

        tools.ocrScreen();

        assertThat(actionLog.getEntries()).anyMatch(e -> e.contains(text) && !e.contains("..."));
    }

    // -------------------------------------------------------------------------
    // Tesseract factory — new instance per call
    // -------------------------------------------------------------------------

    @Test
    void eachCallCreatesNewTesseractInstance() throws Exception {
        when(mockRobotTools.captureScreenBase64()).thenReturn(tiny1x1Png);
        when(mockTesseract.doOCR(any(BufferedImage.class))).thenReturn("text");

        ITesseract first = mock(ITesseract.class);
        ITesseract second = mock(ITesseract.class);
        when(first.doOCR(any(BufferedImage.class))).thenReturn("first");
        when(second.doOCR(any(BufferedImage.class))).thenReturn("second");

        int[] callCount = {0};
        ITesseract[] instances = {first, second};
        OcrTools multiTools = new OcrTools(mockRobotTools, mockWindowTools, actionLog,
                () -> instances[callCount[0]++]);

        assertThat(multiTools.ocrScreen()).isEqualTo("first");
        assertThat(multiTools.ocrScreen()).isEqualTo("second");
    }
}
