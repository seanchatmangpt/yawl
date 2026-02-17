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

import org.jdom2.Document;
import org.jdom2.Element;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * A simple static checker that (1) checks that the url string passed in is a valid
 * URL, and then (2) that the server at the URL is responsive.
 * <p/>
 * Author: Michael Adams
 * Creation Date: 7/05/2009
 * Migrated to java.net.http.HttpClient: 2026-02-16
 */
public class HttpURLValidator {

    private static final int CONNECT_TIMEOUT_MS = 1000;
    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(CONNECT_TIMEOUT_MS);

    /**
     * Shared HTTP client using virtual threads for network I/O.
     * Virtual threads provide efficient handling of concurrent URL validations.
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    private static boolean _cancelled = false;

    /**
     * validates a url passed as a String
     *
     * @param urlStr the url to validate
     * @return a message describing the success of failure of the validation
     */
    public static String validate(String urlStr) {
        try {
            return validate(createURL(urlStr));
        } catch (MalformedURLException mue) {
            return getErrorMessage(mue.getMessage());
        }
    }


    private static String validate(URL url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(url.toURI())
                    .timeout(CONNECT_TIMEOUT)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.discarding());
            int responseCode = response.statusCode();
            if ((responseCode < 0) || (responseCode >= 300)) {
                return getErrorMessage(responseCode + " " + response.toString());
            }
        } catch (java.net.http.HttpConnectTimeoutException cte) {
            return getErrorMessage("Error attempting to validate URL: " + cte.getMessage());
        } catch (java.net.http.HttpTimeoutException te) {
            return getErrorMessage("Error attempting to validate URL: " + te.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return getErrorMessage("Error attempting to validate URL: interrupted");
        } catch (IOException ioe) {
            return getErrorMessage("Error attempting to validate URL: " + ioe.getMessage());
        } catch (URISyntaxException use) {
            return getErrorMessage("Error attempting to validate URL: " + use.getMessage());
        }

        return "<success/>";
    }


    private static URL createURL(String urlStr) throws MalformedURLException {

        // escape early if urlStr is obviously bad
        if (urlStr == null) throw new MalformedURLException("URL is null");
        if (! (urlStr.startsWith("http://") || urlStr.startsWith("https://"))) {
            throw new MalformedURLException("Invalid protocol for http");
        }

        // this will throw an exception if the URL is invalid
        return URI.create(urlStr).toURL();
    }


    private static String getErrorMessage(String msg) {
        return StringUtil.wrap(msg, "failure");
    }


    public static void cancelAll() {
        _cancelled = true;            // called on shutdown to stop any current pings
    }


    public static boolean pingUntilAvailable(String urlStr, int timeoutSeconds)
            throws MalformedURLException {
        return new OnlineChecker().pingUntilAvailable(urlStr, timeoutSeconds);
    }


    public static boolean simplePing(String host, int port) {
        if ((host == null) || (port < 0)) return false;
        try {
            InetAddress address = InetAddress.getByName(host);
            Socket socket = new Socket(address, port);
            socket.close();
            return true;
        } catch (UnknownHostException uhe) {
            return false;
        } catch (IOException ioe) {
            return false;
        }
    }


    public static boolean isTomcatRunning(String urlStr) {
        try {
            return simplePing(URI.create(urlStr).toURL().getHost(), getTomcatServerPort());
        } catch (MalformedURLException mue) {
            return false;
        }
    }


    private static int getTomcatServerPort() {
        Document serverConfigDoc = loadTomcatConfigFile("server.xml");
        if (serverConfigDoc != null) {
            Element e = serverConfigDoc.getRootElement();
            if (e != null) {
                return StringUtil.strToInt(e.getAttributeValue("port"), -1);
            }
        }
        return -1;
    }


    private static Document loadTomcatConfigFile(String filename) {
        if (!filename.startsWith("conf")) {
            filename = "conf" + File.separator + filename;
        }
        File configFile = new File(filename);
        if (!configFile.isAbsolute()) {
            configFile = new File(System.getProperty("catalina.base"), filename);
        }
        return (configFile.exists()) ? JDOMUtil.fileToDocument(configFile) : null;
    }


    static class OnlineChecker {

        boolean pingUntilAvailable(String urlStr, int timeoutSeconds)
                throws MalformedURLException {
            URL url = createURL(urlStr);                         // exception if URL is bad
            long now = System.currentTimeMillis();
            long timeoutMoment = now + (timeoutSeconds * 1000);
            while (now <= timeoutMoment) {
                if (validate(url).equals("<success/>")) return true;
                try {
                    if (_cancelled) throw new InterruptedException();
                    Thread.sleep(CONNECT_TIMEOUT_MS);
                    now = System.currentTimeMillis();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return false;
        }
    }


}
