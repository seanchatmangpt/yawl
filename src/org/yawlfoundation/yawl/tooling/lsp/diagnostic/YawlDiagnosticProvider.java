/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
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

package org.yawlfoundation.yawl.tooling.lsp.diagnostic;

import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YSpecificationValidator;
import org.yawlfoundation.yawl.elements.YSpecificationValidator.ValidationError;
import org.yawlfoundation.yawl.schema.SchemaHandler;
import org.yawlfoundation.yawl.schema.YSchemaVersion;
import org.yawlfoundation.yawl.unmarshal.YMarshal;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Validates a YAWL specification document and produces LSP Diagnostic records.
 *
 * Two validation passes are performed:
 * <ol>
 *   <li>XSD schema validation via {@link SchemaHandler} — produces parse-level
 *       diagnostics with approximate line numbers extracted from SAX error messages.</li>
 *   <li>Semantic validation via {@link YSpecificationValidator} — produces
 *       structural diagnostics (element-level, without line numbers since the
 *       semantic model is post-parse).</li>
 * </ol>
 *
 * All diagnostics are emitted in LSP format via {@link Diagnostic#toJson()}.
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
public class YawlDiagnosticProvider {

    private static final Logger logger = Logger.getLogger(YawlDiagnosticProvider.class.getName());

    /**
     * LSP DiagnosticSeverity values.
     */
    public enum Severity {
        ERROR(1), WARNING(2), INFORMATION(3), HINT(4);

        private final int lspCode;
        Severity(int lspCode) { this.lspCode = lspCode; }
        public int lspCode() { return lspCode; }
    }

    /**
     * Validate the given specification text and return LSP diagnostics.
     *
     * @param documentText full XML text of the specification (may be null or blank)
     * @return ordered list of diagnostics (may be empty, never null)
     */
    public List<Diagnostic> validate(String documentText) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        if (documentText == null || documentText.isBlank()) {
            diagnostics.add(Diagnostic.error(0, 0, 0, 0,
                    "Document is empty", "yawl-schema"));
            return diagnostics;
        }

        // Pass 1: XSD validation
        SchemaHandler schemaHandler = new SchemaHandler(YSchemaVersion.defaultVersion().getSchemaURL());
        boolean schemaValid = schemaHandler.compileAndValidate(documentText);

        if (!schemaValid) {
            for (String schemaError : schemaHandler.getErrorMessages()) {
                DiagnosticPosition pos = extractPositionFromSchemaError(schemaError);
                diagnostics.add(new Diagnostic(
                        pos.startLine(), pos.startChar(),
                        pos.endLine(),   pos.endChar(),
                        cleanSchemaErrorMessage(schemaError),
                        Severity.ERROR,
                        "yawl-xsd"));
            }
            // Schema errors prevent meaningful semantic validation
            return diagnostics;
        }

        // Pass 2: Semantic validation
        List<YSpecification> specs;
        try {
            specs = YMarshal.unmarshalSpecifications(documentText, false);
        } catch (Exception e) {
            diagnostics.add(Diagnostic.error(0, 0, 0, 0,
                    "Parse error: " + e.getMessage(), "yawl-parse"));
            return diagnostics;
        }

        for (YSpecification spec : specs) {
            YSpecificationValidator validator = new YSpecificationValidator(spec);
            validator.validate();

            for (ValidationError error : validator.getErrors()) {
                // Semantic errors reference element IDs, not line numbers.
                // Use line 0 with the element ID as source so VS Code
                // shows the error in the Problems panel with context.
                diagnostics.add(Diagnostic.error(0, 0, 0, Integer.MAX_VALUE,
                        formatSemanticMessage(error), "yawl-semantic"));
            }
        }

        logger.fine("Validation produced " + diagnostics.size() + " diagnostics");
        return diagnostics;
    }

    // ---- Helpers -------------------------------------------------------------

    /**
     * Extract line/column numbers from a SAX schema error message.
     * SAX messages typically contain "lineNumber: N; columnNumber: M".
     */
    private DiagnosticPosition extractPositionFromSchemaError(String message) {
        int line = 0;
        int col  = 0;

        int lineIdx = message.indexOf("lineNumber:");
        if (lineIdx >= 0) {
            int start = lineIdx + "lineNumber:".length();
            int end   = message.indexOf(';', start);
            String num = end >= 0 ? message.substring(start, end).trim()
                                  : message.substring(start).trim();
            try { line = Math.max(0, Integer.parseInt(num) - 1); }
            catch (NumberFormatException ignored) { line = 0; }
        }

        int colIdx = message.indexOf("columnNumber:");
        if (colIdx >= 0) {
            int start = colIdx + "columnNumber:".length();
            int end   = message.indexOf(';', start);
            String num = end >= 0 ? message.substring(start, end).trim()
                                  : message.substring(start).trim();
            try { col = Math.max(0, Integer.parseInt(num) - 1); }
            catch (NumberFormatException ignored) { col = 0; }
        }

        return new DiagnosticPosition(line, col, line, col + 1);
    }

    /**
     * Strip SAX position information from the error message for cleaner display.
     */
    private String cleanSchemaErrorMessage(String raw) {
        // Remove "cvc-..." prefix identifiers that are noisy for developers
        String msg = raw;
        int colon = msg.indexOf(':');
        if (colon > 0 && colon < 15 && !msg.substring(0, colon).contains(" ")) {
            msg = msg.substring(colon + 1).trim();
        }
        // Trim SAX location suffix
        int linePart = msg.indexOf("lineNumber:");
        if (linePart > 0) {
            msg = msg.substring(0, linePart).trim();
            if (msg.endsWith(";")) msg = msg.substring(0, msg.length() - 1).trim();
        }
        return msg;
    }

    private String formatSemanticMessage(ValidationError ve) {
        String elementId  = ve.getElementID();
        String elementRef = (elementId != null && !elementId.isBlank())
                ? " [element: " + elementId + "]"
                : "";
        return ve.getMessage() + elementRef;
    }

    // ---- Inner types ---------------------------------------------------------

    private record DiagnosticPosition(int startLine, int startChar, int endLine, int endChar) { }

    /**
     * Immutable LSP Diagnostic with JSON serialisation.
     */
    public record Diagnostic(
            int startLine,
            int startChar,
            int endLine,
            int endChar,
            String message,
            Severity severity,
            String source
    ) {
        static Diagnostic error(int sl, int sc, int el, int ec, String msg, String source) {
            return new Diagnostic(sl, sc, el, ec, msg, Severity.ERROR, source);
        }

        static Diagnostic warning(int sl, int sc, int el, int ec, String msg, String source) {
            return new Diagnostic(sl, sc, el, ec, msg, Severity.WARNING, source);
        }

        /** Serialise to LSP Diagnostic JSON. */
        public String toJson() {
            String escapedMsg = message.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", " ").replace("\r", "");
            // Clamp endChar to a reasonable max for full-line underlines
            int clampedEndChar = Math.min(endChar, 10000);
            return """
                    {"range":{"start":{"line":%d,"character":%d},"end":{"line":%d,"character":%d}},\
                    "severity":%d,"source":"%s","message":"%s"}""".formatted(
                    startLine, startChar, endLine, clampedEndChar,
                    severity.lspCode(), source, escapedMsg);
        }
    }
}
