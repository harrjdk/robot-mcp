package dev.harrjdk.robotmcp;

import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Thread-safe rolling log of tool actions, capped at MAX_ENTRIES.
 * A single listener (the control panel) is notified on each append.
 */
@Component
public class ActionLog {

    private static final int MAX_ENTRIES    = 200;
    private static final int MAX_LINE_LEN   = 120;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ArrayDeque<String> entries = new ArrayDeque<>();
    private Consumer<String> listener;

    public synchronized void add(String message) {
        String truncated = message.length() > MAX_LINE_LEN
                ? message.substring(0, MAX_LINE_LEN) + "…"
                : message;
        String entry = String.format("%s  %s", LocalTime.now().format(TIME_FMT), truncated);
        if (entries.size() >= MAX_ENTRIES) entries.removeFirst();
        entries.addLast(entry);
        if (listener != null) listener.accept(entry);
    }

    public synchronized void setListener(Consumer<String> listener) {
        this.listener = listener;
    }

    public synchronized List<String> getEntries() {
        return new ArrayList<>(entries);
    }
}
