package org.yawlfoundation.yawl.datamodelling.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkspaceModel(String name, String description, List<OdcsTable> tables) {}
