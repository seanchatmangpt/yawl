package org.yawlfoundation.yawl.integration.zai;

import ai.z.openapi.ZaiClient;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe singleton factory for the official Z.AI SDK client.
 *
 * <p>Reads {@code ZAI_API_KEY} from environment automatically via
 * {@code ZaiClient.builder().ofZAI().build()}.
 */
public final class ZaiClientFactory {

    private static volatile ZaiClient instance;
    private static final ReentrantLock _classLock = new ReentrantLock();

    private ZaiClientFactory() {}

    /** Returns the shared ZaiClient instance, creating it on first call. */
    public static ZaiClient getInstance() {
        if (instance == null) {
            _classLock.lock();
            try {
                if (instance == null) {
                    instance = ZaiClient.builder().ofZAI().build();
                }
            } finally {
                _classLock.unlock();
            }
        }
        return instance;
    }

    /**
     * Returns a ZaiClient configured with an explicit API key.
     * Does NOT cache — use getInstance() for the singleton.
     */
    public static ZaiClient withApiKey(String apiKey) {
        return ZaiClient.builder().ofZAI().apiKey(apiKey).build();
    }
}
