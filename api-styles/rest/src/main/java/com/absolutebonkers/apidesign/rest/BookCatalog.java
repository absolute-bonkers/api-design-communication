package com.absolutebonkers.apidesign.rest;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class BookCatalog {
    private final AtomicLong nextId = new AtomicLong(1);
    private final ConcurrentHashMap<Long, Book> books = new ConcurrentHashMap<>();

    public Book create(String title, String author) {
        var book = new Book(nextId.getAndIncrement(), title, author);
        books.put(book.id(), book);
        return book;
    }

    public Optional<Book> find(long id) {
        return Optional.ofNullable(books.get(id));
    }

    public List<Book> findAll() {
        return books.values().stream().sorted(Comparator.comparingLong(Book::id)).toList();
    }
}
