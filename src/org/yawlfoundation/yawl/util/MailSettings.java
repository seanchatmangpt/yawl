package org.yawlfoundation.yawl.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.simplejavamail.api.mailer.config.TransportStrategy;

import java.io.Serializable;

/**
 * Immutable mail settings record for SMTP configuration.
 * Converted to Java 25 record for improved immutability and type safety.
 *
 * @author Michael Adams
 * @author YAWL Foundation (Java 25 conversion)
 * @since 2.0
 * @version 5.2
 *
 * @param host SMTP server host
 * @param port SMTP server port
 * @param strategy Transport strategy (SMTPS, SMTP_TLS, etc.)
 * @param user SMTP authentication username
 * @param password SMTP authentication password
 * @param fromName Sender name
 * @param fromAddress Sender email address
 * @param toName Recipient name
 * @param toAddress Recipient email address
 * @param ccAddress CC email address
 * @param bccAddress BCC email address
 * @param subject Email subject
 * @param content Email content/body
 */
public record MailSettings(
    String host,
    int port,
    TransportStrategy strategy,
    String user,
    String password,
    String fromName,
    String fromAddress,
    String toName,
    String toAddress,
    String ccAddress,
    String bccAddress,
    String subject,
    String content
) implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private static final Logger logger = LogManager.getLogger(MailSettings.class);

    /**
     * Default constructor with standard defaults.
     */
    public MailSettings() {
        this(null, 25, TransportStrategy.SMTPS, null, null, null, null,
             null, null, null, null, null, null);
    }


    /**
     * Gets a setting value by name.
     * @param name the setting name
     * @return the setting value, or null if not found
     */
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

    /**
     * Creates a copy of this mail settings (since records are immutable, returns this).
     * @deprecated Records are immutable; use this instance directly
     * @return this instance
     */
    @Deprecated
    public MailSettings copyOf() {
        logger.debug("MailSettings.copyOf() called (returning this since records are immutable)");
        return this;
    }

    /**
     * Converts to XML representation.
     * @return XML string
     */
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

    /**
     * Creates a MailSettings instance from XML.
     * @param xml XML string
     * @return new MailSettings instance
     */
    public static MailSettings fromXML(String xml) {
        logger.debug("MailSettings.fromXML({})", xml);
        XNode node = new XNodeParser().parse(xml);
        if (node == null) {
            logger.debug("fromXML() returning default");
            return new MailSettings();
        }

        MailSettings settings = new MailSettings(
            node.getChildText("host"),
            StringUtil.strToInt(node.getChildText("port"), 25),
            TransportStrategy.SMTPS,  // Default strategy
            node.getChildText("user"),
            node.getChildText("password"),
            node.getChildText("fromname"),
            node.getChildText("fromaddress"),
            node.getChildText("toname"),
            node.getChildText("toaddress"),
            node.getChildText("CC"),
            node.getChildText("BCC"),
            node.getChildText("subject", true),
            node.getChildText("content", true)
        );
        logger.debug("fromXML() returning {}", settings.toXML());
        return settings;
    }

    /**
     * Creates a new MailSettings with updated host.
     * @param newHost the new host
     * @return a new MailSettings instance
     */
    public MailSettings withHost(String newHost) {
        return new MailSettings(newHost, port, strategy, user, password, fromName,
                               fromAddress, toName, toAddress, ccAddress, bccAddress,
                               subject, content);
    }

    /**
     * Creates a new MailSettings with updated credentials.
     * @param newUser the new username
     * @param newPassword the new password
     * @return a new MailSettings instance
     */
    public MailSettings withCredentials(String newUser, String newPassword) {
        return new MailSettings(host, port, strategy, newUser, newPassword, fromName,
                               fromAddress, toName, toAddress, ccAddress, bccAddress,
                               subject, content);
    }

    /**
     * Creates a new MailSettings with updated recipient.
     * @param newToName the new recipient name
     * @param newToAddress the new recipient address
     * @return a new MailSettings instance
     */
    public MailSettings withRecipient(String newToName, String newToAddress) {
        return new MailSettings(host, port, strategy, user, password, fromName,
                               fromAddress, newToName, newToAddress, ccAddress, bccAddress,
                               subject, content);
    }

    /**
     * Creates a new MailSettings with updated subject and content.
     * @param newSubject the new subject
     * @param newContent the new content
     * @return a new MailSettings instance
     */
    public MailSettings withMessage(String newSubject, String newContent) {
        return new MailSettings(host, port, strategy, user, password, fromName,
                               fromAddress, toName, toAddress, ccAddress, bccAddress,
                               newSubject, newContent);
    }
}

