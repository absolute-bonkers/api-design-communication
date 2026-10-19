package com.absolutebonkers.apidesign.graphql;

import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import java.util.Map;
import java.util.Optional;

public final class BookGraphQl {
    private static final String SCHEMA = """
            type Query {
              book(id: ID!): Book
              books: [Book!]!
            }

            type Book {
              id: ID!
              title: String!
              author: String!
            }
            """;

    private final Map<String, Book> books = Map.of(
            "1", new Book("1", "Designing Data-Intensive Applications", "Martin Kleppmann"),
            "2", new Book("2", "A Philosophy of Software Design", "John Ousterhout"));
    private final GraphQL graphQL;

    public BookGraphQl() {
        DataFetcher<Optional<Book>> bookById = environment ->
                Optional.ofNullable(books.get(environment.getArgument("id")));
        DataFetcher<java.util.List<Book>> allBooks = environment -> books.values().stream()
                .sorted(java.util.Comparator.comparing(Book::id))
                .toList();

        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", type -> type
                        .dataFetcher("book", bookById)
                        .dataFetcher("books", allBooks))
                .build();
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse(SCHEMA), wiring);
        graphQL = GraphQL.newGraphQL(schema).build();
    }

    public graphql.ExecutionResult execute(String query, Map<String, Object> variables) {
        return graphQL.execute(graphql.ExecutionInput.newExecutionInput()
                .query(query)
                .variables(variables)
                .build());
    }
}
