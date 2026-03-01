package org.yawlfoundation.yawl.datamodelling.model;

import java.util.List;

public record OpenApiConversionAnalysis(
    int totalSchemas,
    int convertedSchemas,
    List<String> warnings,
    List<String> unconvertedPaths
) {}
