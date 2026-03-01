package org.yawlfoundation.yawl.datamodelling.model;

import java.util.List;

public record WorkspaceModel(String name, String description, List<OdcsTable> tables) {}
