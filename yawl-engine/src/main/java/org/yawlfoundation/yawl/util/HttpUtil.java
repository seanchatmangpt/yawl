/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.util;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * HTTP utility methods using modern java.net.http.HttpClient.
 *
 * @author Michael Adams
 * @date 4/08/2014
 */
public class HttpUtil {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static boolean isResponsive(URL url) {
        try {
            return resolveURL(url) != null;
        }
        catch (IOException | InterruptedException e) {
            return false;
        }
    }


    public static URL resolveURL(String urlString) throws IOException, InterruptedException {
        return resolveURL(URI.create(urlString).toURL());
    }


    /**
     * Follows redirects and returns the final URL.
     * Uses modern HttpClient which handles redirects automatically.
     */
    public static URL resolveURL(URL url) throws IOException, InterruptedException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(url.toURI())
                    .timeout(TIMEOUT)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode());
            }

            return response.uri().toURL();
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URL: " + url, e);
        }
    }


    public static boolean isPortActive(String host, int port) {
        try {
            return simplePing(host, port);
        }
        catch (IOException ioe) {
            return false;
        }
    }


    public static void download(String fromURL, File toFile) throws IOException, InterruptedException {
        download(URI.create(fromURL).toURL(), toFile);
    }


    /**
     * Download a file from a URL using modern HttpClient.
     */
    public static void download(URL fromURL, File toFile) throws IOException, InterruptedException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(fromURL.toURI())
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();

            HttpResponse<java.io.InputStream> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() >= 400) {
                throw new IOException("Download failed: HTTP " + response.statusCode());
            }

            Files.copy(response.body(), toFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URL: " + fromURL, e);
        }
    }


    private static boolean simplePing(String host, int port) throws IOException {
        if ((host == null) || (port < 0)) {
            throw new IOException("Error: bad parameters");
        }
        InetAddress address = InetAddress.getByName(host);
        Socket socket = new Socket(address, port);
        socket.close();
        return true;
    }


}
