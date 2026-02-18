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

package org.yawlfoundation.yawl.procletService.editor.blockchoiceexception;

import javax.swing.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.procletService.editor.InternalCoordinator;

public class BlockExceptionChoiceCoordinator extends InternalCoordinator {
    private static final Logger logger = LogManager.getLogger(BlockExceptionChoiceCoordinator.class);
	
	  // frame for the block
	  private FrmBlockExceptionChoice frame = null;

	  private static BlockExceptionChoiceCoordinator instance = null;
	  private JTextField nameTextField = null;

	  private BlockExceptionChoiceCoordinator(JFrame aMainFrame) {
	    super(aMainFrame);
	    frame = FrmBlockExceptionChoice.singleton(this);
	    start();
	  }

	  public static BlockExceptionChoiceCoordinator singleton(JFrame aMainFrame) {
	    //if (instance == null) {
	      instance = new BlockExceptionChoiceCoordinator(aMainFrame);
	    //}
	    return (BlockExceptionChoiceCoordinator) instance;
	  }
	  
	  public static BlockExceptionChoiceCoordinator getInstance() {
		  return instance;
	  }

	  public static boolean exists() {
	    return instance != null;
	  }

	  public static void finish() {
//		if (instance != null) {
//			instance.frame = null;
//		}
	    instance = null;
	    FrmBlockExceptionChoice.finish();
	  }

	  /**
	   * start
	   */
	  public void start() {
	    
	  }

	  /**
	   * getInternalFrame
	   *
	   * @return JInternalFrame
	   */
	  public JInternalFrame getInternalFrame() {
	    return frame;
	  }

	  /**
	   * end
	   */
	  public void end() {
	    try {
	      this.frame.setClosed(true);
	    }
	    catch (Exception e) {
            logger.warn("Failed to close block exception choice frame: " + e.getMessage(), e);
        }
	    ;
	  }

	  private BlockExceptionChoiceControl getBlockExceptionChoiceControl() {
	    return this.getControl().getBlockExceptionChoiceControl();
	  }
	
}
