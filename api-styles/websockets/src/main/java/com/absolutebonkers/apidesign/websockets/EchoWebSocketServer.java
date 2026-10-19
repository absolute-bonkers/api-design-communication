package com.absolutebonkers.apidesign.websockets;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public final class EchoWebSocketServer extends WebSocketServer {
    private final CountDownLatch started = new CountDownLatch(1);

    public EchoWebSocketServer(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        connection.send("connected");
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {}

    @Override
    public void onMessage(WebSocket connection, String message) {
        connection.send("echo: " + message);
    }

    @Override
    public void onError(WebSocket connection, Exception exception) {
        System.getLogger(EchoWebSocketServer.class.getName())
                .log(System.Logger.Level.ERROR, "WebSocket transport error", exception);
        if (connection != null) {
            connection.close(1011, "internal server error");
        }
    }

    @Override
    public void onStart() {
        started.countDown();
    }

    public boolean awaitStarted(long timeout, TimeUnit unit) throws InterruptedException {
        return started.await(timeout, unit);
    }
}
