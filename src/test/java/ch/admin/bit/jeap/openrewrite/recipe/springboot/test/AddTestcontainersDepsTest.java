package ch.admin.bit.jeap.openrewrite.recipe.springboot.test;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class AddTestcontainersDepsTest implements RewriteTest {

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
        spec.recipe(new AddTestcontainersDeps())
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void addsJunitJupiterAndMinioDepsFromImports() {
        rewriteRun(
                java("""
                        package com.example;

                        import org.testcontainers.junit.jupiter.Container;
                        import org.testcontainers.junit.jupiter.Testcontainers;
                        import org.testcontainers.minio.MinIOContainer;

                        @Testcontainers
                        class TcTest {
                            @Container
                            static MinIOContainer minio = new MinIOContainer("minio/minio:latest");
                        }
                        """, spec -> spec.path("src/test/java/com/example/TcTest.java")),
                pomXml(MINIMAL_POM, spec -> spec.after(actual -> {
                    org.assertj.core.api.Assertions.assertThat(actual)
                            .contains("<artifactId>testcontainers-junit-jupiter</artifactId>")
                            .contains("<artifactId>testcontainers-minio</artifactId>")
                            .contains("<scope>test</scope>");
                    // Check that the added dependencies don't have a version tag
                    // We can't use doesNotContain("<version>") because the base POM has one.
                    // So we check that the specific artifacts are not followed by a version tag.
                    org.assertj.core.api.Assertions.assertThat(actual)
                            .doesNotContain("<artifactId>testcontainers-junit-jupiter</artifactId>\n        <version>")
                            .doesNotContain("<artifactId>testcontainers-minio</artifactId>\n        <version>");
                    return actual;
                }))
        );
    }

    @Test
    void addsDepsFromContainerClassImports() {
        rewriteRun(
                java("""
                        package com.example;

                        import org.testcontainers.containers.PostgreSQLContainer;
                        import org.testcontainers.containers.KafkaContainer;
                        import org.testcontainers.containers.MinIOContainer;
                        import org.testcontainers.containers.GenericContainer;

                        class TcTest {
                            static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest");
                            static KafkaContainer kafka = new KafkaContainer();
                            static MinIOContainer minio = new MinIOContainer("minio/minio:latest");
                            static GenericContainer<?> generic = new GenericContainer<>("alpine");
                        }
                        """, spec -> spec.path("src/test/java/com/example/TcTest.java")),
                pomXml(MINIMAL_POM, spec -> spec.after(actual -> {
                    org.assertj.core.api.Assertions.assertThat(actual)
                            .contains("<artifactId>testcontainers-postgresql</artifactId>")
                            .contains("<artifactId>testcontainers-kafka</artifactId>")
                            .contains("<artifactId>testcontainers-minio</artifactId>");
                    org.assertj.core.api.Assertions.assertThat(actual)
                            .doesNotContain("<artifactId>testcontainers-postgresql</artifactId>\n        <version>")
                            .doesNotContain("<artifactId>testcontainers-kafka</artifactId>\n        <version>")
                            .doesNotContain("<artifactId>testcontainers-minio</artifactId>\n        <version>");
                    // GenericContainer has no artifact in the map, so it shouldn't add anything extra (it's in the base)
                    return actual;
                }))
        );
    }
    @Test
    void doesNotAddVersionWhenManaged() {
        rewriteRun(
                java("""
                        package com.example;
                        import org.testcontainers.junit.jupiter.Testcontainers;
                        @Testcontainers
                        class TcTest {}
                        """, spec -> spec.path("src/test/java/com/example/TcTest.java")),
                pomXml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>my-module</artifactId>
                          <version>1.0</version>
                          <dependencyManagement>
                            <dependencies>
                              <dependency>
                                <groupId>org.testcontainers</groupId>
                                <artifactId>testcontainers-junit-jupiter</artifactId>
                                <version>1.19.0</version>
                                <scope>test</scope>
                              </dependency>
                            </dependencies>
                          </dependencyManagement>
                          <dependencies>
                          </dependencies>
                        </project>
                        """, spec -> spec.after(actual -> {
                            org.assertj.core.api.Assertions.assertThat(actual)
                                    .contains("<artifactId>testcontainers-junit-jupiter</artifactId>")
                                    .doesNotContain("<version>${testcontainers.version}</version>")
                                    .contains("<scope>test</scope>");
                            return actual;
                        }))
        );
    }

    @Test
    void doesNotAddDependencyWhenOnlyImportPresent() {
        rewriteRun(
                java("""
                        package com.example;
                        import org.testcontainers.containers.PostgreSQLContainer;
                        class TcTest {}
                        """, spec -> spec.path("src/test/java/com/example/TcTest.java")),
                pomXml(MINIMAL_POM)
        );
    }

    @Test
    void doesNotAddDependencyWhenUsageInProductionClass() {
        rewriteRun(
                java("""
                        package com.example;
                        import org.testcontainers.containers.PostgreSQLContainer;
                        class TcProdClass {
                            static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest");
                        }
                        """, spec -> spec.path("src/main/java/com/example/TcProdClass.java")),
                pomXml(MINIMAL_POM)
        );
    }
}
