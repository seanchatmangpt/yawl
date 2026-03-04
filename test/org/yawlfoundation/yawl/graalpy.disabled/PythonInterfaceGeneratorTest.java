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

package org.yawlfoundation.yawl.graalpy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD unit tests for {@link PythonInterfaceGenerator}.
 *
 * <p>Tests interface generation from Python type annotation ({@code .pyi}) files
 * without requiring GraalPy at runtime, making these tests runnable on standard JDK.</p>
 */
@DisplayName("PythonInterfaceGenerator")
class PythonInterfaceGeneratorTest {

    private PythonInterfaceGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new PythonInterfaceGenerator("org.example.generated");
    }

    // ── Basic interface generation ──────────────────────────────────────────────

    @Test
    @DisplayName("generates interface for class with simple method")
    void generatesInterfaceForSimpleClass() {
        String typeAnnotations = """
                class TextAnalyzer:
                    def analyze(self, text: str) -> str: ...
                """;

        String java = generator.generateFromString(typeAnnotations, "TextAnalyzer.pyi");

        assertThat(java, containsString("public interface TextAnalyzer"));
        assertThat(java, containsString("String analyze(String text)"));
        assertThat(java, containsString("package org.example.generated"));
    }

    @Test
    @DisplayName("generates method with dict return type")
    void generatesDictReturnType() {
        String typeAnnotations = """
                class Scorer:
                    def score(self, text: str) -> dict[str, float]: ...
                """;

        String java = generator.generateFromString(typeAnnotations, "Scorer.pyi");

        assertThat(java, containsString("Map<String, Double> score(String text)"));
        assertThat(java, containsString("import java.util.Map"));
    }

    @Test
    @DisplayName("generates method with list return type")
    void generatesListReturnType() {
        String typeAnnotations = """
                class Tokenizer:
                    def tokenize(self, text: str) -> list[str]: ...
                """;

        String java = generator.generateFromString(typeAnnotations, "Tokenizer.pyi");

        assertThat(java, containsString("List<String> tokenize(String text)"));
        assertThat(java, containsString("import java.util.List"));
    }

    @Test
    @DisplayName("generates multiple methods from type annotations")
    void generatesMultipleMethods() {
        String typeAnnotations = """
                class NlpEngine:
                    def classify(self, text: str) -> str: ...
                    def sentiment(self, text: str) -> float: ...
                    def extract_keywords(self, text: str) -> list[str]: ...
                """;

        String java = generator.generateFromString(typeAnnotations, "NlpEngine.pyi");

        assertThat(java, containsString("String classify(String text)"));
        assertThat(java, containsString("double sentiment(String text)"));
        assertThat(java, containsString("List<String> extractKeywords(String text)"));
    }

    @Test
    @DisplayName("converts snake_case method names to camelCase")
    void convertSnakeCaseToCamelCase() {
        String typeAnnotations = """
                class Processor:
                    def process_batch(self, items: list[str]) -> list[str]: ...
                    def get_max_items(self) -> int: ...
                """;

        String java = generator.generateFromString(typeAnnotations, "Processor.pyi");

        assertThat(java, containsString("processBatch("));
        assertThat(java, containsString("getMaxItems()"));
        assertThat(java, not(containsString("process_batch")));
        assertThat(java, not(containsString("get_max_items")));
    }

    @Test
    @DisplayName("skips dunder methods like __init__ and __repr__")
    void skipsDunderMethods() {
        String typeAnnotations = """
                class Model:
                    def __init__(self, path: str) -> None: ...
                    def __repr__(self) -> str: ...
                    def predict(self, x: float) -> float: ...
                """;

        String java = generator.generateFromString(typeAnnotations, "Model.pyi");

        assertThat(java, not(containsString("__init__")));
        assertThat(java, not(containsString("__repr__")));
        assertThat(java, containsString("double predict(double x)"));
    }

    @Test
    @DisplayName("generates getter for public field")
    void generatesGetterForField() {
        String typeAnnotations = """
                class Config:
                    threshold: float
                    def validate(self) -> bool: ...
                """;

        String java = generator.generateFromString(typeAnnotations, "Config.pyi");

        assertThat(java, containsString("double getThreshold()"));
        assertThat(java, containsString("boolean validate()"));
    }

    @Test
    @DisplayName("handles nested generic type dict[str, list[float]]")
    void handlesNestedGenericType() {
        String typeAnnotations = """
                class Aggregator:
                    def group_by_label(self, items: list[str]) -> dict[str, list[float]]: ...
                """;

        String java = generator.generateFromString(typeAnnotations, "Aggregator.pyi");

        assertThat(java, containsString("Map<String, List<Double>> groupByLabel(List<String> items)"));
    }

    @Test
    @DisplayName("maps None return type to void")
    void mapsNoneToVoid() {
        String typeAnnotations = """
                class Writer:
                    def write(self, text: str) -> None: ...
                """;

        String java = generator.generateFromString(typeAnnotations, "Writer.pyi");

        assertThat(java, containsString("void write(String text)"));
    }

    @Test
    @DisplayName("throws PythonException when source has no class")
    void throwsWhenNoClassFound() {
        String typeAnnotations = """
                def module_function(x: int) -> str: ...
                """;

        PythonException ex = assertThrows(PythonException.class,
                () -> generator.generateFromString(typeAnnotations, "no_class.pyi"));

        assertThat(ex.getErrorKind(), is(PythonException.ErrorKind.INTERFACE_GENERATION_ERROR));
        assertThat(ex.getMessage(), containsString("No class definition"));
    }

    @Test
    @DisplayName("generates from .pyi file on disk")
    void generatesFromPyiFile() throws IOException {
        Path tempDir = Files.createTempDirectory("graalpy-gen-test");
        Path pyiFile = tempDir.resolve("Classifier.pyi");
        Files.writeString(pyiFile, """
                class Classifier:
                    def predict(self, features: list[float]) -> str: ...
                """, StandardCharsets.UTF_8);

        Path outputDir = tempDir.resolve("generated");
        Path result = generator.generateFromStub(pyiFile, outputDir);

        assertTrue(Files.exists(result));
        assertThat(result.getFileName().toString(), is("Classifier.java"));
        String content = Files.readString(result);
        assertThat(content, containsString("public interface Classifier"));
        assertThat(content, containsString("String predict(List<Double> features)"));
    }

    @Test
    @DisplayName("throws PythonException when .pyi file does not exist")
    void throwsWhenPyiFileNotFound() throws IOException {
        Path outputDir = Files.createTempDirectory("graalpy-gen-test");
        Path missingPyi = Path.of("/nonexistent/path/Missing.pyi");

        PythonException ex = assertThrows(PythonException.class,
                () -> generator.generateFromStub(missingPyi, outputDir));

        assertThat(ex.getErrorKind(), is(PythonException.ErrorKind.INTERFACE_GENERATION_ERROR));
        assertThat(ex.getMessage(), containsString("not found"));
    }

    @Test
    @DisplayName("generated interface includes package declaration")
    void generatedInterfaceHasPackage() {
        PythonInterfaceGenerator customGen = new PythonInterfaceGenerator("com.acme.python");
        String typeAnnotations = """
                class Predictor:
                    def predict(self, value: float) -> float: ...
                """;

        String java = customGen.generateFromString(typeAnnotations, "Predictor.pyi");

        assertThat(java, startsWith("/*"));
        assertThat(java, containsString("package com.acme.python;"));
    }

    // ── Type mapping tests ──────────────────────────────────────────────────────

    @ParameterizedTest(name = "Python {0} maps to Java {1} in method return")
    @CsvSource({
            "str, String",
            "int, long",
            "float, double",
            "bool, boolean",
            "None, void",
            "Any, Object"
    })
    @DisplayName("maps Python primitive types to Java types")
    void mapsPrimitivePythonTypesToJava(String pythonType, String expectedJavaType) {
        String typeAnnotations = "class Test:\n    def method(self) -> " + pythonType + ": ...\n";
        String java = generator.generateFromString(typeAnnotations, "Test.pyi");
        assertThat(java, containsString(expectedJavaType + " method()"));
    }

    // ── Constructor validation tests ────────────────────────────────────────────

    @Test
    @DisplayName("throws IllegalArgumentException for blank package name")
    void throwsForBlankPackageName() {
        assertThrows(IllegalArgumentException.class,
                () -> new PythonInterfaceGenerator(""));
    }

    @Test
    @DisplayName("throws IllegalArgumentException for null package name")
    void throwsForNullPackageName() {
        assertThrows(IllegalArgumentException.class,
                () -> new PythonInterfaceGenerator(null));
    }
}
