package ch.admin.bit.jeap.openrewrite.recipe.maven;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class RemoveSpringCloudDependenciesBomImportTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new RemoveSpringCloudDependenciesBomImport());
    }

    @Test
    void removesBomImportFromDependencyManagement() {
        rewriteRun(
                xml(
                        """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>demo</artifactId>
                          <version>1.0.0</version>
                          <dependencyManagement>
                            <dependencies>
                              <dependency>
                                <groupId>org.springframework.cloud</groupId>
                                <artifactId>spring-cloud-dependencies</artifactId>
                                <version>2025.1.1</version>
                                <type>pom</type>
                                <scope>import</scope>
                              </dependency>
                              <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-dependencies</artifactId>
                                <version>4.0.6</version>
                                <type>pom</type>
                                <scope>import</scope>
                              </dependency>
                            </dependencies>
                          </dependencyManagement>
                        </project>
                        """,
                        """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>demo</artifactId>
                          <version>1.0.0</version>
                          <dependencyManagement>
                            <dependencies>
                              <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-dependencies</artifactId>
                                <version>4.0.6</version>
                                <type>pom</type>
                                <scope>import</scope>
                              </dependency>
                            </dependencies>
                          </dependencyManagement>
                        </project>
                        """
                )
        );
    }
}
