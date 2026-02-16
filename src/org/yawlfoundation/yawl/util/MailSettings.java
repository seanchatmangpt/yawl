package org.yawlfoundation.yawl.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.simplejavamail.mailer.config.TransportStrategy;

/**
 *
 * @author Michael Adams
 * @date 18/12/2024
 */
public class MailSettings {
    private Logger _logger = LogManager.getLogger(getClass());

    private static final Logger logger = LogManager.getLogger(MailSettings.class);
    
    public String host = null;
    public int port = 25;
    public TransportStrategy strategy = TransportStrategy.SMTPS;
    public String user = null;
    public String password = null;
    public String fromName = null;
    public String fromAddress = null;
    public String toName = null;
    public String toAddress = null;
    public String ccAddress = null;
    public String bccAddress = null;
    public String subject = null;
    public String content = null;

    public MailSettings() { }


    public String getSetting(String name) {
        return switch (name) {
            case "host" -> host;
            case "user" -> user;
            case "password" -> password;
            case "senderName" -> fromName;
            case "senderAddress" -> fromAddress;
            case "recipientName" -> toName;
            case "recipientAddress" -> toAddress;
            case "CC" -> ccAddress;
            case "BCC" -> bccAddress;
            case "subject" -> subject;
            case "content" -> content;
            default -> null;
        };
    }


    public MailSettings copyOf() {
        _logger.debug("enter MailSettings.copyOf()");
        MailSettings settings = new MailSettings();
        settings.host = this.host;
        settings.port = this.port;
        settings.strategy = this.strategy;
        settings.user = this.user;
        settings.password = this.password;
        settings.fromName = this.fromName;
        settings.fromAddress = this.fromAddress;
        settings.toName = this.toName;
        settings.toAddress = this.toAddress;
        settings.ccAddress = this.ccAddress;
        settings.bccAddress = this.bccAddress;
        settings.subject = this.subject;
        settings.content = this.content;
        _logger.debug("copyOf() returning " + settings.toXML());
        return settings;
    }


    public String toXML() {
        XNode node = new XNode("mailsettings");
        node.addChild("host", host);
        node.addChild("port", port);
        node.addChild( "user", user);
        node.addChild( "password", password);
        node.addChild( "fromname", fromName);
        node.addChild( "fromaddress", fromAddress);
        node.addChild( "toname", toName);
        node.addChild( "toaddress", toAddress);
        node.addChild( "CC", ccAddress);
        node.addChild( "BCC", bccAddress);
        node.addChild( "subject", subject, true);
        node.addChild( "content", content, true);
        return node.toString();
    }


    public void fromXML(String xml) {
        _logger.debug(String.format("enter fromXML(%s)", xml));
        XNode node = new XNodeParser().parse(xml);
        if (node != null) {
            host = node.getChildText("host");
            port = StringUtil.strToInt(node.getChildText("port"), 25);
            user = node.getChildText("user");
            password = node.getChildText("password");
            fromName = node.getChildText("fromname");
            fromAddress = node.getChildText("fromaddress");
            toName = node.getChildText("toname");
            toAddress = node.getChildText("toaddress");
            ccAddress = node.getChildText("CC");
            bccAddress = node.getChildText("BCC");
            subject = node.getChildText("subject", true);
            content = node.getChildText("content", true);
        }
        _logger.debug("returning from fromXML()");
    }
}

