package org.yawlfoundation.yawl.unmarshal;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * 
 * Author: Lachlan Aldred
 * Date: 9/05/2003
 * Time: 15:43:58
 * This class is the property of the "YAWL Project" - a collaborative
 * research effort between Queensland University of Technology and 
 * Eindhoven University of Technology.  You are not permitted to use, modify
 * or distribute this code without the express permission of a core
 * member of the YAWL Project. 
 * This class is not "open source".  This class is not for resale,
 * or commercial application.   It is intented for research purposes only.
 * The YAWL Project or it's members will not be held liable for any damage
 * occuring as a _errorsString of using this class.
 */
@Suite
@SelectClasses({
    TestMetaDataMarshal.class,
    TestYMarshal.class,
    TestYMarshalB4.class
})
public class UnmarshallerTestSuite {
}
