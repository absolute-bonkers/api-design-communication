package com.absolutebonkers.apidesign.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BookApiServer implements AutoCloseable {
    private static final String COLLECTION_PATH = "/api/v1/books";
    private static final int MAX_BODY_BYTES = 16_384;

    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final BookCatalog catalog;
    private final ObjectMapper json = new ObjectMapper();

    public BookApiServer(BookCatalog catalog) throws IOException {
        this.catalog = catalog;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(COLLECTION_PATH, this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public URI baseUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + COLLECTION_PATH);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getRawPath();
            boolean collection = path.equals(COLLECTION_PATH) || path.equals(COLLECTION_PATH + "/");
            if (!acceptsJson(exchange.getRequestHeaders().getFirst("Accept"))) {
                send(exchange, 406, Map.of("error", "Only application/json is available"));
                return;
            }

            if (collection && exchange.getRequestMethod().equals("GET")) {
                send(exchange, 200, catalog.findAll());
            } else if (collection && exchange.getRequestMethod().equals("POST")) {
                createBook(exchange);
            } else if (!collection && path.startsWith(COLLECTION_PATH + "/")
                    && exchange.getRequestMethod().equals("GET")) {
                findBook(exchange, path.substring(COLLECTION_PATH.length() + 1));
            } else if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.getResponseHeaders().set("Allow", collection ? "GET, POST, OPTIONS" : "GET, OPTIONS");
                exchange.sendResponseHeaders(204, -1);
            } else if (path.equals(COLLECTION_PATH) || path.equals(COLLECTION_PATH + "/")
                    || path.startsWith(COLLECTION_PATH + "/")) {
                exchange.getResponseHeaders().set("Allow", collection ? "GET, POST, OPTIONS" : "GET, OPTIONS");
                send(exchange, 405, Map.of("error", "Method not allowed"));
            } else {
                send(exchange, 404, Map.of("error", "Resource not found"));
            }
        } finally {
            exchange.close();
        }
    }

    private void createBook(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (!isJsonContentType(contentType)) {
            send(exchange, 415, Map.of("error", "Content-Type must be application/json"));
            return;
        }

        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            send(exchange, 413, Map.of("error", "Request body is too large"));
            return;
        }

        CreateBookRequest request;
        try {
            request = json.readValue(body, CreateBookRequest.class);
        } catch (JsonProcessingException exception) {
            send(exchange, 400, Map.of("error", "Request body must be valid JSON"));
            return;
        }
        String title = request.title() == null ? null : request.title().trim();
        String author = request.author() == null ? null : request.author().trim();
        if (title == null || title.isBlank() || title.length() > 200
                || author == null || author.isBlank() || author.length() > 120) {
            send(exchange, 422, Map.of("error", "Title (1-200 chars) and author (1-120 chars) are required"));
            return;
        }

        Book created = catalog.create(title, author);
        exchange.getResponseHeaders().set("Location", baseUri() + "/" + created.id());
        send(exchange, 201, created);
    }

    private void findBook(HttpExchange exchange, String rawId) throws IOException {
        if (!rawId.matches("[0-9]+")) {
            send(exchange, 400, Map.of("error", "Book id must be a positive integer"));
            return;
        }
        long id;
        try {
            id = Long.parseLong(rawId);
        } catch (NumberFormatException exception) {
            send(exchange, 400, Map.of("error", "Book id is outside the supported range"));
            return;
        }
        if (id < 1) {
            send(exchange, 400, Map.of("error", "Book id must be a positive integer"));
            return;
        }
        var book = catalog.find(id);
        if (book.isPresent()) {
            send(exchange, 200, book.get());
        } else {
            send(exchange, 404, Map.of("error", "Book not found"));
        }
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] response = json.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
    }

    private static boolean acceptsJson(String accept) {
        if (accept == null || accept.isBlank()) {
            return true;
        }
        return List.of(accept.split(",")).stream()
                .map(value -> value.split(";", 2))
                .anyMatch(parts -> (parts[0].trim().equalsIgnoreCase("application/json")
                                || parts[0].trim().equalsIgnoreCase("application/*")
                                || parts[0].trim().equals("*/*"))
                        && qualityIsPositive(parts));
    }

    private static boolean qualityIsPositive(String[] parts) {
        if (parts.length == 1) {
            return true;
        }
        for (String parameter : parts[1].split(";")) {
            String[] pair = parameter.trim().split("=", 2);
            if (pair.length == 2 && pair[0].equalsIgnoreCase("q")) {
                try {
                    double quality = Double.parseDouble(pair[1]);
                    return quality > 0 && quality <= 1;
                } catch (NumberFormatException exception) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isJsonContentType(String contentType) {
        return contentType != null
                && contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json");
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
