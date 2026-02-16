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

package org.yawlfoundation.yawl.wsif;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Element;
import org.yawlfoundation.yawl.elements.data.YParameter;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.TaskInformation;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.YParametersSchema;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceBWebsideController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;


/**
 * DEPRECATED: Apache WSIF was abandoned in 2007 and has no modern replacement.
 * This controller is kept for backward compatibility but throws
 * UnsupportedOperationException when used.
 *
 * Migration path: Use Jakarta JAX-WS (jakarta.xml.ws) or modern REST APIs instead.
 *
 * @author Lachlan Aldred
 * @deprecated since 5.2 - Apache WSIF abandoned, use JAX-WS or REST
 * Date: 19/03/2004
 * Time: 11:40:59
 */
@Deprecated(since = "5.2", forRemoval = true)
public class WSIFController extends InterfaceBWebsideController {

    private String _sessionHandle = null;
    private Logger _log = LogManager.getLogger(this.getClass());

    private static final String WSDL_LOCATION_PARAMNAME = "YawlWSInvokerWSDLLocation";
    private static final String WSDL_PORTNAME_PARAMNAME = "YawlWSInvokerPortName";
    private static final String WSDL_OPERATIONNAME_PARAMNAME = "YawlWSInvokerOperationName";


    /**
     * Implements InterfaceBWebsideController.  It receives messages from the engine
     * notifying an enabled task and acts accordingly.  In this case it takes the message,
     * tries to check out the work item, and if successful it begins to start up a web service
     * invocation.
     * @param enabledWorkItem
     */
    public void handleEnabledWorkItemEvent(WorkItemRecord enabledWorkItem) {
        _log.error("WSIF support has been removed. Apache WSIF was abandoned in 2007. " +
                "Please migrate to Jakarta JAX-WS (jakarta.xml.ws) or modern REST APIs.");
        throw new UnsupportedOperationException(
                "WSIF support removed in YAWL 5.2. " +
                "Apache WSIF was abandoned in 2007 and has known security vulnerabilities. " +
                "Migrate to Jakarta JAX-WS (jakarta.xml.ws.*) or use modern REST APIs instead. " +
                "See https://eclipse-ee4j.github.io/metro-jax-ws/ for JAX-WS documentation.");
    }

    private Map<String, String> getOutputDataTypes(WorkItemRecord wir) throws IOException {
        Map<String, String> dataTypes = new Hashtable<String, String>();
        TaskInformation taskInfo = this.getTaskInformation(
                new YSpecificationID(wir), wir.getTaskID(), _sessionHandle);
        if (taskInfo != null) {
            YParametersSchema schema = taskInfo.getParamSchema();
            if (schema != null) {
                for (YParameter param : schema.getOutputParams()) {
                    dataTypes.put(param.getPreferredName(), param.getDataTypeName());
                }
            }
        }
        return dataTypes;
    }

    private String validateValue(String type, String value) {
        if (type.endsWith("boolean")) {
            return String.valueOf(value.equalsIgnoreCase("true"));
        }
        try {
            if (type.endsWith("integer")) {
               return String.valueOf(Integer.valueOf(value));
            }
            else if (type.endsWith("double")) {
               return String.valueOf(Double.valueOf(value));
            }
            else if (type.endsWith("float")) {
               return String.valueOf(Float.valueOf(value));
            }
            else return value;    // we tried!
        }
        catch (NumberFormatException nfe) {
            if (type.endsWith("integer")) {
                return "0";
            }
            else {
                return "0.0";
            }
        }
    }

    public void handleCancelledWorkItemEvent(WorkItemRecord workItemRecord) {

    }

    public YParameter[] describeRequiredParams() {
        YParameter[] params = new YParameter[3];
        YParameter param;

        param = new YParameter(null, YParameter._INPUT_PARAM_TYPE);
        param.setDataTypeAndName(XSD_ANYURI_TYPE, WSDL_LOCATION_PARAMNAME, XSD_NAMESPACE);
        param.setDocumentation("This is the location of the WSDL for the Web Service");
        params[0] = param;

        param = new YParameter(null, YParameter._INPUT_PARAM_TYPE);
        param.setDataTypeAndName(XSD_NCNAME_TYPE, WSDL_PORTNAME_PARAMNAME, XSD_NAMESPACE);
        param.setDocumentation("This is the port name of the Web service - inside the WSDL.");
        params[1] = param;

        param = new YParameter(null, YParameter._INPUT_PARAM_TYPE);
        param.setDataTypeAndName(XSD_NCNAME_TYPE, WSDL_OPERATIONNAME_PARAMNAME, XSD_NAMESPACE);
        param.setDocumentation("This is the operation name of the Web service - inside the WSDL.");
        params[2] = param;

        return params;
    }


    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        RequestDispatcher dispatcher =
                request.getRequestDispatcher("/authServlet");

        dispatcher.forward(request, response);
    }


}

