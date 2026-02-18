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

package org.yawlfoundation.yawl.authentication;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.jdom2.Element;
import org.yawlfoundation.yawl.util.XNode;

/**
 * Base class that defines a custom service or external client application, in
 * particular their session authentication credentials.
 * <p/>
 * Known child classes: YExternalClient, YAWLServiceReference
 *
 * @author Michael Adams
 * @since 2.1
 * @date 11/05/2010
 */
@MappedSuperclass
public class YClient {

    @Id
    @Column(name = "user_name", length = 255)
    protected String _userName;

    @Column(name = "password", length = 255)
    protected String _password;

    @Column(name = "documentation", length = 1000)
    protected String _documentation;

    public YClient() {}

    public YClient(String userID, String password, String documentation) {
        _userName = userID;
        _password = password;
        _documentation = documentation;
    }

    public YClient(Element xml) {
        _userName = xml.getChildText("username");
        _password = xml.getChildText("password");
        _documentation = xml.getChildText("documentation");
    }


    public String getUserName() { return _userName; }

    public void setUserName(String userID) { _userName = userID; }


    public String getPassword() { return _password; }

    public void setPassword(String password) { _password = password; }


    public String getDocumentation() { return _documentation; }

    public void setDocumentation(String documentation) { _documentation = documentation; }


    public boolean equals(Object other) {
        return other instanceof YClient client &&
                ((getUserName() != null) ?
                  getUserName().equals(client.getUserName()) :
                        super.equals(other));
    }

    public int hashCode() {
        return (getUserName() != null) ? getUserName().hashCode() : super.hashCode();
    }

    /**
     * Serializes this client to XML format.
     * <p>
     * SECURITY NOTE: Passwords are redacted in XML output to prevent credential exposure
     * in logs and debugging output. The password field is represented as "[REDACTED]"
     * rather than the actual value.
     * </p>
     *
     * @return XML representation of this client with redacted password
     */
    public String toXML() {
        XNode root = new XNode("client");
        root.addChild("username", _userName);
        root.addChild("password", "[REDACTED]");
        root.addChild("documentation", _documentation);
        return root.toString();
    }


}
