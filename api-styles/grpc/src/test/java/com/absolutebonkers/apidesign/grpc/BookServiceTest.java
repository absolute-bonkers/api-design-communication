package com.absolutebonkers.apidesign.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.absolutebonkers.apidesign.grpc.v1.BookServiceGrpc;
import com.absolutebonkers.apidesign.grpc.v1.GetBookRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookServiceTest {
    private Server server;
    private ManagedChannel channel;
    private BookServiceGrpc.BookServiceBlockingStub stub;

    @BeforeEach
    void startServer() throws Exception {
        server = ServerBuilder.forPort(0).addService(new BookService()).build().start();
        channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                .usePlaintext()
                .build();
        stub = BookServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void stopServer() throws Exception {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void generatedStubMakesATypedUnaryCall() {
        var book = stub.getBook(GetBookRequest.newBuilder().setId(1).build());

        assertEquals("API Design", book.getTitle());
        assertEquals(1, book.getId());
    }

    @Test
    void missingBookUsesTheStandardNotFoundStatus() {
        StatusRuntimeException error = assertThrows(StatusRuntimeException.class,
                () -> stub.getBook(GetBookRequest.newBuilder().setId(404).build()));

        assertEquals(Status.Code.NOT_FOUND, error.getStatus().getCode());
    }
}
