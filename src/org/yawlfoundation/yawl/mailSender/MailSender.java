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

package org.yawlfoundation.yawl.mailSender;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceBWebsideController;

import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.Properties;


public class MailSender extends InterfaceBWebsideController {

    private static final Logger logger = LogManager.getLogger(MailSender.class);


    //private static String _sessionHandle = null;
    /**
     * MailSender is JSP-driven: work items are submitted via Send.jsp which
     * invokes SendEmail() directly with form parameters. Engine-initiated work
     * item events are not supported by this service.
     *
     * @param enabledWorkItem the enabled work item
     * @throws UnsupportedOperationException always - use Send.jsp to trigger email sending
     */
    public void handleEnabledWorkItemEvent(WorkItemRecord enabledWorkItem) {
        throw new UnsupportedOperationException(
                "MailSender does not handle engine-enabled work item events. " +
                "Email sending is triggered via Send.jsp with explicit form parameters.");
    }

    /**
     * MailSender does not track in-flight work items, so there is no
     * cancellation cleanup to perform. This event is not supported.
     *
     * @param workItemRecord the cancelled work item
     * @throws UnsupportedOperationException always
     */
    public void handleCancelledWorkItemEvent(WorkItemRecord workItemRecord) {
        throw new UnsupportedOperationException(
                "MailSender does not handle cancelled work item events.");
    }
    class MyAuthenticator extends Authenticator
    {  
    	String smtpUsername = null;
    	String smtpPassword = null;
     
    	public MyAuthenticator(String username, String password)
    	{
    		smtpUsername = username;
    		smtpPassword = password;
    	}
     
     
    	protected PasswordAuthentication getPasswordAuthentication()
    	{
    		return new PasswordAuthentication(smtpUsername,smtpPassword);
    	}
    }
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/WebMail").forward(req, resp);
    }
    public void SendEmail(String SMTP, String Port, String Login, String password, String To, String Alias, String subject, String content, String filename)
    {
    	String _Pathway = System.getenv("CATALINA_HOME")+ "/webapps/mailSender/files/";
    	try{
			File file = new File(_Pathway, "SMTP.xml");
	        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	        // SOC2 CRITICAL: Disable XXE to prevent external entity injection via SMTP.xml
	        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
	        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
	        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
	        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
	        dbf.setXIncludeAware(false);
	        dbf.setExpandEntityReferences(false);
	        try { dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); }
	        catch (IllegalArgumentException ignored) { /* not supported by this parser */ }
	        try { dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); }
	        catch (IllegalArgumentException ignored) { /* not supported by this parser */ }
	        DocumentBuilder db = dbf.newDocumentBuilder();
	        org.w3c.dom.Document doc = db.parse(file);
	        doc.getDocumentElement().normalize();

	        logger.debug("SMTP config root element: {}", doc.getDocumentElement().getNodeName());
	        NodeList nodeLst = doc.getElementsByTagName("SMTP");
	        for (int s = 0; s < nodeLst.getLength(); s++) {

	            Node fstNode = nodeLst.item(s);

	            if (fstNode.getNodeType() == Node.ELEMENT_NODE) {

	              org.w3c.dom.Element fstElmnt = (org.w3c.dom.Element) fstNode;

	              NodeList fstNmElmntLst = fstElmnt.getElementsByTagName("SMTP_Address");
	              org.w3c.dom.Element fstNmElmnt = (org.w3c.dom.Element) fstNmElmntLst.item(0);
	              NodeList fstNm = fstNmElmnt.getChildNodes();
				  SMTP = ((Node) fstNm.item(0)).getNodeValue();
	              logger.debug("SMTP_Address from config: {}", SMTP);

	              NodeList lstNmElmntLst = fstElmnt.getElementsByTagName("Port");
	              org.w3c.dom.Element lstNmElmnt = (org.w3c.dom.Element) lstNmElmntLst.item(0);
	              NodeList lstNm = lstNmElmnt.getChildNodes();
				  Port=((Node) lstNm.item(0)).getNodeValue();
	              logger.debug("Port from config: {}", Port);
	            }
	      }
    	}catch  (Exception e){
    	    // XML parsing failed - fall back to method parameters
    	    // This is acceptable: method already received SMTP/Port as parameters
    	    logger.info("Could not load SMTP configuration from XML (using provided parameters): {}",
    	            e.getMessage());
    	}

    	 Properties props = new Properties();
         props.setProperty("mail.smtp.host", SMTP);
         props.setProperty("mail.smtp.port", Port);
         props.setProperty("mail.smtp.auth", "true");
         // SOC2 CRITICAL: SMTP debug logging is disabled in production to prevent credential
         // leakage. SMTP debug mode logs the full SMTP session including AUTH commands with
         // base64-encoded credentials in plaintext to stdout.
         props.setProperty("mail.debug", "false");
         props.setProperty("mail.smtp.starttls.enable", "true");

        if(Port.compareTo("25") != 0 ){
         props.setProperty("mail.smtp.socketFactory.port", Port);
         props.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
         props.setProperty("mail.smtp.socketFactory.fallback", "false");
        }

        logger.debug("Sending email to: {}, subject: {}, SMTP: {}:{}", To, subject, SMTP, Port);
     try{
         MyAuthenticator auth = new MyAuthenticator (Login,password);
         Session session;
         session = Session.getDefaultInstance(props, auth);
         // SOC2 CRITICAL: Session debug mode disabled; would log plaintext SMTP traffic
         // including credentials. Never enable in production.
         session.setDebug(false);
         Message message;
         message = new MimeMessage(session);
         InternetAddress addressFrom = new InternetAddress(Alias);
         message.setFrom(addressFrom);
         message.setSentDate(new java.util.Date());
         InternetAddress addressTo = new InternetAddress(To);
         message.addRecipient(Message.RecipientType.TO, addressTo);
         message.setSubject(subject);
         // create and fill the first message part
         BodyPart MessageContent = new MimeBodyPart();
         MessageContent.setText(content);

         // set the message body of email
         final Multipart mimemultipart = new MimeMultipart();
         mimemultipart.addBodyPart(MessageContent);
         if(filename != null) {
             MimeBodyPart mimebodypart1 = new MimeBodyPart();
             FileDataSource filedatasource = new FileDataSource(System.getenv("CATALINA_HOME") + "/webapps/mailSender/files/" + filename);
             mimebodypart1.setDataHandler(new DataHandler(filedatasource));
             mimebodypart1.setFileName(filename);
             mimebodypart1.setDisposition("inline");
             logger.debug("Attachment encoding: {}", mimebodypart1.getEncoding());
             mimemultipart.addBodyPart(mimebodypart1);
         }
         message.setContent(mimemultipart);

         Transport.send(message);
         logger.info("Email sent successfully to: {}, subject: {}", To, subject);
     }catch (Exception e) {
         logger.error("Failed to send email to: {}, subject: {}", To, subject, e);
         throw new IllegalStateException("Email delivery failed to recipient '" + To + "': " + e.getMessage(), e);
     }
    }
}
