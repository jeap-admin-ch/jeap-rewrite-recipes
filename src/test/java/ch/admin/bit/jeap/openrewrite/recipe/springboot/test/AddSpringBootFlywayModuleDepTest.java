package ch.admin.bit.jeap.openrewrite.recipe.springboot.test;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class AddSpringBootFlywayModuleDepTest implements RewriteTest {

    private static final String MINIMAL_POM = """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>my-module</artifactId>
              <version>1.0</version>
              <dependencies>
                <dependency>
                  <groupId>junit</groupId>
                  <artifactId>junit</artifactId>
                  <version>4.13.2</version>
                  <scope>test</scope>
                </dependency>
              </dependencies>
            </project>
            """;

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new AddSpringBootFlywayModuleDep())
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void addsDependencyWhenSb3FlywayPropertiesImportPresent() {
        rewriteRun(
                java("""
                        package com.example;

                        import org.springframework.boot.autoconfigure.flyway.FlywayProperties;

                        class FlywayTestConfig {
                            FlywayProperties props;
                        }
                        """),
                pomXml(MINIMAL_POM, spec -> spec.after(actual -> {
                    org.assertj.core.api.Assertions.assertThat(actual)
                            .contains("<artifactId>spring-boot-flyway</artifactId>");
                    return actual;
                }))
        );
    }

    @Test
    void addsDependencyWhenSb4FlywayPropertiesImportPresent() {
        rewriteRun(
                java("""
                        package com.example;

                        import org.springframework.boot.flyway.autoconfigure.FlywayProperties;

                        class FlywayConfig {
                            FlywayProperties props;
                        }
                        """),
                pomXml(MINIMAL_POM, spec -> spec.after(actual -> {
                    org.assertj.core.api.Assertions.assertThat(actual)
                            .contains("<artifactId>spring-boot-flyway</artifactId>");
                    return actual;
                }))
        );
    }

    @Test
    void doesNotAddDependencyWhenOnlyImportPresent() {
        rewriteRun(
                java("""
                        package com.example;
                        import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
                        class MyConfig {}
                        """),
                pomXml(MINIMAL_POM)
        );
    }
}
