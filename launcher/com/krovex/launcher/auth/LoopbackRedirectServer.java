package com.krovex.launcher.auth;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class LoopbackRedirectServer
implements AutoCloseable {
    private final HttpServer server;
    private final CompletableFuture<Map<String, String>> callbackFuture = new CompletableFuture();

    LoopbackRedirectServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/callback", exchange -> {
            Map<String, String> params = LoopbackRedirectServer.parseQuery(exchange.getRequestURI().getRawQuery());

            String html = params.containsKey("error") ? "<html><body style='font-family:sans-serif;background:#121014;color:#f5f3f7;text-align:center;padding-top:80px'><h2>Sign-in failed</h2><p>" + LoopbackRedirectServer.escape(params.getOrDefault("error_description", params.get("error"))) + "</p><p>You can close this window and try again in Krovex Launcher.</p></body></html>" : "<html><body style='font-family:sans-serif;background:#121014;color:#f5f3f7;text-align:center;padding-top:80px'><h2>Account selected</h2><p>Return to Krovex Launcher to finish Minecraft sign-in.</p></body></html>";
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody();){
                os.write(bytes);
            } finally {
                callbackFuture.complete(params);
            }
        });
        this.server.start();
    }

    int port() {
        return this.server.getAddress().getPort();
    }

    String redirectUri() {
        return "http://localhost:" + this.port() + "/callback";
    }

    Map<String, String> awaitCallback(long timeout, TimeUnit unit) throws Exception {
        return this.callbackFuture.get(timeout, unit);
    }

    private static Map<String, String> parseQuery(String query) {
        LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
        if (query == null) {
            return result;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf(61);
            if (eq < 0) continue;
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public void close() {
        this.server.stop(0);
    }
}


