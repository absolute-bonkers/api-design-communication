package com.absolutebonkers.apidesign.grpc;

import com.absolutebonkers.apidesign.grpc.v1.Book;
import com.absolutebonkers.apidesign.grpc.v1.BookServiceGrpc;
import com.absolutebonkers.apidesign.grpc.v1.GetBookRequest;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.Map;

public final class BookService extends BookServiceGrpc.BookServiceImplBase {
    private final Map<Long, Book> books = Map.of(
            1L, Book.newBuilder().setId(1).setTitle("API Design").setAuthor("Example").build(),
            2L, Book.newBuilder().setId(2).setTitle("Protocol Buffers").setAuthor("Example").build());

    @Override
    public void getBook(GetBookRequest request, StreamObserver<Book> responseObserver) {
        Book book = books.get(request.getId());
        if (book == null) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("No book with id " + request.getId())
                    .asRuntimeException());
            return;
        }
        responseObserver.onNext(book);
        responseObserver.onCompleted();
    }
}
