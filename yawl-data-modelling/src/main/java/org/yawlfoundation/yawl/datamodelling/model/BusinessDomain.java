package org.yawlfoundation.yawl.datamodelling.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BusinessDomain(String name, String description, List<SystemDefinition> systems) {}
