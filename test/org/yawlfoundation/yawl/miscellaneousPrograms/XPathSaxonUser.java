package org.yawlfoundation.yawl.miscellaneousPrograms;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;

/**
 * Example of using Saxon S9API for XQuery evaluation.
 * Updated to use the modern Saxon S9API (replaces deprecated Saxon 9.0 API).
 *
 * @author Lachlan Aldred
 * Date: 11/02/2004
 * Time: 15:34:41
 *
 */
public class XPathSaxonUser {
    public static void main(String[] args) {
        Processor processor = new Processor(false);
        DocumentBuilder builder = processor.newDocumentBuilder();
        XQueryCompiler compiler = processor.newXQueryCompiler();

        try {
            XQueryExecutable exp = compiler.compile("generate-id(/bye_mum/hi_there)");
            XdmNode doc = builder.build(new StreamSource(new StringReader(
                    "<bye_mum inf='h'><hi_there/></bye_mum>")));
            XQueryEvaluator evaluator = exp.load();
            evaluator.setContextItem(doc);
            XdmValue result = evaluator.evaluate();
            System.out.println("result = " + result);
        } catch (SaxonApiException e) {
            e.printStackTrace();
        }
    }
}
