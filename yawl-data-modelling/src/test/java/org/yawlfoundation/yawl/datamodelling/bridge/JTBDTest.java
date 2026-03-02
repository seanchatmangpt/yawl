package org.yawlfoundation.yawl.datamodelling.bridge;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation for L4 Jobs To Be Done (JTBD) test methods.
 *
 * <p>Compose with {@link DataModellingJTBDJob} to declare which JTBD job
 * a test method exercises:
 *
 * <pre>{@code
 * @JTBDTest(DataModellingJTBDJob.SCHEMA_DOCUMENTATION_PUBLISHING)
 * void schemaDocumentationPublishing_dataEngineer_apiDocumentationUpdate() throws IOException {
 *     // ...
 * }
 * }</pre>
 *
 * <p>JTBD tests are L4 — they encode real actors doing real jobs with real
 * business outcome assertions. They block release, not merge.
 * They use {@code assumeTrue(l3.isAvailable())} so they skip gracefully
 * when the native library is absent in CI.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Test
public @interface JTBDTest {
    DataModellingJTBDJob value();
}
