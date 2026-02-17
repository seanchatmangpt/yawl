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
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.net.URL;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.swingWorklist.YWorklistGUI;

/**
 * Tabbed engine GUI panel for YAWL administration and worklists.
 * Updated for Java 25 compatibility with proper dispose patterns and memory management.
 *
 * @author Lachlan Aldred
 * Date: 17/01/2005
 * Time: 19:05:50
 */
public class TabbedEngineGUI extends JPanel {
    private static final long serialVersionUID = 1L;
    private static JFrame frame;
    private static YSplash splash;
    private final JTabbedPane tabbedPane;
    private static boolean journalising = false;
    private static boolean generateUIMetaData = false;
    private static final Logger logger = LogManager.getLogger(TabbedEngineGUI.class);

    /**
     * Constructs a new TabbedEngineGUI panel.
     */
    public TabbedEngineGUI() {
        super(new BorderLayout());
        logger.debug("Init");

        ImageIcon logo = new ImageIcon(
                getToolkit().getImage(
                        getClass().getResource("YAWL_Splash2.jpg")));

        JLabel logoLabel = new JLabel(logo);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(logoLabel, BorderLayout.NORTH);

        tabbedPane = new JTabbedPane();

        JComponent panel1 = makeAdminPanel();
        tabbedPane.addTab("Administration", panel1);

        YAdminGUI adminPanel = (YAdminGUI) panel1;
        adminPanel.loadWorklists();

        panel1.setPreferredSize(new Dimension(800, 600));
        tabbedPane.setMnemonicAt(0, KeyEvent.VK_1);

        add(leftPanel, BorderLayout.WEST);
        add(tabbedPane, BorderLayout.EAST);
    }

    private JComponent makeAdminPanel() {
        return new YAdminGUI(splash, frame, this, journalising, generateUIMetaData);
    }

    /**
     * Creates a simple text panel for display.
     *
     * @param text the text to display
     * @return a JPanel containing the text
     */
    protected JComponent makeTextPanel(String text) {
        JPanel panel = new JPanel(false);
        JLabel filler = new JLabel(text);
        filler.setHorizontalAlignment(JLabel.CENTER);
        panel.setLayout(new java.awt.GridLayout(1, 1));
        panel.add(filler);
        return panel;
    }

    /**
     * Returns an ImageIcon, or null if the path was invalid.
     *
     * @param path the resource path
     * @return an ImageIcon or null if not found
     */
    protected static ImageIcon createImageIcon(String path) {
        URL imgURL = TabbedEngineGUI.class.getResource("YAWLIcon.jpg");
        if (imgURL != null) {
            return new ImageIcon(imgURL);
        } else {
            logger.error("Couldn't find file: {}", path);
            return null;
        }
    }

    /**
     * Create the GUI and show it. For thread safety,
     * this method should be invoked from the event-dispatching thread.
     */
    private static void createAndShowGUI() {
        frame = new JFrame("YAWL Engine : stand-alone version");

        if (journalising) {
            frame.setTitle(frame.getTitle() + " [Persistent mode]");
        }

        splash = new YSplash("YAWL_Splash.gif", frame, 300);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JComponent newContentPane = new TabbedEngineGUI();
        newContentPane.setOpaque(true);
        frame.getContentPane().add(newContentPane, BorderLayout.CENTER);

        URL iconURL = YAdminGUI.class.getResource("YAWLIcon.jpg");
        frame.setIconImage(Toolkit.getDefaultToolkit().createImage(iconURL));

        frame.pack();

        Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Dimension frameSize = frame.getSize();
        int x = (screenBounds.width - frameSize.width) / 2;
        int y = (screenBounds.height - frameSize.height) / 2;
        frame.setLocation(x, y);
        frame.setVisible(true);
    }

    /**
     * Main entry point for the YAWL Engine GUI.
     *
     * @param args command line arguments (-p for persistent mode, -uim for UI metadata)
     */
    public static void main(String[] args) {
        for (String arg : args) {
            if (arg.equalsIgnoreCase("-p")) {
                journalising = true;
            }
            if (arg.equalsIgnoreCase("-uim")) {
                generateUIMetaData = true;
            }
        }

        SwingUtilities.invokeLater(TabbedEngineGUI::createAndShowGUI);
    }

    /**
     * Adds a worklist panel for a user.
     *
     * @param userName the user name
     * @param worklist the worklist GUI component
     */
    public void addWorklistPanel(String userName, YWorklistGUI worklist) {
        tabbedPane.addTab(userName + "'s Worklist", worklist);
        tabbedPane.repaint();
    }

    /**
     * Gets the main frame for this GUI.
     *
     * @return the JFrame or null if not created
     */
    public static JFrame getFrame() {
        return frame;
    }

    /**
     * Disposes of the main frame and releases resources.
     */
    public static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
        if (splash != null) {
            splash.dispose();
            splash = null;
        }
    }
}
