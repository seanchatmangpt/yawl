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

package org.yawlfoundation.yawl.engine.interfce.interfaceB;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.elements.data.YParameter;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.Marshaller;
import org.yawlfoundation.yawl.engine.interfce.ServletUtils;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.util.StringUtil;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Receives event announcements from the engine and passes each of them to the
 * custom service's appropriate handling method.
 *
 * Migrated to virtual threads for improved concurrency and resource efficiency.
 * Each event is processed on a dedicated virtual thread, ensuring no blocking
 * of the HTTP request/response cycle.
 *
 * @author Lachlan Aldred
 * Date: 23/01/2004
 * Time: 13:26:04
 *
 * @author Michael Adams (refactored for v2.0, 12/2008)
 * @updated 2026-02-16 Virtual thread migration (Java 25)
 */

public class InterfaceB_EnvironmentBasedServer extends HttpServlet {
    private InterfaceBWebsideController _controller;
    private final Logger _logger = LogManager.getLogger(InterfaceB_EnvironmentBasedServer.class);

    /**
     * Virtual thread executor for async event processing.
     * Before: Single platform thread (sequential event processing)
     * After: Virtual threads (concurrent event processing with no queue wait)
     *
     * Each HTTP event announcement spawns a virtual thread, allowing HTTP response
     * to complete immediately while event processing continues asynchronously.
     */
    private final ExecutorService _executor = Executors.newVirtualThreadPerTaskExecutor();


    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        ServletContext context = servletConfig.getServletContext();

        // get the name of the custom service implementing this interface
        // (i.e. the name of the class that extends InterfaceBWebSideController)
        String controllerClassName = context.getInitParameter("InterfaceBWebSideController");

