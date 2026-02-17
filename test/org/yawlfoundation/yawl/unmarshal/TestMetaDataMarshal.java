package org.yawlfoundation.yawl.unmarshal;

import org.yawlfoundation.yawl.elements.YSpecVersion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 *
 * @author Lachlan Aldred
 * Date: 4/08/2005
 * Time: 08:27:49
 *
 */
class TestMetaDataMarshal{
    private YMetaData metaData;
    private LocalDate today;
    private LocalDate tomorrow;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @BeforeEach

    void setUp(){
        today = LocalDate.now();
        tomorrow = today.plusDays(1);
        metaData = new YMetaData();
        metaData.addContributor("Lachlan Aldred");
        metaData.addContributor("Arthur ter Hofstede");
        metaData.addContributor("Lindsay Bradford");
        metaData.addContributor("Guy Redding");
        metaData.setCoverage("covers this example test");
        metaData.setCreated(today);
        metaData.addCreator("Peter Pan");
        metaData.setDescription("This tests the metadata class");
        metaData.setStatus("This is not production class meta data");
        metaData.addSubject("testing");
        metaData.addSubject("and more testing");
        metaData.setTitle("Meta Data Test");
        metaData.setValidFrom(today);
        metaData.setValidUntil(tomorrow);
        metaData.setVersion(new YSpecVersion("1.1"));
    }

    @Test

    void testToXML(){
        String todayStr = DATE_FORMAT.format(today);
        String tomorrowStr = DATE_FORMAT.format(tomorrow);
        assertEquals("<metaData>" +
                "<title>Meta Data Test</title>" +
                "<creator>Peter Pan</creator>" +
                "<subject>testing</subject>" +
                "<subject>and more testing</subject>" +
                "<description>This tests the metadata class</description>" +
                "<contributor>Lachlan Aldred</contributor>" +
                "<contributor>Arthur ter Hofstede</contributor>" +
                "<contributor>Lindsay Bradford</contributor>" +
                "<contributor>Guy Redding</contributor>" +
                "<coverage>covers this example test</coverage>" +
                "<validFrom>" + todayStr + "</validFrom>" +
                "<validUntil>" + tomorrowStr + "</validUntil>" +
                "<created>" + todayStr + "</created>" +
                "<version>1.1</version>" +
                "<status>This is not production class meta data</status>" +
                "<persistent>false</persistent>" +
                "</metaData>",
                metaData.toXML());
    }
}
