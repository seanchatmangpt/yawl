package org.yawlfoundation.yawl.resourcing;

import org.yawlfoundation.yawl.resourcing.util.PluginFactory;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.util.JDOMUtil;
import org.jdom2.Document;
import org.yawlfoundation.yawl.util.YPluginLoader;

/**
 *
 * @author Michael Adams
 * Date: 01/08/2007
 *
 */
class TestGetSelectors {

    @Test

    void testGetSelectors() {
         String xml = PluginFactory.getAllSelectors();
        Document doc = JDOMUtil.stringToDocument(xml);
        JDOMUtil.documentToFile(doc, "c:/temp/selectors.xml");
    }
}
