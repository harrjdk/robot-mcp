package dev.harrjdk.robotmcp;

import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class ControlPanelWindow implements ApplicationListener<ApplicationReadyEvent> {

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}")
    private String mcpEndpoint;

    private final ConfigurableApplicationContext context;
    private final WebMvcStreamableServerTransportProvider transportProvider;
    private final ActiveRequestTracker activeRequestTracker;

    // Single shared scheduler — shut down when the window closes
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();


    private static final Color BG_DARK    = new Color(22, 22, 22);
    private static final Color BG_MID     = new Color(30, 30, 30);
    private static final Color BG_ROW     = new Color(28, 28, 28);
    private static final Color FG_GREEN   = new Color(80, 220, 100);
    private static final Color FG_GREY    = new Color(120, 120, 120);
    private static final Color FG_LABEL   = new Color(160, 160, 160);
    private static final Color FG_MCP     = new Color(100, 180, 255);
    private static final Color FG_REST    = new Color(100, 220, 180);
    private static final Color FG_TOOLS   = new Color(255, 185, 80);
    private static final Color FG_HOVER   = new Color(220, 220, 220);

    public ControlPanelWindow(ConfigurableApplicationContext context,
                               WebMvcStreamableServerTransportProvider transportProvider,
                               ActiveRequestTracker activeRequestTracker) {
        this.context = context;
        this.transportProvider = transportProvider;
        this.activeRequestTracker = activeRequestTracker;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String base       = "http://localhost:" + serverPort;
        String mcpUrl     = base + mcpEndpoint;
        String restUrl    = base + "/api";
        String toolsUrl   = base + "/api/tools";

        ConcurrentHashMap<?, ?> sessions = resolveSessions();

        Frame frame = new Frame("Robot MCP");
        frame.setLayout(new BorderLayout(0, 0));
        frame.setSize(520, 220);
        frame.setResizable(false);
        frame.setBackground(BG_MID);

        // --- Top status bar ---
        Panel statusBar = new Panel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        statusBar.setBackground(BG_MID);

        Label statusDot = new Label("\u25CF  Running");
        statusDot.setForeground(FG_GREEN);
        statusDot.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        statusBar.add(statusDot);

        Label sessionLabel = new Label("0 connected");
        sessionLabel.setForeground(FG_LABEL);
        sessionLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        statusBar.add(sessionLabel);

        Label activeLabel = new Label("");
        activeLabel.setForeground(FG_LABEL);
        activeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        statusBar.add(activeLabel);

        if (sessions != null) {
            scheduler.scheduleAtFixedRate(() -> {
                int count = sessions.size();
                sessionLabel.setText(count + " connected");
                sessionLabel.setForeground(count > 0 ? FG_GREEN : FG_LABEL);
            }, 0, 1, TimeUnit.SECONDS);
        }

        activeLabel.setVisible(false);
        scheduler.scheduleAtFixedRate(() -> {
            int active = activeRequestTracker.getActiveCount();
            if (active > 0) {
                activeLabel.setText("\u25CF " + active + " active request" + (active == 1 ? "" : "s"));
                activeLabel.setVisible(true);
            } else {
                activeLabel.setVisible(false);
            }
        }, 0, 500, TimeUnit.MILLISECONDS);

        // --- Endpoint rows ---
        Panel endpointPanel = new Panel(new GridLayout(3, 1, 0, 1));
        endpointPanel.setBackground(BG_DARK);
        endpointPanel.add(endpointRow("MCP",        "Streamable HTTP",      mcpUrl,   FG_MCP));
        endpointPanel.add(endpointRow("REST",        "HTTP API",             restUrl,  FG_REST));
        endpointPanel.add(endpointRow("OpenAI Tools","Function call schema", toolsUrl, FG_TOOLS));

        // --- Bottom bar ---
        Panel bottomBar = new Panel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        bottomBar.setBackground(BG_MID);

        Label activeWarningLabel = new Label("");
        activeWarningLabel.setForeground(new Color(255, 185, 80));
        activeWarningLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        activeWarningLabel.setVisible(false);
        bottomBar.add(activeWarningLabel);

        Button stopButton = new Button("Stop Server");
        stopButton.setBackground(new Color(160, 50, 50));
        stopButton.setForeground(Color.WHITE);
        stopButton.addActionListener(e -> {
            int active = activeRequestTracker.getActiveCount();
            if (active > 0) {
                activeWarningLabel.setText(active + " request" + (active == 1 ? "" : "s") + " still active — click again to force stop");
                activeWarningLabel.setVisible(true);
                stopButton.setLabel("Force Stop");
                stopButton.addActionListener(ignored -> shutdown(frame));
            } else {
                shutdown(frame);
            }
        });
        bottomBar.add(stopButton);

        frame.add(statusBar,    BorderLayout.NORTH);
        frame.add(endpointPanel, BorderLayout.CENTER);
        frame.add(bottomBar,    BorderLayout.SOUTH);

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                shutdown(frame);
            }
        });

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private Panel endpointRow(String type, String hint, String url, Color urlColor) {
        Panel row = new Panel(new BorderLayout(0, 0));
        row.setBackground(BG_ROW);

        // Left: type badge
        Panel left = new Panel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        left.setBackground(BG_ROW);

        Label typeLabel = new Label(type);
        typeLabel.setForeground(FG_GREY);
        typeLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        left.add(typeLabel);

        Label hintLabel = new Label(hint);
        hintLabel.setForeground(new Color(70, 70, 70));
        hintLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        left.add(hintLabel);

        // Right: clickable URL
        Panel right = new Panel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        right.setBackground(BG_ROW);

        Label urlLabel = new Label(url);
        urlLabel.setForeground(urlColor);
        urlLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        urlLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        urlLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Toolkit.getDefaultToolkit()
                        .getSystemClipboard()
                        .setContents(new StringSelection(url), null);
                urlLabel.setText("Copied!");
                urlLabel.setForeground(FG_GREEN);
                scheduler.schedule(() -> {
                    urlLabel.setText(url);
                    urlLabel.setForeground(urlColor);
                }, 1500, TimeUnit.MILLISECONDS);
            }

            @Override
            public void mouseEntered(MouseEvent e) { urlLabel.setForeground(FG_HOVER); }

            @Override
            public void mouseExited(MouseEvent e)  { urlLabel.setForeground(urlColor); }
        });

        right.add(urlLabel);

        row.add(left,  BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private void shutdown(Frame frame) {
        scheduler.shutdownNow();
        frame.dispose();
        context.close();
    }

    /**
     * Reads the active session count via reflection.
     * This is a workaround — Spring AI MCP does not currently expose a public API for session count.
     * If WebMvcStreamableServerTransportProvider renames or removes the "sessions" field, this will
     * return null and the session counter will simply not update.
     */
    private ConcurrentHashMap<?, ?> resolveSessions() {
        try {
            Field field = WebMvcStreamableServerTransportProvider.class.getDeclaredField("sessions");
            field.setAccessible(true);
            return (ConcurrentHashMap<?, ?>) field.get(transportProvider);
        } catch (Exception e) {
            return null;
        }
    }
}
