/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.orderfulfillment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;

/**
 * Multi-agent capacity coordination. Checks peer agents' /capacity endpoint
 * before proceeding with dependent work (e.g. Ordering checks Carrier before
 * requesting a quote).
 *
 * AGENT_PEERS: comma-separated URLs, e.g. http://carrier-agent:8092,http://freight-agent:8093
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class CapacityChecker {

    private static final int TIMEOUT_MS = 2000;

    /**
     * Check if all peer agents report available capacity.
     *
     * @param peersCommaSeparated comma-separated agent base URLs
     * @return true if all peers report available, or if no peers configured
     */
    public static boolean checkPeersAvailable(String peersCommaSeparated) {
        if (peersCommaSeparated == null || peersCommaSeparated.isBlank()) {
            return true;
        }
        String[] urls = peersCommaSeparated.split(",");
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(TIMEOUT_MS))
            .build();
        for (String url : urls) {
            String base = url.strip();
            if (base.isEmpty()) continue;
            String capacityUrl = base.endsWith("/") ? base + "capacity" : base + "/capacity";
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(capacityUrl))
                    .timeout(Duration.ofMillis(TIMEOUT_MS))
                    .GET()
                    .build();
                HttpResponse<String> resp = client.send(req,
                    HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    return false;
                }
                String body = resp.body();
                if (body == null || body.contains("\"available\":false")) {
                    return false;
                }
            } catch (IOException | InterruptedException e) {
                return false;
            }
        }
        return true;
    }
}
