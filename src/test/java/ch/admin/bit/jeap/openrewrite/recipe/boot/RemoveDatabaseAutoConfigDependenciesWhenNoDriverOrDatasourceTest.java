package ch.admin.bit.jeap.openrewrite.recipe.boot;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class RemoveDatabaseAutoConfigDependenciesWhenNoDriverOrDatasourceTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new RemoveDatabaseAutoConfigDependenciesWhenNoDriverOrDatasource());
    }

    @Test
    void removesFlywayAndReplacesSpringDataJpaWhenNoDriverAndNoDatasourceUrl() {
        rewriteRun(
                xml(
                                """
                                <project xmlns="http://maven.apache.org/POM/4.0.0">
                                  <modelVersion>4.0.0</modelVersion>
                                  <groupId>com.example</groupId>
                                  <artifactId>demo</artifactId>
                                  <version>1.0.0</version>
                                  <dependencies>
                                    <dependency>
                                      <groupId>org.springframework.data</groupId>
                                      <artifactId>spring-data-jpa</artifactId>
                                      <version>3.5.5</version>
                                    </dependency>
                                    <dependency>
                                      <groupId>org.flywaydb</groupId>
                                      <artifactId>flyway-core</artifactId>
                                      <version>10.22.0</version>
                                    </dependency>
                                    <dependency>
                                      <groupId>org.liquibase</groupId>
                                      <artifactId>liquibase-core</artifactId>
                                      <version>4.31.1</version>
                                    </dependency>
                                  </dependencies>
                                </project>
                                """,
                                """
                                <project xmlns="http://maven.apache.org/POM/4.0.0">
                                  <modelVersion>4.0.0</modelVersion>
                                  <groupId>com.example</groupId>
                                  <artifactId>demo</artifactId>
                                  <version>1.0.0</version>
                                  <dependencies><dependency>
                                      <groupId>org.springframework.data</groupId>
                                      <artifactId>spring-data-commons</artifactId>
                                      <version>3.5.5</version>
                                    </dependency>
                                  </dependencies>
                                </project>
                                """
                )
        );
    }

    @Test
    void noChangeWhenDatasourceUrlExists() {
        rewriteRun(
                xml(
                                """
                                <project xmlns="http://maven.apache.org/POM/4.0.0">
                                  <modelVersion>4.0.0</modelVersion>
                                  <groupId>com.example</groupId>
                                  <artifactId>demo</artifactId>
                                  <version>1.0.0</version>
                                  <dependencies>
                                    <dependency>
                                      <groupId>org.springframework.data</groupId>
                                      <artifactId>spring-data-jpa</artifactId>
                                      <version>3.5.5</version>
                                    </dependency>
                                  </dependencies>
                                </project>
                                """
                        ),
                yaml(
                                """
                                spring:
                                  datasource:
                                    url: jdbc:postgresql://localhost:5432/demo
                                """
                )
        );
    }

    @Test
    void noChangeWhenDriverDependencyExists() {
        rewriteRun(
                xml(
                                """
                                <project xmlns="http://maven.apache.org/POM/4.0.0">
                                  <modelVersion>4.0.0</modelVersion>
                                  <groupId>com.example</groupId>
                                  <artifactId>demo</artifactId>
                                  <version>1.0.0</version>
                                  <dependencies>
                                    <dependency>
                                      <groupId>org.springframework.data</groupId>
                                      <artifactId>spring-data-jpa</artifactId>
                                      <version>3.5.5</version>
                                    </dependency>
                                    <dependency>
                                      <groupId>org.postgresql</groupId>
                                      <artifactId>postgresql</artifactId>
                                      <version>42.7.4</version>
                                    </dependency>
                                  </dependencies>
                                </project>
                                """
                )
        );
    }
}
