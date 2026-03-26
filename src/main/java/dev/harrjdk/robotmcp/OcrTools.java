package dev.harrjdk.robotmcp;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.function.Supplier;

@Component
public class OcrTools {

    private final RobotTools robotTools;
    private final WindowTools windowTools;
    private final ActionLog actionLog;
    private final Supplier<ITesseract> tesseractFactory;

    @Autowired
    public OcrTools(RobotTools robotTools,
                    WindowTools windowTools,
                    ActionLog actionLog,
                    @Value("${ocr.tessdata.path:C:/Program Files/Tesseract-OCR/tessdata}") String tessdataPath,
                    @Value("${ocr.language:eng}") String language) {
        this(robotTools, windowTools, actionLog, () -> {
            Tesseract t = new Tesseract();
            t.setDatapath(tessdataPath);
            t.setLanguage(language);
            return t;
        });
    }

    /** Package-private constructor for unit tests — accepts a pre-built Tesseract factory. */
    OcrTools(RobotTools robotTools, WindowTools windowTools, ActionLog actionLog,
             Supplier<ITesseract> tesseractFactory) {
        this.robotTools = robotTools;
        this.windowTools = windowTools;
        this.actionLog = actionLog;
        this.tesseractFactory = tesseractFactory;
    }

    @Tool(description = "Run OCR on the full virtual desktop (all monitors) and return the recognized text.")
    public String ocrScreen() throws Exception {
        String base64 = robotTools.captureScreenBase64();
        String text = runOcr(decodeImage(base64));
        return logged("OCR screen: " + summarize(text), text);
    }

    @Tool(description = "Run OCR on a region of the screen and return the recognized text.")
    public String ocrRegion(int x, int y, int width, int height) throws Exception {
        String base64 = robotTools.captureRegionBase64(x, y, width, height);
        String text = runOcr(decodeImage(base64));
        return logged(String.format("OCR region (%d,%d,%dx%d): %s", x, y, width, height, summarize(text)), text);
    }

    @Tool(description = "Run OCR on a specific monitor by index and return the recognized text. Use listMonitors to get available indices.")
    public String ocrMonitor(int monitorIndex) throws Exception {
        String base64 = robotTools.captureMonitorBase64(monitorIndex);
        String text = runOcr(decodeImage(base64));
        return logged(String.format("OCR monitor %d: %s", monitorIndex, summarize(text)), text);
    }

    @Tool(description = """
            Run OCR on a window matching the given title substring and return the recognized text. \
            Matches the first visible window whose title contains the given substring (case-insensitive).\
            """)
    public String ocrWindow(String titleSubstring) throws Exception {
        String base64 = windowTools.captureWindowBase64(titleSubstring);
        String text = runOcr(decodeImage(base64));
        return logged(String.format("OCR window '%s': %s", titleSubstring, summarize(text)), text);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String runOcr(BufferedImage image) throws Exception {
        return tesseractFactory.get().doOCR(image);
    }

    private static BufferedImage decodeImage(String base64) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(base64);
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private String logged(String logEntry, String result) {
        actionLog.add(logEntry);
        return result;
    }

    private static String summarize(String text) {
        if (text == null || text.isBlank()) return "(no text)";
        String trimmed = text.strip();
        return trimmed.length() > 60 ? trimmed.substring(0, 60) + "..." : trimmed;
    }
}
