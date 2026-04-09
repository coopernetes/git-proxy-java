package org.finos.gitproxy.db;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.finos.gitproxy.db.mongo.MongoFetchStore;
import org.finos.gitproxy.db.mongo.MongoPushStore;

/**
 * Factory that creates MongoDB-backed stores sharing a single {@link MongoClient}. Callers that need both a
 * {@link PushStore} and a {@link FetchStore} should create one instance of this class so the underlying connection pool
 * is shared.
 */
public final class MongoStoreFactory implements AutoCloseable {

    private final MongoClient client;
    private final String databaseName;

    public MongoStoreFactory(String connectionString, String databaseName) {
        this.client = MongoClients.create(connectionString);
        this.databaseName = databaseName;
    }

    /** Create and initialize a {@link PushStore} backed by this factory's client. */
    public PushStore pushStore() {
        MongoPushStore store = new MongoPushStore(client, databaseName);
        store.initialize();
        return store;
    }

    /** Create and initialize a {@link FetchStore} backed by this factory's client. */
    public FetchStore fetchStore() {
        MongoFetchStore store = new MongoFetchStore(client, databaseName);
        store.initialize();
        return store;
    }

    @Override
    public void close() {
        client.close();
    }
}
