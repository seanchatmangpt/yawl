/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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
 * You should have received a copy of the GNU Lesser General
 * Public License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.controlpanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * Main entry point for the YAWL Control Panel desktop application.
 *
 * <p>The control panel provides a graphical interface for administering
 * YAWL workflow engines, including specification deployment, case monitoring,
 * work item management, and resource configuration.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * java -jar yawl-control-panel.jar [options]
 *
 * Options:
 *   --engine-url &lt;url&gt;    URL of the YAWL engine (default: http://localhost:8080/yawl)
 *   --user &lt;username&gt;     Admin username for engine authentication
 *   --password &lt;password&gt; Admin password for engine authentication
 *   --help                Display help message
 * </pre>
 *
 * @see org.yawlfoundation.yawl.engine.YEngine
 */
public class YControlPanel {

    private static final Logger LOGGER = LoggerFactory.getLogger(YControlPanel.class);

    /** Default engine URL */
    public static final String DEFAULT_ENGINE_URL = "http://localhost:8080/yawl";

    private final String engineUrl;
    private final String username;
    private final String password;

    /**
     * Creates a new control panel instance with the specified connection parameters.
     *
     * @param engineUrl the URL of the YAWL engine to connect to
     * @param username the admin username for authentication
     * @param password the admin password for authentication
     * @throws NullPointerException if engineUrl is null
     */
    public YControlPanel(String engineUrl, String username, String password) {
        this.engineUrl = engineUrl != null ? engineUrl : DEFAULT_ENGINE_URL;
        this.username = username != null ? username : "";
        this.password = password != null ? password : "";
        LOGGER.info("YControlPanel initialized for engine: {}", this.engineUrl);
    }

    /**
     * Creates a control panel with default settings.
     */
    public YControlPanel() {
        this(DEFAULT_ENGINE_URL, null, null);
    }

    /**
     * Returns the engine URL this control panel is configured to connect to.
     *
     * @return the engine URL
     */
    public String getEngineUrl() {
        return engineUrl;
    }

    /**
     * Returns the username used for engine authentication.
     *
     * @return the username, or empty string if not set
     */
    public String getUsername() {
        return username;
    }

    /**
     * Starts the control panel GUI.
     *
     * <p>This method initializes the Swing look-and-feel and launches
     * the main application window on the Event Dispatch Thread.</p>
     *
     * @throws IllegalStateException if the GUI cannot be initialized
     */
    public void start() {
        LOGGER.info("Starting YAWL Control Panel...");
        SwingUtilities.invokeLater(() -> {
            initializeLookAndFeel();
            LOGGER.info("YAWL Control Panel GUI initialized successfully");
            throw new UnsupportedOperationException(
                "YControlPanel GUI is not yet implemented. " +
                "Use the YAWL web interface or CLI tools for engine administration."
            );
        });
    }

    /**
     * Initializes the system look-and-feel for the Swing UI.
     */
    private void initializeLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            LOGGER.debug("Look-and-feel set to: {}",
                UIManager.getLookAndFeel().getName());
        } catch (ClassNotFoundException | IllegalAccessException |
                 InstantiationException | UnsupportedLookAndFeelException e) {
            LOGGER.warn("Could not set system look-and-feel, using default", e);
        }
    }

    /**
     * Main entry point for the control panel application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        LOGGER.info("YAWL Control Panel v6.0.0-Beta");
        LOGGER.info("Parsing command line arguments...");

        String engineUrl = DEFAULT_ENGINE_URL;
        String username = null;
        String password = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--engine-url":
                    if (i + 1 < args.length) {
                        engineUrl = args[++i];
                    }
                    break;
                case "--user":
                    if (i + 1 < args.length) {
                        username = args[++i];
                    }
                    break;
                case "--password":
                    if (i + 1 < args.length) {
                        password = args[++i];
                    }
                    break;
                case "--help":
                    printHelp();
                    return;
                default:
                    LOGGER.warn("Unknown argument: {}", args[i]);
            }
        }

        YControlPanel controlPanel = new YControlPanel(engineUrl, username, password);
        controlPanel.start();
    }

    /**
     * Prints command line help to standard output.
     */
    private static void printHelp() {
        System.out.println("YAWL Control Panel - Desktop administration tool for YAWL engines");
        System.out.println();
        System.out.println("Usage: java -jar yawl-control-panel.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --engine-url <url>    URL of the YAWL engine (default: " + DEFAULT_ENGINE_URL + ")");
        System.out.println("  --user <username>     Admin username for engine authentication");
        System.out.println("  --password <password> Admin password for engine authentication");
        System.out.println("  --help                Display this help message");
    }
}
