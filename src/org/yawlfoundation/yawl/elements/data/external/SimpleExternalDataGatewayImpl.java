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

package org.yawlfoundation.yawl.elements.data.external;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Element;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.data.YParameter;
import org.yawlfoundation.yawl.elements.data.YVariable;
import org.yawlfoundation.yawl.engine.YSpecificationID;

import org.yawlfoundation.yawl.integration.CredentialManager;

/**
 * Author: Michael Adams
 * Creation Date: 13/08/2009
 */
public class SimpleExternalDataGatewayImpl implements ExternalDataGateway {

    private static final Logger _logger = LogManager.getLogger(SimpleExternalDataGatewayImpl.class);

    public SimpleExternalDataGatewayImpl() {}

    private boolean configured = false;

    // an instance of the backend 'engine' that provides the interface to the database
    protected HibernateEngine _dbEngine = HibernateEngine.getInstance();

    /**
     * Configures the engine to the database using credentials from environment variables.
     *
     * <p>Reads connection details from:
     * <ul>
     *   <li>{@code YAWL_JDBC_URL} or constructed from {@code DB_HOST}/{@code DB_PORT}/{@code DB_NAME}</li>
     *   <li>{@code YAWL_JDBC_USER} or {@code DB_USER} (default: yawl)</li>
     *   <li>{@code YAWL_JDBC_PASSWORD} or {@code DB_PASSWORD} (required)</li>
     * </ul>
     * </p>
     *
     * @throws IllegalStateException if required credential environment variables are missing
     */
    public boolean configure() {
        if (! configured) {
            _dbEngine.configureSession(
                    "org.hibernate.dialect.PostgreSQLDialect",
                    "org.postgresql.Driver",
                    CredentialManager.getJdbcUrl(),
                    CredentialManager.getJdbcUser(),
                    CredentialManager.getJdbcPassword(),
                    null);
            configured = true;
        }
        return configured;
    }


    /**
     * Configures the engine to the database specified. Examples given are for postgres.
     * @param dialect the database dialect (e.g. "org.hibernate.dialect.PostgreSQLDialect")
     * @param driver the database driver (e.g. "org.postgresql.Driver")
     * @param url the database url (e.g. "jdbc:postgresql:yawl")
     * @param username the logon name for the database
     * @param password the logon password
     * @param classes a list of classes for Hibernate to use to converse with the
     * underlying tables (can be null if no classes are involved)
     */
    protected void configureSession(String dialect, String driver, String url,
                                 String username, String password, List<Class> classes) {
        _dbEngine.configureSession(dialect, driver, url, username, password, classes);
    }


    /**
     * Configures the engine to the database specified in a properties file
     * @param propertiesFileName the full path & name of the properties file
     */
    protected void configureSession(String propertiesFileName, List<Class> classes) {
        if (propertiesFileName == null) return;
        Properties props = new Properties();
        try {
            props.load(new FileInputStream(propertiesFileName));
            _dbEngine.configureSession(props, classes);
        }
        catch (IOException e) {
            LogManager.getLogger(ExternalDataGateway.class).error(
                    "Could not open properties file " + propertiesFileName, e);
        }
    }


    public String getDescription() {
        return "A simple example of an external database gateway implementation";
    }


    public Element populateTaskParameter(YTask task, YParameter param, Element caseData) {
        Element result = new Element(param.getName());
//        if (configure()) {
//            List resultSet = _dbEngine.execQuery("SELECT * FROM privilege");
//            for (Object row : resultSet) {
//                System.out.println(row) ;
//            }
            result.setText("the data");
 //       }
        result.setAttribute("trusty", "true");
        result.setAttribute("other", "smith");
        return result;
    }


    public void updateFromTaskCompletion(YTask task, String paramName, Element outputData,
                                         Element caseData) {
        // No-op in base SimpleExternalDataGatewayImpl. Subclasses should override to
        // write task output data back to the external data source.
        _logger.debug("updateFromTaskCompletion called for task: {}, param: {}",
                task != null ? task.getID() : "null", paramName);
    }


    public Element populateCaseData(YSpecificationID specID, String caseID,
                                    List<YParameter> inputParams,
                                    List<YVariable> localVars, Element caseDataTemplate) {
        // Base implementation returns the unmodified template.
        // Subclasses should override to populate case data from the external data source.
        _logger.debug("populateCaseData called for case: {}", caseID);
        return caseDataTemplate;
    }

    public void updateFromCaseData(YSpecificationID specID, String caseID,
                                   List<YParameter> outputParams,
                                   Element updatingData) {
        _logger.debug("updateFromCaseData called for case: {}", caseID);
    }


}