        try {
            Class<? extends InterfaceBWebsideController> controllerClass =
                    Class.forName(controllerClassName).asSubclass(InterfaceBWebsideController.class);

            // If the class has a getInstance() method, call that method rather than
            // calling a constructor (& thus instantiating 2 instances of the class)
            try {
                Method instMethod = controllerClass.getDeclaredMethod("getInstance");
                _controller = (InterfaceBWebsideController) instMethod.invoke(null);
            }
            catch (NoSuchMethodException nsme) {
                _controller = controllerClass.getDeclaredConstructor().newInstance();
            }
            
            // retrieve the URL of the YAWL Engine from the web.xml file.
            String engineBackendAddress = context.getInitParameter("InterfaceB_BackEnd");
            _controller.setUpInterfaceBClient(engineBackendAddress);

            //If there is an auth proxy firewall and it has been configured it in the
            //web.xml file the settings be retrieved for use.
            String userName = context.getInitParameter("UserName");
            String password = context.getInitParameter("Password");
            String proxyHost = context.getInitParameter("ProxyHost");
            String proxyPort = context.getInitParameter("ProxyPort");
            _controller.setRemoteAuthenticationDetails(
                    userName, password, proxyHost, proxyPort);
            
            // if there are overridden engine logon & password, set them for the service.
            // SOC2 CRITICAL: Environment variables take precedence over web.xml values to
            // prevent plaintext credential storage in deployment descriptors.
            String logonName = context.getInitParameter("EngineLogonUserName");
            String logonPassword = resolveCredential(
                    context.getInitParameter("EngineLogonPassword"),
                    "YAWL_ENGINE_PASSWORD");
            if (logonName != null) _controller.setEngineLogonName(logonName);
            if (logonPassword != null) _controller.setEngineLogonPassword(logonPassword);

            context.setAttribute("controller", _controller);
        }
        catch (Exception e) {
            _logger.error("Failed to initialize InterfaceB controller '{}' - servlet cannot process requests",
                    controllerClassName, e);
            throw new ServletException("Failed to initialize InterfaceB controller: " + controllerClassName, e);
        }
    }


    /**
     * Resolves a credential value from environment variable (preferred) or web.xml fallback.
     *
     * SOC2 CRITICAL: Credentials must not be stored in plaintext in web.xml. The environment
     * variable takes absolute precedence. The web.xml value is accepted only when the env var
     * is absent (e.g. developer workstation without secrets infrastructure).
     *
     * @param webXmlValue  value read from the servlet context init parameter (may be null)
     * @param envVarName   name of the environment variable to prefer
     * @return the credential from the environment variable if present, otherwise webXmlValue
     */
    private String resolveCredential(String webXmlValue, String envVarName) {
        String envValue = System.getenv(envVarName);
        if (envValue != null && !envValue.isEmpty()) {
            _logger.debug("Resolved credential '{}' from environment variable", envVarName);
            return envValue;
        }
        if (webXmlValue != null && !webXmlValue.isEmpty()) {
            _logger.warn("Credential '{}' is configured in web.xml; migrate to environment " +
                    "variable '{}' for SOC2 compliance", "EngineLogonPassword", envVarName);
        }
        return webXmlValue;
    }


    public void destroy() {
        _controller.destroy();
    }


    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
         _controller.doGet(request, response);
    }


    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        // store a copy of the request params for later
        Map<String, String> paramsMap = new HashMap<>();
        Enumeration paramNms = request.getParameterNames();
        while (paramNms.hasMoreElements()) {
            String name = (String) paramNms.nextElement();
            paramsMap.put(name, request.getParameter(name));
        }

        // only a request for parameter info needs a non-default response
        String action = request.getParameter("action");
        String result = "OK";
        if ("ParameterInfoRequest".equals(action)) {
            YParameter[] params = _controller.describeRequiredParams();
            StringBuilder output = new StringBuilder();
            for (YParameter param : params) {
                if (param != null) output.append(param.toXML());
            }
            result = StringUtil.wrap(output.toString(), "response");
        }

        // complete the request/response thread early by sending the default response
        OutputStreamWriter outputWriter = ServletUtils.prepareResponse(response);
        outputWriter.write(result);
        outputWriter.flush();
        outputWriter.close();

        // for all event actions, send the notification to services
       _executor.execute(new EventHandler(paramsMap));
    }

    
    class EventHandler implements Runnable {

        Map<String,String> paramsMap;

        EventHandler(Map<String, String> map) { paramsMap = map; }

        public void run() {
            String action = paramsMap.get("action");
            String caseID = paramsMap.get("caseID");
            String workItemXML = paramsMap.get("workItem");
            WorkItemRecord workItem = (workItemXML != null) ?
                    Marshaller.unmarshalWorkItem(workItemXML) : null;

            // where there are two choices for 'action' below, those on the left are
            // passed from 2.2 or later engine versions, while those on the right come
            // from pre-2.2 engine versions
            if ("announceItemEnabled".equals(action) || "handleEnabledItem".equals(action)) {
                _controller.handleEnabledWorkItemEvent(workItem);
            }
            else if ("announceItemStatus".equals(action)) {
                String oldStatus = paramsMap.get("oldStatus");
                String newStatus = paramsMap.get("newStatus");
                _controller.handleWorkItemStatusChangeEvent(workItem, oldStatus, newStatus);
            }
            else if ("announceCaseStarted".equals(action)) {
                String launchingService = paramsMap.get("launchingService");
                String delayedStr = paramsMap.get("delayed");
                boolean delayed = delayedStr != null && delayedStr.equalsIgnoreCase("true");
                YSpecificationID specID = new YSpecificationID(
                        paramsMap.get("specidentifier"),
                        paramsMap.get("specversion"),
                        paramsMap.get("specuri"));
                _controller.handleStartCaseEvent(specID, caseID, launchingService, delayed);
            }
            else if ("announceCaseCompleted".equals(action) || "announceCompletion".equals(action)) {
                String casedata = paramsMap.get("casedata");
                _controller.handleCompleteCaseEvent(caseID, casedata);
            }
            else if ("announceItemCancelled".equals(action) || "cancelWorkItem".equals(action)) {
                _controller.handleCancelledWorkItemEvent(workItem);
            }
            else if ("announceCaseCancelled".equals(action)) {
                _controller.handleCancelledCaseEvent(caseID);
            }
            else if ("announceCaseDeadlocked".equals(action)) {
                String tasks = paramsMap.get("tasks");
                _controller.handleDeadlockedCaseEvent(caseID, tasks);
            }
            else if ("announceTimerExpiry".equals(action) || "timerExpiry".equals(action)) {
                _controller.handleTimerExpiryEvent(workItem);
            }
            else if ("announceEngineInitialised".equals(action)) {
                _controller.handleEngineInitialisationCompletedEvent();
            }
            else if ("announceCaseSuspending".equals(action)) {
                _controller.handleCaseSuspendingEvent(caseID);
            }
            else if ("announceCaseSuspended".equals(action)) {
                _controller.handleCaseSuspendedEvent(caseID);
            }
            else if ("announceCaseResumed".equals(action)) {
                _controller.handleCaseResumedEvent(caseID);
            }
        }
    }
}
