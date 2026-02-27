/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

/**
 * Thrown when a POWL model cannot be parsed from LLM text output.
 */
public class PowlParseException extends Exception {
    private final String rawText;

    public PowlParseException(String message, String rawText) {
        super(message);
        this.rawText = rawText;
    }

    public PowlParseException(String message, String rawText, Throwable cause) {
        super(message, cause);
        this.rawText = rawText;
    }

    /** Returns the raw LLM text that could not be parsed. */
    public String getRawText() { return rawText; }
}
