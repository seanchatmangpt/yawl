package org.yawlfoundation.yawl.worklist;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.interfce.TaskInformation;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

/**
 *
 * @author Lachlan Aldred
 * Date: 27/02/2004
 * Time: 14:22:31
 *
 */
@Tag("unit")
class TestWorklistController{

    @Test

    void testGetTaskInformation(){
        String thestring =
                "<response><taskInfo>" +
                "<specification><identifier/><version>0.1</version><uri>makeTrip2.xml</uri></specification>" +
                "<taskID>register</taskID><taskName>register</taskName>" +
                "<params>" +
                "<inputParam name=\"customer\"><type>xs:string</type><ordering>0</ordering></inputParam>" +
                "<outputParam name=\"payment_account_number\"><type>xs:string</type>" +
                "<ordering>0</ordering><mandatory/></outputParam>" +
                "<outputParam name=\"legs\"><type>mm:LegType</type><ordering>2</ordering><mandatory/>" +
                "</outputParam>" +
                "<outputParam name=\"customer\"><type>xs:string</type><ordering>1</ordering><mandatory/>" +
                "</outputParam>" +
                "</params></taskInfo></response>";
        TaskInformation taskinfo =
                new InterfaceB_EnvironmentBasedClient("").
                parseTaskInformation(thestring);
        assertTrue(taskinfo != null);
    }
}
