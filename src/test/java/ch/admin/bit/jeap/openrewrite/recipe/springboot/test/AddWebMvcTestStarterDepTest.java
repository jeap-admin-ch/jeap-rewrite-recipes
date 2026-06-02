package ch.admin.bit.jeap.openrewrite.recipe.springboot.test;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class AddWebMvcTestStarterDepTest implements RewriteTest {

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
        spec.recipe(new AddWebMvcTestStarterDep())
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void addsDependencyWhenSb3AnnotationUsed() {
        rewriteRun(
                java("""
                        package com.example;
                        import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
                        @AutoConfigureMockMvc
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
                            .contains("<artifactId>spring-boot-starter-webmvc-test</artifactId>")
                            .contains("<scope>test</scope>");
                    return actual;
                }))
        );
    }

    @Test
    void addsDependencyWhenSb4AnnotationUsed() {
        rewriteRun(
                java("""
                        package com.example;
                        import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
                        @AutoConfigureMockMvc
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
                            .contains("<artifactId>spring-boot-starter-webmvc-test</artifactId>")
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
                        import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
                        """, spec -> spec.path("module3/pom.xml"))
        );
    }

    @Test
    void doesNotAddDependencyWhenUsageInProductionClass() {
        rewriteRun(
                java("""
                        package com.example;
                        import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
                        @AutoConfigureMockMvc
                        class MyProdClass {}
                        """, spec -> spec.path("module4/src/main/java/com/example/MyProdClass.java")),
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
    void doesNotAddDependencyWhenStarterAlreadyPresent() {
        rewriteRun(
                java("""
                        package com.example;
                        import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
                        @AutoConfigureMockMvc
                        class MyTest {}
                        """, spec -> spec.path("module5/src/test/java/com/example/MyTest.java")),
                pomXml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>module5</artifactId>
                          <version>1.0</version>
                          <parent>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-parent</artifactId>
                            <version>4.0.6</version>
                          </parent>
                          <dependencies>
                            <dependency>
                              <groupId>org.springframework.boot</groupId>
                              <artifactId>spring-boot-starter-webmvc-test</artifactId>
                              <scope>test</scope>
                            </dependency>
                          </dependencies>
                        </project>
                        """, spec -> spec.path("module5/pom.xml"))
        );
    }

    @Test
    void doesNotAddDependencyWhenModuleAlreadyPresent() {
        rewriteRun(
                java("""
                        package com.example;
                        import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
                        @AutoConfigureMockMvc
                        class MyTest {}
                        """, spec -> spec.path("module6/src/test/java/com/example/MyTest.java")),
                pomXml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>module6</artifactId>
                          <version>1.0</version>
                          <parent>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-parent</artifactId>
                            <version>4.0.6</version>
                          </parent>
                          <dependencies>
                            <dependency>
                              <groupId>org.springframework.boot</groupId>
                              <artifactId>spring-boot-webmvc-test</artifactId>
                              <scope>test</scope>
                            </dependency>
                          </dependencies>
                        </project>
                        """, spec -> spec.path("module6/pom.xml"))
        );
    }
}
