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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;

/**
 * Splash screen window for YAWL engine startup.
 * Updated for Java 25 compatibility with proper dispose patterns.
 *
 * @author remco d
 * Created on 12 december 2001, 16:34
 */
public class YSplash extends JWindow {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LogManager.getLogger(YSplash.class);
    private final JProgressBar progressBar;
    private volatile boolean disposed = false;

    /**
     * Creates a new splash screen.
     *
     * @param filename the image resource filename
     * @param parent the parent frame (may be null)
     * @param waitTime the time to display the splash in milliseconds
     */
    public YSplash(String filename, java.awt.Frame parent, int waitTime) {
        super(parent);

        ImageIcon splashImage = new ImageIcon(getToolkit().getImage(
                getClass().getResource(filename)));
        JLabel imageLabel = new JLabel(splashImage);
        getContentPane().add(imageLabel, BorderLayout.CENTER);

        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        getContentPane().add(progressBar, BorderLayout.SOUTH);
        pack();

        centerOnScreen(imageLabel.getPreferredSize());

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                closeSplash();
            }
        });

        setVisible(true);
    }

    /**
     * Centers the splash screen on the primary display.
     *
     * @param labelSize the size of the splash content
     */
    private void centerOnScreen(Dimension labelSize) {
        Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int x = (screenBounds.width - labelSize.width) / 2;
        int y = (screenBounds.height - labelSize.height) / 2;
        setLocation(x, y);
    }

    /**
     * Sets the progress bar value.
     *
     * @param progress the progress value (0-100)
     */
    public void setProgress(int progress) {
        if (disposed) {
            return;
        }

        progressBar.setValue(progress);

        if (progress == 100) {
            closeSplash();
        }
    }

    /**
     * Closes the splash screen and releases resources.
     */
    private void closeSplash() {
        if (disposed) {
            return;
        }
        disposed = true;

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("Splash sleep interrupted");
        }

        if (SwingUtilities.isEventDispatchThread()) {
            dispose();
        } else {
            try {
                SwingUtilities.invokeAndWait(this::dispose);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.debug("Splash dispose interrupted");
            } catch (InvocationTargetException e) {
                logger.error("Error disposing splash screen", e);
            }
        }
    }

    /**
     * Checks if the splash screen has been disposed.
     *
     * @return true if disposed
     */
    public boolean isDisposed() {
        return disposed;
    }
}
