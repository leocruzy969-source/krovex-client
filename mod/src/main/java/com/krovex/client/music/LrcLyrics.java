package com.krovex.client.music;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a standard `.lrc` synced-lyrics file (section 3: "let me import a synced
 * .lrc file"). Format: lines like `[01:23.45]Some lyric text`, optionally several
 * timestamps sharing one line (`[00:01.00][00:15.00]Chorus`).
 */
public final class LrcLyrics {

    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    public record Line(long timestampMs, String text) {}

    private final List<Line> lines; // sorted ascending by timestamp

    private LrcLyrics(List<Line> lines) {
        this.lines = lines;
    }

    public static LrcLyrics parse(String lrcContent) {
        List<Line> result = new ArrayList<>();
        for (String rawLine : lrcContent.split("\\r?\\n")) {
            Matcher matcher = TIMESTAMP.matcher(rawLine);
            List<Long> timestamps = new ArrayList<>();
            int lastEnd = 0;
            while (matcher.find()) {
                long minutes = Long.parseLong(matcher.group(1));
                long seconds = Long.parseLong(matcher.group(2));
                String fraction = matcher.group(3);
                long millis = fraction == null ? 0 : (long) (Double.parseDouble("0." + fraction) * 1000);
                timestamps.add(minutes * 60_000 + seconds * 1000 + millis);
                lastEnd = matcher.end();
            }
            if (timestamps.isEmpty()) continue; // metadata line ([ar:...], [ti:...]) or blank -- not a lyric line
            String text = rawLine.substring(lastEnd).trim();
            for (long ts : timestamps) result.add(new Line(ts, text));
        }
        result.sort((a, b) -> Long.compare(a.timestampMs(), b.timestampMs()));
        return new LrcLyrics(result);
    }

    public static LrcLyrics loadFromFile(Path file) throws IOException {
        return parse(Files.readString(file));
    }

    public boolean isEmpty() { return lines.isEmpty(); }

    /** The lyric line active at a given playback position, or empty string if before the first line. */
    public String lineAt(long playbackPositionMs) {
        String current = "";
        for (Line line : lines) {
            if (line.timestampMs() > playbackPositionMs) break;
            current = line.text();
        }
        return current;
    }

    public List<Line> lines() { return List.copyOf(lines); }

    public int size() { return lines.size(); }

    /** Index of the line playing at this position, or -1 before the first line. */
    public int indexAt(long playbackPositionMs) {
        int low = 0, high = lines.size() - 1, found = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).timestampMs() <= playbackPositionMs) { found = mid; low = mid + 1; } else high = mid - 1;
        }
        return found;
    }

    public String text(int index) { return index < 0 || index >= lines.size() ? "" : lines.get(index).text(); }

    public long startMs(int index) { return index < 0 ? 0 : lines.get(Math.min(index, lines.size() - 1)).timestampMs(); }

    /** When a line stops being current: the next line's start (or a few seconds after the last line). */
    public long endMs(int index) {
        if (index < 0) return lines.isEmpty() ? 0 : lines.get(0).timestampMs();
        return index + 1 < lines.size() ? lines.get(index + 1).timestampMs() : lines.get(index).timestampMs() + 5_000;
    }
}
