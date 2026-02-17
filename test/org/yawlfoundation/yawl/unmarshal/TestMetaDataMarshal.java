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
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @BeforeEach

    void setUp(){
        metaData = new YMetaData();
        metaData.addContributor("Lachlan Aldred");
        metaData.addContributor("Arthur ter Hofstede");
        metaData.addContributor("Lindsay Bradford");
        metaData.addContributor("Guy Redding");
        metaData.setCoverage("covers this example test");
        metaData.setCreated(LocalDate.now());
        metaData.addCreator("Peter Pan");
        metaData.setDescription("This tests the metadata class");
        metaData.setStatus("This is not production class meta data");
        metaData.addSubject("testing");
        metaData.addSubject("and more testing");
        metaData.setTitle("Meta Data Test");
        metaData.setValidFrom(LocalDate.now());
        metaData.setValidUntil(LocalDate.now().plusDays(1));
        metaData.setVersion(new YSpecVersion("1.1"));
    }

    @Test

    void testToXML(){
        String today = DATE_FORMAT.format(LocalDate.now());
        String tomorrow = DATE_FORMAT.format(LocalDate.now().plusDays(1));
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
                "<validFrom>" + today + "</validFrom>" +
                "<validUntil>" + tomorrow + "</validUntil>" +
                "<created>" + today + "</created>" +
                "<version>1.1</version>" +
                "<status>This is not production class meta data</status>" +
                "<persistent>false</persistent>" +
                "</metaData>",
                metaData.toXML());
    }
}
