package io.github.emiliasamaemt.bluemaprailway.web;

import io.github.emiliasamaemt.bluemaprailway.RailwayService;
import io.github.emiliasamaemt.bluemaprailway.PluginLog;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class AdminWebServer {

    private final JavaPlugin plugin;
    private final RailwayService railwayService;
    private final PluginLog log;
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread thread;

    public AdminWebServer(JavaPlugin plugin, RailwayService railwayService, PluginLog log) {
        this.plugin = plugin;
        this.railwayService = railwayService;
        this.log = log;
    }

    public void start() {
        if (running || !plugin.getConfig().getBoolean("admin-web.enabled", false)) {
            return;
        }

        String host = plugin.getConfig().getString("admin-web.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("admin-web.port", 8765);
        try {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(InetAddress.getByName(host), port));
            running = true;
            thread = new Thread(this::acceptLoop, "BlueMapRailway Admin Web");
            thread.setDaemon(true);
            thread.start();
            log.info("BlueMapRailway admin web started: http://" + host + ":" + port + "/");
        } catch (IOException exception) {
            log.warning("Failed to start admin web: " + exception.getMessage(), exception);
            stop();
        }
    }

    public void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
        thread = null;
    }

    private void acceptLoop() {
        while (running && serverSocket != null) {
            try {
                Socket socket = serverSocket.accept();
                Thread handler = new Thread(() -> handle(socket), "BlueMapRailway Admin Web Request");
                handler.setDaemon(true);
                handler.start();
            } catch (IOException exception) {
                if (running) {
                    log.warning("Admin web request failed: " + exception.getMessage());
                }
            }
        }
    }

    private void handle(Socket socket) {
        try (socket;
             BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
             BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream())) {
            Request request = readRequest(input);
            Response response = route(request);
            writeResponse(output, response);
        } catch (Exception exception) {
            log.warning("Admin web handler failed: " + exception.getMessage());
        }
    }

    private Response route(Request request) throws IOException {
        String path = request.path();
        if (request.method().equals("OPTIONS")) {
            return new Response(204, "text/plain; charset=utf-8", new byte[0]);
        }

        if (path.equals("/") || path.equals("/index.html")) {
            return resource("web/index.html", "text/html; charset=utf-8");
        }
        if (path.equals("/app.js")) {
            return resource("web/app.js", "text/javascript; charset=utf-8");
        }
        if (path.equals("/style.css")) {
            return resource("web/style.css", "text/css; charset=utf-8");
        }
        if (path.equals("/background.png")) {
            return background();
        }

        if (path.startsWith("/api/") && !authorized(request)) {
            return json(401, "{\"ok\":false,\"error\":\"unauthorized\"}");
        }

        if (path.equals("/api/state") && request.method().equals("GET")) {
            return json(200, railwayService.webStateJson());
        }
        if (path.equals("/api/route") && request.method().equals("POST")) {
            Map<String, Object> body = SimpleJson.object(SimpleJson.parse(request.body()));
            return json(200, railwayService.webSaveRoute(body));
        }
        if (path.equals("/api/station") && request.method().equals("POST")) {
            Map<String, Object> body = SimpleJson.object(SimpleJson.parse(request.body()));
            return json(200, railwayService.webSaveStation(body));
        }
        if (path.equals("/api/rescan") && request.method().equals("POST")) {
            railwayService.requestFullRescan();
            return json(200, "{\"ok\":true}");
        }

        return text(404, "Not found");
    }

    private boolean authorized(Request request) {
        String token = plugin.getConfig().getString("admin-web.token", "change-me");
        if (token == null || token.isBlank()) {
            return true;
        }

        String queryToken = request.query().get("token");
        String headerToken = request.headers().getOrDefault("x-bluemaprailway-token", "");
        return token.equals(queryToken) || token.equals(headerToken);
    }

    private Response resource(String name, String contentType) throws IOException {
        try (InputStream input = plugin.getResource(name)) {
            if (input == null) {
                return text(404, "Resource not found");
            }
            return new Response(200, contentType, input.readAllBytes());
        }
    }

    private Response background() throws IOException {
        String relative = plugin.getConfig().getString("admin-web.background.image", "admin-web/background.png");
        File file = new File(plugin.getDataFolder(), relative == null ? "admin-web/background.png" : relative);
        if (!file.isFile()) {
            return text(404, "Background image not found");
        }

        String contentType = file.getName().toLowerCase(Locale.ROOT).endsWith(".jpg") ||
                file.getName().toLowerCase(Locale.ROOT).endsWith(".jpeg")
                ? "image/jpeg"
                : "image/png";
        return new Response(200, contentType, Files.readAllBytes(file.toPath()));
    }

    private Request readRequest(BufferedInputStream input) throws IOException {
        String requestLine = readLine(input);
        if (requestLine == null || requestLine.isBlank()) {
            throw new IOException("Empty request");
        }

        String[] parts = requestLine.split(" ", 3);
        if (parts.length < 2) {
            throw new IOException("Invalid request");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        String line;
        while ((line = readLine(input)) != null && !line.isEmpty()) {
            int separator = line.indexOf(':');
            if (separator > 0) {
                headers.put(
                        line.substring(0, separator).trim().toLowerCase(Locale.ROOT),
                        line.substring(separator + 1).trim()
                );
            }
        }

        int length = 0;
        if (headers.containsKey("content-length")) {
            length = Integer.parseInt(headers.get("content-length"));
        }

        byte[] bodyBytes = input.readNBytes(length);
        String target = parts[1];
        String path = target;
        Map<String, String> query = new LinkedHashMap<>();
        int queryIndex = target.indexOf('?');
        if (queryIndex >= 0) {
            path = target.substring(0, queryIndex);
            query = parseQuery(target.substring(queryIndex + 1));
        }

        return new Request(parts[0].toUpperCase(Locale.ROOT), path, query, headers, new String(bodyBytes, StandardCharsets.UTF_8));
    }

    private String readLine(BufferedInputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) >= 0) {
            if (value == '\n') {
                break;
            }
            if (value != '\r') {
                buffer.write(value);
            }
        }

        if (value < 0 && buffer.size() == 0) {
            return null;
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private Map<String, String> parseQuery(String queryString) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : queryString.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String key = separator >= 0 ? pair.substring(0, separator) : pair;
            String value = separator >= 0 ? pair.substring(separator + 1) : "";
            result.put(urlDecode(key), urlDecode(value));
        }
        return result;
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private Response json(int status, String json) {
        return new Response(status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private Response text(int status, String text) {
        return new Response(status, "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8));
    }

    private void writeResponse(BufferedOutputStream output, Response response) throws IOException {
        output.write(("HTTP/1.1 " + response.status() + " " + reason(response.status()) + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Type: " + response.contentType() + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Length: " + response.body().length + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write("Cache-Control: no-store\r\n".getBytes(StandardCharsets.UTF_8));
        output.write("Access-Control-Allow-Origin: *\r\n".getBytes(StandardCharsets.UTF_8));
        output.write("Access-Control-Allow-Headers: Content-Type, X-BlueMapRailway-Token\r\n".getBytes(StandardCharsets.UTF_8));
        output.write("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n".getBytes(StandardCharsets.UTF_8));
        output.write("Connection: close\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        output.write(response.body());
        output.flush();
    }

    private String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 204 -> "No Content";
            case 401 -> "Unauthorized";
            case 404 -> "Not Found";
            default -> "Error";
        };
    }

    private record Request(
            String method,
            String path,
            Map<String, String> query,
            Map<String, String> headers,
            String body
    ) {
    }

    private record Response(int status, String contentType, byte[] body) {
    }
}
