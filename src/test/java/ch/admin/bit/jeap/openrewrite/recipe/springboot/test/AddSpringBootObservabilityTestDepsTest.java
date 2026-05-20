package ch.admin.bit.jeap.openrewrite.recipe.springboot.test;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class AddSpringBootObservabilityTestDepsTest implements RewriteTest {

    private static final String MINIMAL_POM = """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>my-module</artifactId>
              <version>1.0</version>
              <dependencies>
              </dependencies>
            </project>
            """;

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new AddSpringBootObservabilityTestDeps())
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void addsMetricsDependencyWhenAutoConfigureMetricsImportPresent() {
        rewriteRun(
                java("""
                        package com.example;
                        import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
                        @AutoConfigureMetrics
                        class MyTest {}
                        """, spec -> spec.path("module1/src/test/java/com/example/MyTest.java")),
                pomXml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>module1</artifactId>
                          <version>1.0</version>
                          <dependencies>
                          </dependencies>
                        </project>
                        """, spec -> spec.path("module1/pom.xml").after(actual -> {
                    org.assertj.core.api.Assertions.assertThat(actual)
                            .contains("<artifactId>spring-boot-micrometer-metrics-test</artifactId>")
                            .contains("<scope>test</scope>");
                    return actual;
                }))
        );
    }

    @Test
    void addsTracingDependencyWhenAutoConfigureTracingImportPresent() {
        rewriteRun(
                java("""
                        package com.example;
                        import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
                        @AutoConfigureTracing
                        class MyTest {}
                        """, spec -> spec.path("module2/src/test/java/com/example/MyTest.java")),
                pomXml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>module2</artifactId>
                          <version>1.0</version>
                          <dependencies>
                          </dependencies>
                        </project>
                        """, spec -> spec.path("module2/pom.xml").after(actual -> {
                    org.assertj.core.api.Assertions.assertThat(actual)
                            .contains("<artifactId>spring-boot-micrometer-tracing-test</artifactId>")
                            .contains("<scope>test</scope>");
                    return actual;
                }))
        );
    }

    @Test
    void addsBothWhenAutoConfigureObservabilityImportPresent() {
        rewriteRun(
                java("""
                        package com.example;
                        import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
                        @AutoConfigureObservability
                        class MyTest {}
                        """, spec -> spec.path("module3/src/test/java/com/example/MyTest.java")),
                pomXml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>module3</artifactId>
                          <version>1.0</version>
                          <dependencies>
                          </dependencies>
                        </project>
                        """, spec -> spec.path("module3/pom.xml").after(actual -> {
                    org.assertj.core.api.Assertions.assertThat(actual)
                            .contains("<artifactId>spring-boot-micrometer-metrics-test</artifactId>")
                            .contains("<artifactId>spring-boot-micrometer-tracing-test</artifactId>");
                    return actual;
                }))
        );
    }

    @Test
    void doesNotAddDependencyWhenOnlyImportPresent() {
        rewriteRun(
                java("""
                        package com.example;
                        import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
                        class MyTest {}
                        """, spec -> spec.path("module4/src/test/java/com/example/MyTest.java")),
                pomXml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>module4</artifactId>
                          <version>1.0</version>
                          <dependencies>
                          </dependencies>
                        </project>
                        """, spec -> spec.path("module4/pom.xml"))
        );
    }

    @Test
    void doesNotAddDependencyWhenUsageInProductionClass() {
        rewriteRun(
                java("""
                        package com.example;
                        import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
                        @AutoConfigureMetrics
                        class MyProdClass {}
                        """, spec -> spec.path("module5/src/main/java/com/example/MyProdClass.java")),
                pomXml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>module5</artifactId>
                          <version>1.0</version>
                          <dependencies>
                          </dependencies>
                        </project>
                        """, spec -> spec.path("module5/pom.xml"))
        );
    }
}
