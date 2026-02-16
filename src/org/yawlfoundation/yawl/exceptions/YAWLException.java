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

package org.yawlfoundation.yawl.exceptions;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced YAWL exception with detailed context and troubleshooting guidance.
 *
 * @author Lachlan Aldred
 * Date: 26/11/2004
 * Time: 15:26:54
 */
public class YAWLException extends Exception {
    protected static SAXBuilder _builder = new SAXBuilder();
    protected String _message;
    public static final String MESSAGE_NM = "message";

    private final Map<String, String> _context = new HashMap<>();
    private String _troubleshootingGuide;

    public YAWLException() {
    }

    public YAWLException(String message) {
        _message = message;
    }

    /**
     * Constructs a new exception with the specified cause and a detail
     * message of <tt>(cause==null ? null : cause.toString())</tt> (which
     * typically contains the class and detail message of <tt>cause</tt>).
     * This constructor is useful for exceptions that are little more than
     * wrappers for other throwables (for example, {@link
     * java.security.PrivilegedActionException}).
     *
     * @param cause the cause (which is saved for later retrieval by the
     *              {@link #getCause()} method).  (A <tt>null</tt> value is
     *              permitted, and indicates that the cause is nonexistent or
     *              unknown.)
     * @since 1.4
     */
    public YAWLException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new exception with the specified detail message and
     * cause.  <p>Note that the detail message associated with
     * <code>cause</code> is <i>not</i> automatically incorporated in
     * this exception's detail message.
     *
     * @param message the detail message (which is saved for later retrieval
     *                by the {@link #getMessage()} method).
     * @param cause   the cause (which is saved for later retrieval by the
     *                {@link #getCause()} method).  (A <tt>null</tt> value is
     *                permitted, and indicates that the cause is nonexistent or
     *                unknown.)
     * @since 1.4
     */
    public YAWLException(String message, Throwable cause) {
        super(cause);
        _message = message;
    }

    public String getMessage() {
        return _message;
    }

    public String toXML() {
        return """
            <%s>%s</%s>
            """.formatted(this.getClass().getName(), toXMLGuts(), this.getClass().getName()).trim();
    }

    protected String toXMLGuts() {
        return "<message>%s</message>".formatted(getMessage());
    }

    public static YAWLException unmarshal(Document exceptionDoc) throws JDOMException, IOException, ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException, java.lang.reflect.InvocationTargetException {

        String exceptionType = exceptionDoc.getRootElement().getName();
        if ("YDataStateException".equals(exceptionType)) {
            return YDataStateException.unmarshall(exceptionDoc);
        }
        if ("YDataQueryException".equals(exceptionType)) {
            return YDataQueryException.unmarshall(exceptionDoc);
        }
        if ("YDataValidationException".equals(exceptionType)) {
            return YDataValidationException.unmarshall(exceptionDoc);
        }
        YAWLException e = (YAWLException) Class.forName(exceptionType).getDeclaredConstructor().newInstance();
        e.setMessage(parseMessage(exceptionDoc));
        return e;
    }

    protected static String parseMessage(Document exceptionDoc) {
        return exceptionDoc.getRootElement().getChildText(MESSAGE_NM);
    }

    public void setMessage(String message) {
        _message = message;
    }

    /**
     * Adds contextual information to this exception.
     * @param key the context key
     * @param value the context value
     * @return this exception for method chaining
     */
    public YAWLException withContext(String key, String value) {
        _context.put(key, value);
        return this;
    }

    /**
     * Adds troubleshooting guidance to this exception.
     * @param guide the troubleshooting guidance message
     * @return this exception for method chaining
     */
    public YAWLException withTroubleshootingGuide(String guide) {
        _troubleshootingGuide = guide;
        return this;
    }

    /**
     * Gets the contextual information associated with this exception.
     * @return an immutable copy of the context map
     */
    public Map<String, String> getContext() {
        return new HashMap<>(_context);
    }

    /**
     * Gets the troubleshooting guidance for this exception.
     * @return the troubleshooting guidance, or null if not set
     */
    public String getTroubleshootingGuide() {
        return _troubleshootingGuide;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        if (!_context.isEmpty()) {
            sb.append("\nContext: ").append(_context);
        }
        if (_troubleshootingGuide != null) {
            sb.append("\nTroubleshooting: ").append(_troubleshootingGuide);
        }
        return sb.toString();
    }

    /**
     * A convenience method that effectively rethrows the exceptions listed.
     * Caveat: ALL 4 exceptions must appear in the throws clause of any methods that
     * call this method.
     * @throws YStateException
     * @throws YDataStateException
     * @throws YQueryException
     * @throws YPersistenceException
     */

    public void rethrow() throws YStateException, YDataStateException, YQueryException,
                                 YPersistenceException
    {
        if (this instanceof YStateException stateEx) {
            throw stateEx;
        } else if (this instanceof YDataStateException dataEx) {
            throw dataEx;
        } else if (this instanceof YQueryException queryEx) {
            throw queryEx;
        } else if (this instanceof YPersistenceException persistEx) {
            throw persistEx;
        }

    }
}
