package org.yawlfoundation.yawl.datamodelling.model;

import java.util.List;

public record OdcsTable(String name, String description, List<String> columns) {}
