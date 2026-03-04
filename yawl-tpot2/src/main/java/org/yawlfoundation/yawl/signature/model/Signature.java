/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.signature.model;

import java.util.List;

/**
 * Runtime signature representation.
 *
 * <p>Contains metadata extracted from signature template annotations
 * and provides methods for validation and LLM interaction.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class Signature {
    
    private final String description;
    private final List<InputField> inputs;
    private final List<OutputField> outputs;
    
    public Signature(String description, List<InputField> inputs, List<OutputField> outputs) {
        this.description = description;
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
    }
    
    public String getDescription() {
        return description;
    }
    
    public List<InputField> getInputs() {
        return inputs;
    }
    
    public List<OutputField> getOutputs() {
        return outputs;
    }
    
    public static class InputField {
        private final String name;
        private final String description;
        
        public InputField(String name, String description) {
            this.name = name;
            this.description = description;
        }
        
        public String getName() {
            return name;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public static class OutputField {
        private final String name;
        private final String description;
        
        public OutputField(String name, String description) {
            this.name = name;
            this.description = description;
        }
        
        public String getName() {
            return name;
        }
        
        public String getDescription() {
            return description;
        }
    }
}
