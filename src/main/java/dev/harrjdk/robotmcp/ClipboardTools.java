package dev.harrjdk.robotmcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

@Component
public class ClipboardTools {

    private final ActionLog actionLog;

    public ClipboardTools(ActionLog actionLog) {
        this.actionLog = actionLog;
    }

    @Tool(description = "Get the current text content of the system clipboard.")
    public String getClipboard() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable contents = clipboard.getContents(null);
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String text = (String) contents.getTransferData(DataFlavor.stringFlavor);
                actionLog.add(String.format("Read clipboard (%d chars)", text.length()));
                return text;
            }
            actionLog.add("Read clipboard (empty)");
            return "(clipboard is empty or contains non-text content)";
        } catch (Exception e) {
            return String.format("Failed to read clipboard: %s", e.getMessage());
        }
    }

    @Tool(description = "Set the system clipboard to the given text.")
    public String setClipboard(String text) {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(text), null);
            return logged(String.format("Clipboard set (%d chars)", text.length()));
        } catch (Exception e) {
            return String.format("Failed to set clipboard: %s", e.getMessage());
        }
    }

    private String logged(String result) {
        actionLog.add(result);
        return result;
    }
}
