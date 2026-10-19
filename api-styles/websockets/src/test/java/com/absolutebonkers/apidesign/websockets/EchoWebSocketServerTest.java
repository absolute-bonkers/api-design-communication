package com.absolutebonkers.apidesign.websockets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EchoWebSocketServerTest {
    private EchoWebSocketServer server;
    private TestClient client;

    @BeforeEach
    void startServerAndClient() throws Exception {
        server = new EchoWebSocketServer(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        assertTrue(server.awaitStarted(5, TimeUnit.SECONDS));
        client = new TestClient(URI.create("ws://127.0.0.1:" + server.getPort()));
        assertTrue(client.connectBlocking(5, TimeUnit.SECONDS));
    }

    @AfterEach
    void stopServerAndClient() throws Exception {
        if (client != null) {
            client.closeBlocking();
        }
        if (server != null) {
            server.stop(2_000);
        }
    }

    @Test
    void exchangesMessagesBothWaysOnTheUpgradedConnection() throws Exception {
        assertEquals("connected", client.messages.poll(5, TimeUnit.SECONDS));

        client.send("hello");

        assertEquals("echo: hello", client.messages.poll(5, TimeUnit.SECONDS));
    }

    private static final class TestClient extends WebSocketClient {
        private final BlockingQueue<String> messages = new ArrayBlockingQueue<>(8);

        private TestClient(URI uri) {
            super(uri);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {}

        @Override
        public void onMessage(String message) {
            messages.offer(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {}

        @Override
        public void onError(Exception exception) {}
    }
}
