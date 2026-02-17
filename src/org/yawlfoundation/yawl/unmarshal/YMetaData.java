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

package org.yawlfoundation.yawl.unmarshal;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.yawlfoundation.yawl.elements.YSpecVersion;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 * Holds the Specification Metadata
 *
 * 
 * @author Lachlan Aldred
 * Date: 3/08/2005
 * Time: 18:47:47
 * 
 */
public class YMetaData {

  	static final String INITIAL_VERSION = "0.1";
    public static final DateTimeFormatter dateFormat =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private String title;
    private List<String> creators = new ArrayList<String>();
    private List<String> subjects = new ArrayList<String>();
    private String description;
    private List<String> contributors = new ArrayList<String>();
    private String coverage;
    private LocalDate validFrom;
    private LocalDate validUntil;
    private LocalDate created;
    private YSpecVersion version = new YSpecVersion(INITIAL_VERSION);
    private String status;
	  private boolean persistent;
    private String uniqueID = null;                            // null for pre-2.0 specs

    public YMetaData() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCoverage() {
        return coverage;
    }

    public void setCoverage(String coverage) {
        this.coverage = coverage;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public LocalDate getCreated() {
        return created;
    }

    public void setCreated(LocalDate created) {
        this.created = created;
    }

    public YSpecVersion getVersion() {
        return version;
    }

    public void setVersion(YSpecVersion version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getCreators() {
        return creators;
    }

    public List<String> getSubjects() {
        return subjects;
    }

    public List<String> getContributors() {
        return contributors;
    }

    public void setCreators(List<String> creators) {
        this.creators = creators;
    }

    public void setSubjects(List<String> subjects) {
        this.subjects = subjects;
    }

    public void setContributors(List<String> contributors) {
        this.contributors = contributors;
    }

    public void addCreator(String creator) {
        this.creators.add(creator);
    }

    public void addSubject(String subject) {
        this.subjects.add(subject);
    }

    public void addContributor(String contributor) {
        this.contributors.add(contributor);
    }

    public boolean isPersistent() {
        return persistent;
    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    public String getUniqueID() {
        return uniqueID;
    }

    public void setUniqueID(String uniqueID) {
        this.uniqueID = uniqueID;
    }

    public String toXML() {
        String titleXML = this.title != null ? StringUtil.wrapEscaped(title, "title") : "";
        String creatorsXML = creators.stream()
            .map(c -> StringUtil.wrapEscaped(c, "creator"))
            .collect(java.util.stream.Collectors.joining());
        String subjectsXML = subjects.stream()
            .map(s -> StringUtil.wrapEscaped(s, "subject"))
            .collect(java.util.stream.Collectors.joining());
        String descriptionXML = description != null ? StringUtil.wrapEscaped(description, "description") : "";
        String contributorsXML = contributors.stream()
            .map(c -> StringUtil.wrapEscaped(c, "contributor"))
            .collect(java.util.stream.Collectors.joining());
        String coverageXML = coverage != null ? StringUtil.wrapEscaped(coverage, "coverage") : "";
        String validFromXML = validFrom != null ? StringUtil.wrap(dateFormat.format(validFrom), "validFrom") : "";
        String validUntilXML = validUntil != null ? StringUtil.wrap(dateFormat.format(validUntil), "validUntil") : "";
        String createdXML = created != null ? StringUtil.wrap(dateFormat.format(created), "created") : "";
        String statusXML = status != null ? StringUtil.wrap(status, "status") : "";
        String identifierXML = uniqueID != null ? StringUtil.wrap(uniqueID, "identifier") : "";

        return "<metaData>%s%s%s%s%s%s%s%s%s%s%s%s%s</metaData>".formatted(
                titleXML,
                creatorsXML,
                subjectsXML,
                descriptionXML,
                contributorsXML,
                coverageXML,
                validFromXML,
                validUntilXML,
                createdXML,
                StringUtil.wrap(version.toString(), "version"),
                statusXML,
                StringUtil.wrap(String.valueOf(persistent), "persistent"),
                identifierXML
            );
    }
}
