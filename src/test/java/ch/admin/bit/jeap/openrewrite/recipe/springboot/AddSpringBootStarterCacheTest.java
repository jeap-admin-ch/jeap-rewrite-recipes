package ch.admin.bit.jeap.openrewrite.recipe.springboot;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class AddSpringBootStarterCacheTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddSpringBootStarterCache());
    }

    @Test
    void addsDependenciesIfEnableCachingPresent() {
        rewriteRun(
            spec -> spec.expectedCyclesThatMakeChanges(1),
            pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1.0.0</version>
                  <dependencies><dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter-cache</artifactId>
                      </dependency><dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-cache-test</artifactId>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
                </project>
                """,
                spec -> spec.path("pom.xml")
            ),
            java(
                """
                import org.springframework.cache.annotation.EnableCaching;
                import org.springframework.context.annotation.Configuration;

                @Configuration
                @EnableCaching
                class MyConfig {}
                """,
                spec -> spec.path("src/main/java/com/example/MyConfig.java")
            )
        );
    }

    @Test
    void addsAutoConfigureCacheToDataJpaTests() {
        rewriteRun(
            spec -> spec.typeValidationOptions(TypeValidation.none())
                .parser(org.openrewrite.java.JavaParser.fromJavaVersion()
                    .dependsOn(
                        """
                        package org.springframework.cache.annotation;
                        public @interface EnableCaching {}
                        """,
                        """
                        package org.springframework.boot.test.autoconfigure.orm.jpa;
                        public @interface DataJpaTest {}
                        """,
                        """
                        package org.springframework.boot.cache.test.autoconfigure;
                        public @interface AutoConfigureCache {}
                        """
                    )),
            java(
                """
                import org.springframework.cache.annotation.EnableCaching;
                import org.springframework.context.annotation.Configuration;

                @Configuration
                @EnableCaching
                class MyConfig {}
                """,
                spec -> spec.path("src/main/java/com/example/MyConfig.java")
            ),
            java(
                """
                package com.example;

                import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

                @DataJpaTest
                class MyTest {}
                """,
                """
                package com.example;

                import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
                import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

                @DataJpaTest
                @AutoConfigureCache
                class MyTest {}
                """,
                spec -> spec.path("src/test/java/com/example/MyTest.java")
            )
        );
    }

    @Test
    void addsAutoConfigureCacheWhenEnableCachingTypeIsUnresolved() {
        rewriteRun(
            spec -> spec.typeValidationOptions(TypeValidation.none())
                .parser(org.openrewrite.java.JavaParser.fromJavaVersion()
                    .dependsOn(
                        """
                        package org.springframework.boot.cache.test.autoconfigure;
                        public @interface AutoConfigureCache {}
                        """
                    )),
            java(
                """
                package com.example;

                import org.springframework.cache.annotation.EnableCaching;
                import org.springframework.context.annotation.Configuration;

                @Configuration
                @EnableCaching
                class MyConfig {}
                """,
                spec -> spec.path("src/main/java/com/example/MyConfig.java")
            ),
            java(
                """
                package com.example;

                import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

                @DataJpaTest
                class MyTest {}
                """,
                """
                package com.example;

                import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
                import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

                @DataJpaTest
                @AutoConfigureCache
                class MyTest {}
                """,
                spec -> spec.path("src/test/java/com/example/MyTest.java")
            )
        );
    }

    @Test
    void doesNotAddDependencyIfEnableCachingMissing() {
        rewriteRun(
            pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                  </dependencies>
                </project>
                """,
                spec -> spec.path("pom.xml")
            ),
            java(
                """
                import org.springframework.context.annotation.Configuration;

                @Configuration
                class MyConfig {}
                """,
                spec -> spec.path("src/main/java/com/example/MyConfig.java")
            )
        );
    }

    @Test
    void doesNotAddDependenciesIfAlreadyPresent() {
        rewriteRun(
            pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-cache</artifactId>
                      <version>3.4.1</version>
                    </dependency>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-test</artifactId>
                      <version>3.4.1</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """,
                spec -> spec.path("pom.xml")
            )
        );
    }
}
