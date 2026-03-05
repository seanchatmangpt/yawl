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

package org.yawlfoundation.yawl.engine.interfce.rest;

import java.util.HashMap;
import java.util.Map;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Maps exceptions to HTTP responses with JSON error messages.
 * Provides consistent error handling across all REST endpoints.
 *
 * @author Michael Adams
 * @date 16/02/2026
 */
@Provider
public class YawlExceptionMapper implements ExceptionMapper<Exception> {


    private static final Logger logger = LogManager.getLogger(YawlExceptionMapper.class);
    private static final Logger _logger = LogManager.getLogger(YawlExceptionMapper.class);
    private final ObjectMapper _mapper;

    public YawlExceptionMapper() {
        _mapper = new ObjectMapper();
    }

    @Override
    public Response toResponse(Exception exception) {
        _logger.error("REST API exception", exception);

        Map<String, Object> error = new HashMap<>();
        error.put("error", exception.getClass().getSimpleName());
        error.put("message", exception.getMessage() != null ? exception.getMessage() : "Unknown error");

        try {
            String json = _mapper.writeValueAsString(error);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(json)
                    .build();
        } catch (Exception e) {
            _logger.error("Error serializing exception response", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Internal server error\", \"message\": \"Error processing exception\"}")
                    .build();
        }
    }
}
