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

    @Tool(description = "Get the current text content of the system clipboard.")
    public String getClipboard() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable contents = clipboard.getContents(null);
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return (String) contents.getTransferData(DataFlavor.stringFlavor);
            }
            return "(clipboard is empty or contains non-text content)";
        } catch (Exception e) {
            return "Failed to read clipboard: " + e.getMessage();
        }
    }

    @Tool(description = "Set the system clipboard to the given text.")
    public String setClipboard(String text) {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(text), null);
            return "Clipboard set (" + text.length() + " chars)";
        } catch (Exception e) {
            return "Failed to set clipboard: " + e.getMessage();
        }
    }
}
