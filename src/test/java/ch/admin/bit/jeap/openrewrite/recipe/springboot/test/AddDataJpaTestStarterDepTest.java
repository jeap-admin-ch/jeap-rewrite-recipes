package ch.admin.bit.jeap.openrewrite.recipe.springboot.test;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class AddDataJpaTestStarterDepTest implements RewriteTest {

    // Minimal pom with a versioned dep so Maven parsing doesn't fail
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
        spec.recipe(new AddDataJpaTestStarterDep())
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void addsDependencyWhenSb3DataJpaTestImportPresent() {
        rewriteRun(
                java("""
                        package com.example;

                        import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

                        @DataJpaTest
                        class MyPersistenceTest {}
                        """, spec -> spec.path("src/test/java/com/example/MyPersistenceTest.java")),
                pomXml(MINIMAL_POM, spec -> spec.after(actual -> {
                    org.assertj.core.api.Assertions.assertThat(actual)
                            .contains("<artifactId>spring-boot-starter-data-jpa-test</artifactId>")
                            .contains("<scope>test</scope>");
                    return actual;
                }))
        );
    }

    @Test
    void addsDependencyWhenSb4DataJpaTestImportPresent() {
        rewriteRun(
                java("""
                        package com.example;

                        import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

                        @DataJpaTest
                        class MyPersistenceTest {}
                        """, spec -> spec.path("src/test/java/com/example/MyPersistenceTest.java")),
                pomXml(MINIMAL_POM, spec -> spec.after(actual -> {
                    org.assertj.core.api.Assertions.assertThat(actual)
                            .contains("<artifactId>spring-boot-starter-data-jpa-test</artifactId>")
                            .contains("<scope>test</scope>");
                    return actual;
                }))
        );
    }

    @Test
    void addsDependencyWhenSb3AutoConfigureTestDatabaseImportPresent() {
        rewriteRun(
                java("""
                        package com.example;

                        import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

                        @AutoConfigureTestDatabase
                        class MyTest {}
                        """, spec -> spec.path("src/test/java/com/example/MyTest.java")),
                pomXml(MINIMAL_POM, spec -> spec.after(actual -> {
                    org.assertj.core.api.Assertions.assertThat(actual)
                            .contains("<artifactId>spring-boot-starter-data-jpa-test</artifactId>")
                            .contains("<scope>test</scope>");
                    return actual;
                }))
        );
    }

    @Test
    void noChangeWhenNeitherAnnotationUsed() {
        rewriteRun(
                java("""
                        package com.example;

                        import org.junit.jupiter.api.Test;

                        class MyTest {
                            @Test
                            void test() {}
                        }
                        """),
                pomXml(MINIMAL_POM)
        );
    }

    @Test
    void noChangeWhenDepAlreadyPresent() {
        // The `isDependencyPresent` check uses text-based matching so works without version resolution.
        // We verify idempotency by having a pom with the dep already present (managed by a BOM via parent).
        // The parent BOM provides the version so Maven parsing doesn't fail.
        String pomWithDepAlready = """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>my-module</artifactId>
                  <version>1.0</version>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>4.0.6</version>
                  </parent>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-data-jpa-test</artifactId>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """;
        // Recipe should detect dep already present and not modify the pom
        rewriteRun(
                java("""
                        package com.example;

                        import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

                        @DataJpaTest
                        class MyPersistenceTest {}
                        """, spec -> spec.path("src/test/java/com/example/MyPersistenceTest.java")),
                pomXml(pomWithDepAlready)
        );
    }

    @Test
    void noChangeWhenOnlyImportPresent() {
        rewriteRun(
                java("""
                        package com.example;
                        import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
                        class MyPersistenceTest {}
                        """, spec -> spec.path("src/test/java/com/example/MyPersistenceTest.java")),
                pomXml(MINIMAL_POM)
        );
    }

    @Test
    void noChangeWhenUsageInProductionClass() {
        rewriteRun(
                java("""
                        package com.example;
                        import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
                        @DataJpaTest
                        class MyPersistenceProdClass {}
                        """, spec -> spec.path("src/main/java/com/example/MyPersistenceProdClass.java")),
                pomXml(MINIMAL_POM)
        );
    }
}
