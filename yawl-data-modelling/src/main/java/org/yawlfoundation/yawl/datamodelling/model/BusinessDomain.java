package org.yawlfoundation.yawl.datamodelling.model;

import java.util.List;

public record BusinessDomain(String name, String description, List<SystemDefinition> systems) {}
