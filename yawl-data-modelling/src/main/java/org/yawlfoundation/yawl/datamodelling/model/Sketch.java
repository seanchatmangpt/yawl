package org.yawlfoundation.yawl.datamodelling.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Sketch(String name, SketchType type, String description, String content) {}
