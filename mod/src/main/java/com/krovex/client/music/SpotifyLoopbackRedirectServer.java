package com.krovex.client.music;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Local loopback HTTP server for Spotify's OAuth redirect, duplicated (not shared)
 * from the launcher's identical {@code LoopbackRedirectServer} for the same reason
 * as {@link SpotifyPkceUtil}: the mod must authenticate with Spotify entirely on its
 * own, without the Krovex Launcher installed.
 */
final class SpotifyLoopbackRedirectServer implements AutoCloseable {

    private final HttpServer server;
    private final CompletableFuture<Map<String, String>> callbackFuture = new CompletableFuture<>();

    SpotifyLoopbackRedirectServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/krovex-spotify-callback", exchange -> {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            callbackFuture.complete(params);

            String html = params.containsKey("error")
                    ? "<html><body style='font-family:sans-serif;background:#121014;color:#f5f3f7;"
                      + "text-align:center;padding-top:80px'><h2>Spotify connection failed</h2>"
                      + "<p>" + escape(params.getOrDefault("error_description", params.get("error"))) + "</p>"
                      + "<p>You can close this window and return to Minecraft.</p></body></html>"
                    : "<html><body style='font-family:sans-serif;background:#121014;color:#f5f3f7;"
                      + "text-align:center;padding-top:80px'><h2>Spotify connected</h2>"
                      + "<p>You can close this window and return to Minecraft.</p></body></html>";

            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    int port() { return server.getAddress().getPort(); }
    String redirectUri() { return "http://127.0.0.1:" + port() + "/krovex-spotify-callback"; }

    Map<String, String> awaitCallback(long timeout, TimeUnit unit) throws Exception {
        return callbackFuture.get(timeout, unit);
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new LinkedHashMap<>();
        if (query == null) return result;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String key = java.net.URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public void close() { server.stop(0); }
}
