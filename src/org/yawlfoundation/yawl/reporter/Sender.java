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

package org.yawlfoundation.yawl.reporter;

import org.apache.commons.codec.binary.Base64;
import org.yawlfoundation.yawl.engine.interfce.Interface_Client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Email sender using HTTP POST.
 * Uses Interface_Client's HttpClient (migrated 2026-02-16) for transport.
 *
 * @author Michael Adams
 * @date 1/10/15
 */
public class Sender extends Interface_Client {

    private Properties _props;

    protected Sender(InputStream isProps) { loadProperties(isProps); }


    protected String send(String subject, String text) throws IOException {
        if (_props == null) {
            throw new IOException("Failed to load sender parameters");
        }
        var params = new HashMap<String, String>();
        params.put("from",get("f"));
        params.put("to", get("t"));
        params.put("subject", subject);
        params.put("html", text);
        return send(params);
    }


    private String send(Map<String, String> params) throws IOException {
        // Add authentication header if needed
        // The executePost method will handle the actual HTTP communication
        return executePost(get("m"), params);
    }


    private String encodeCredentials() throws IOException {
        byte[] bytes = Base64.encodeBase64((get("u") + ":" + get("k")).getBytes(StandardCharsets.UTF_8));
        return new String(bytes);
    }


    private void loadProperties(InputStream inputStream) {
        try {
            _props = new Properties();
            _props.load(inputStream);
        }
        catch (Exception e) {
            _props = null;
        }
    }


    private String get(String key) throws IOException {
        String prop = _props.getProperty(key);
        if (prop == null) {
            throw new IOException("Failed to load sender parameter");
        }
        return prop;
    }

}
