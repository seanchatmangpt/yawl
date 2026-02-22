package org.yawlfoundation.yawl.integration.zai;

import ai.z.openapi.ZaiClient;

/**
 * Thread-safe singleton factory for the official Z.AI SDK client.
 *
 * <p>Reads {@code ZAI_API_KEY} from environment automatically via
 * {@code ZaiClient.builder().ofZAI().build()}.
 */
public final class ZaiClientFactory {

    private static volatile ZaiClient instance;

    private ZaiClientFactory() {}

    /** Returns the shared ZaiClient instance, creating it on first call. */
    public static ZaiClient getInstance() {
        if (instance == null) {
            synchronized (ZaiClientFactory.class) {
                if (instance == null) {
                    instance = ZaiClient.builder().ofZAI().build();
                }
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
