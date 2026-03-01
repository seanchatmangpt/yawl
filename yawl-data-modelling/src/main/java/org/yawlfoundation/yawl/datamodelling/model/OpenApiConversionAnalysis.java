package org.yawlfoundation.yawl.datamodelling.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenApiConversionAnalysis(
    int totalSchemas,
    int convertedSchemas,
    List<String> warnings,
    List<String> unconvertedPaths
) {}
