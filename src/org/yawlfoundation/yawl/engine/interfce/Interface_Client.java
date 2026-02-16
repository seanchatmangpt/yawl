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

package org.yawlfoundation.yawl.engine.interfce;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * This class is used by clients and servers to execute GET and POST requests
 * across the YAWL interfaces. Note that since v2.0up4 (12/08) all requests are sent as
 * POSTS - increases efficiency, security and allows 'extended' chars to be included.
 *
 * Modernized in v5.2 to use java.net.http.HttpClient (Java 11+).
 * Migrated to virtual threads (Java 25) for improved scalability with external services.
 *
 * @author Lachlan Aldred
 * Date: 22/03/2004
 * Time: 17:49:42
 *
 * @author Michael Adams (refactored for v2.0, 06/2008; and again 12/2008 & 04/2010)
 * @updated 2026-02-16 Virtual thread migration (Java 25)
 */

public class Interface_Client {

    /**
     * Shared HTTP client instance using virtual threads for network I/O.
     * Before: Default executor (ForkJoinPool.commonPool())
     * After: Virtual thread executor (unbounded concurrency for blocking I/O)
     *
     * Virtual threads are ideal for HTTP client operations because:
     * 1. Network I/O is inherently blocking
     * 2. No CPU-intensive work in HTTP communication
     * 3. Can handle thousands of concurrent connections efficiently
     * 4. Eliminates need for connection pool tuning
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(5000))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    // allows the prevention of socket reads from blocking indefinitely
    private static Duration READ_TIMEOUT = Duration.ofMillis(120000);  // default: 2 minutes


    /**
     * Executes a HTTP POST request on the url specified.
     *
     * @param urlStr the URL to send the POST to
     * @param paramsMap a set of attribute-value pairs that make up the posted data
     * @return the result of the POST request
     * @throws IOException when there's some kind of communication problem
     */
    protected String executePost(String urlStr, Map<String, String> paramsMap)
            throws IOException {

        String encodedData = encodeData(paramsMap);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlStr))
                .timeout(READ_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept-Charset", "UTF-8")
                .header("Connection", "close")  // prevent connection reuse under heavy load
                .POST(HttpRequest.BodyPublishers.ofString(encodedData))
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());
            String result = response.body();
            return stripOuterElement(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", e);
        }
    }

    /**
     * Executes an asynchronous HTTP POST request using virtual threads.
     * This method returns immediately with a CompletableFuture that will be completed
     * when the HTTP response arrives.
     *
     * Virtual threads make this naturally efficient - each async request runs in its
     * own virtual thread without platform thread pool concerns.
     *
     * @param urlStr the URL to send the POST to
     * @param paramsMap a set of attribute-value pairs that make up the posted data
     * @return CompletableFuture that will complete with the response string
     */
    protected CompletableFuture<String> executePostAsync(String urlStr, Map<String, String> paramsMap) {
        String encodedData = encodeData(paramsMap);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlStr))
                .timeout(READ_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept-Charset", "UTF-8")
                .header("Connection", "close")
                .POST(HttpRequest.BodyPublishers.ofString(encodedData))
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> stripOuterElement(response.body()))
                .exceptionally(throwable -> {
                    throw new RuntimeException("HTTP POST failed for URL: " + urlStr, throwable);
                });
    }


    /**
     * Executes a rerouted HTTP GET request as a POST on the specified URL
     *
     * @param urlStr the URL to send the GET to
     * @param paramsMap a set of attribute-value pairs that make up the posted data
     * @return the result of the request
     * @throws IOException when there's some kind of communication problem
     */
    protected String executeGet(String urlStr, Map<String, String> paramsMap)
            throws IOException {

        String encodedData = encodeData(paramsMap);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlStr))
                .timeout(READ_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept-Charset", "UTF-8")
                .header("Connection", "close")
                .POST(HttpRequest.BodyPublishers.ofString(encodedData))
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());
            return response.body();  // don't strip outer element for GET requests
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", e);
        }
    }

    /**
     * Executes an asynchronous HTTP GET request (implemented as POST) using virtual threads.
     *
     * @param urlStr the URL to send the GET to
     * @param paramsMap a set of attribute-value pairs that make up the posted data
     * @return CompletableFuture that will complete with the response string
     */
    protected CompletableFuture<String> executeGetAsync(String urlStr, Map<String, String> paramsMap) {
        String encodedData = encodeData(paramsMap);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlStr))
                .timeout(READ_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept-Charset", "UTF-8")
                .header("Connection", "close")
                .POST(HttpRequest.BodyPublishers.ofString(encodedData))
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .exceptionally(throwable -> {
                    throw new RuntimeException("HTTP GET failed for URL: " + urlStr, throwable);
                });
    }


    /**
     * Initialises a map for transporting parameters - used by extending classes
     * @param action the name of the action to take
     * @param handle the current engine session handle
     * @return the initialised Map
     */
    protected Map<String, String> prepareParamMap(String action, String handle) {
        Map<String, String> paramMap = new HashMap<String, String>();
        paramMap.put("action", action) ;
        if (handle != null) paramMap.put("sessionHandle", handle) ;
        return paramMap;
    }


    /**
     * Set the read timeout value for future connections
     * @param timeout the timeout value in milliseconds. A value of 0 or less
     *                means a read will wait indefinitely (up to default limit).
     */
    protected void setReadTimeout(int timeout) {
        if (timeout > 0) {
            READ_TIMEOUT = Duration.ofMillis(timeout);
        } else {
            READ_TIMEOUT = Duration.ofMillis(120000);  // 2 minute default
        }
    }


    /**
     * Removes the outermost set of xml tags from a string, if any
     * @param xml the xml string to strip
     * @return the stripped xml string
     */
    protected String stripOuterElement(String xml) {
        if (xml != null) {
            int start = xml.indexOf('>') + 1;
            int end = xml.lastIndexOf('<');
            if (end > start) {
                return xml.substring(start, end);
            }    
        }
        return xml;
    }




    /**
     * Tests a response message for success or failure
     * @param message the response message to test
     * @return true if the response represents success
     */
    public boolean successful(String message) {
        return (message != null)  &&
               (message.length() > 0) &&
               (! message.contains("<failure>")) ;
    }


    /*******************************************************************************/

    // PRIVATE METHODS //


     /**
     * Encodes parameter values for HTTP transport
     * @param params a map of the data parameter values, of the form
     *        [param1=value1],[param2=value2]...
     * @return a formatted http data string with the data values encoded
     */
    private String encodeData(Map<String, String> params) {
        StringBuilder result = new StringBuilder("");
        for (String param : params.keySet()) {
            String value = params.get(param);
            if (value != null) {
                if (result.length() > 0) result.append("&");
                result.append(param)
                      .append("=")
                      .append(ServletUtils.urlEncode(value));
            }
        }
        return result.toString();
    }


}
