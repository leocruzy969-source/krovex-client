package com.krovex.client.music;

/** A snapshot of what Spotify's "currently playing" endpoint reported. */
public record SpotifyTrack(
        String title,
        String artist,
        String albumArtUrl,
        boolean isPlaying,
        long progressMs,
        long durationMs
) {
    public static final SpotifyTrack NOTHING_PLAYING = new SpotifyTrack(null, null, null, false, 0, 0);

    public boolean hasTrack() { return title != null; }

    /** 83000 -> "1:23" (and "1:02:03" for tracks over an hour). */
    public static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        return s >= 3600 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** "1:00 - 2:14": where the song is now, and how long it is. Empty while the length is unknown. */
    public static String timeLabel(long progressMs, long durationMs) {
        return durationMs <= 0 ? "" : clock(Math.min(progressMs, durationMs)) + " - " + clock(durationMs);
    }

    public String timeLabel() { return timeLabel(progressMs, durationMs); }

    public float progressFraction() {
        if (durationMs <= 0) return 0f;
        return Math.max(0f, Math.min(1f, (float) progressMs / (float) durationMs));
    }
}
