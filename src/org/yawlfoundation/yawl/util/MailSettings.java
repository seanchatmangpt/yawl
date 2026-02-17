package org.yawlfoundation.yawl.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.simplejavamail.api.mailer.config.TransportStrategy;

/**
 *
 * @author Michael Adams
 * @date 18/12/2024
 */
public class MailSettings {
    private static final Logger _logger = LogManager.getLogger(MailSettings.class);

    private String host;
    private int port;
    private TransportStrategy strategy;
    private String user;
    private String password;
    private String fromName;
    private String fromAddress;
    private String toName;
    private String toAddress;
    private String ccAddress;
    private String bccAddress;
    private String subject;
    private String content;

    public MailSettings() {
        this.port = 25;
        this.strategy = TransportStrategy.SMTPS;
    }

    public MailSettings(String host, int port, TransportStrategy strategy,
                        String user, String password,
                        String fromName, String fromAddress,
                        String toName, String toAddress,
                        String ccAddress, String bccAddress,
                        String subject, String content) {
        this.host = host;
        this.port = port;
        this.strategy = strategy;
        this.user = user;
        this.password = password;
        this.fromName = fromName;
        this.fromAddress = fromAddress;
        this.toName = toName;
        this.toAddress = toAddress;
        this.ccAddress = ccAddress;
        this.bccAddress = bccAddress;
        this.subject = subject;
        this.content = content;
    }

    public String host() { return host; }
    public int port() { return port; }
    public TransportStrategy strategy() { return strategy; }
    public String user() { return user; }
    public String password() { return password; }
    public String fromName() { return fromName; }
    public String fromAddress() { return fromAddress; }
    public String toName() { return toName; }
    public String toAddress() { return toAddress; }
    public String ccAddress() { return ccAddress; }
    public String bccAddress() { return bccAddress; }
    public String subject() { return subject; }
    public String content() { return content; }

    public void setHost(String host) { this.host = host; }
    public void setPort(int port) { this.port = port; }
    public void setStrategy(TransportStrategy strategy) { this.strategy = strategy; }
    public void setUser(String user) { this.user = user; }
    public void setPassword(String password) { this.password = password; }
    public void setFromName(String fromName) { this.fromName = fromName; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }
    public void setToName(String toName) { this.toName = toName; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }
    public void setCcAddress(String ccAddress) { this.ccAddress = ccAddress; }
    public void setBccAddress(String bccAddress) { this.bccAddress = bccAddress; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setContent(String content) { this.content = content; }

    public String getSetting(String name) {
        switch (name) {
            case "host": return host;
            case "user": return user;
            case "password": return password;
            case "senderName": return fromName;
            case "senderAddress": return fromAddress;
            case "recipientName": return toName;
            case "recipientAddress": return toAddress;
            case "CC": return ccAddress;
            case "BCC": return bccAddress;
            case "subject": return subject;
            case "content": return content;
            default: return null;
        }
    }


    public MailSettings copyOf() {
        _logger.debug("enter MailSettings.copyOf()");
        MailSettings settings = new MailSettings(host, port, strategy, user, password,
                fromName, fromAddress, toName, toAddress, ccAddress, bccAddress,
                subject, content);
        _logger.debug("copyOf() returning " + settings.toXML());
        return settings;
    }


    public String toXML() {
        XNode node = new XNode("mailsettings");
        node.addChild("host", host);
        node.addChild("port", port);
        node.addChild("user", user);
        node.addChild("password", password);
        node.addChild("fromname", fromName);
        node.addChild("fromaddress", fromAddress);
        node.addChild("toname", toName);
        node.addChild("toaddress", toAddress);
        node.addChild("CC", ccAddress);
        node.addChild("BCC", bccAddress);
        node.addChild("subject", subject, true);
        node.addChild("content", content, true);
        return node.toString();
    }


    public static MailSettings fromXML(String xml) {
        _logger.debug(String.format("enter fromXML(%s)", xml));
        MailSettings settings = new MailSettings();
        XNode node = new XNodeParser().parse(xml);
        if (node != null) {
            settings.host = node.getChildText("host");
            settings.port = StringUtil.strToInt(node.getChildText("port"), 25);
            settings.user = node.getChildText("user");
            settings.password = node.getChildText("password");
            settings.fromName = node.getChildText("fromname");
            settings.fromAddress = node.getChildText("fromaddress");
            settings.toName = node.getChildText("toname");
            settings.toAddress = node.getChildText("toaddress");
            settings.ccAddress = node.getChildText("CC");
            settings.bccAddress = node.getChildText("BCC");
            settings.subject = node.getChildText("subject", true);
            settings.content = node.getChildText("content", true);
        }
        _logger.debug("returning from fromXML()");
        return settings;
    }
}

