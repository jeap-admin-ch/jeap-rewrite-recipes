package ch.admin.bit.jeap.openrewrite.recipe.testcontainers;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class MigrateTestcontainersArtifactsTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new MigrateTestcontainersArtifacts());
    }

    @Test
    void renamesTestcontainersArtifacts() {
        rewriteRun(
                xml(
                        """
                        <project>
                          <dependencies>
                            <dependency>
                              <groupId>org.testcontainers</groupId>
                              <artifactId>postgresql</artifactId>
                              <version>1.19.0</version>
                            </dependency>
                            <dependency>
                              <groupId>org.testcontainers</groupId>
                              <artifactId>kafka</artifactId>
                            </dependency>
                            <dependency>
                              <groupId>org.testcontainers</groupId>
                              <artifactId>minio</artifactId>
                            </dependency>
                            <dependency>
                              <groupId>org.testcontainers</groupId>
                              <artifactId>testcontainers</artifactId>
                            </dependency>
                          </dependencies>
                          <dependencyManagement>
                            <dependencies>
                              <dependency>
                                <groupId>org.testcontainers</groupId>
                                <artifactId>mysql</artifactId>
                                <version>1.19.0</version>
                              </dependency>
                            </dependencies>
                          </dependencyManagement>
                        </project>
                        """,
                        """
                        <project>
                          <dependencies>
                            <dependency>
                              <groupId>org.testcontainers</groupId>
                              <artifactId>testcontainers-postgresql</artifactId>
                              <version>1.19.0</version>
                            </dependency>
                            <dependency>
                              <groupId>org.testcontainers</groupId>
                              <artifactId>testcontainers-kafka</artifactId>
                            </dependency>
                            <dependency>
                              <groupId>org.testcontainers</groupId>
                              <artifactId>testcontainers-minio</artifactId>
                            </dependency>
                            <dependency>
                              <groupId>org.testcontainers</groupId>
                              <artifactId>testcontainers</artifactId>
                            </dependency>
                          </dependencies>
                          <dependencyManagement>
                            <dependencies>
                              <dependency>
                                <groupId>org.testcontainers</groupId>
                                <artifactId>testcontainers-mysql</artifactId>
                                <version>1.19.0</version>
                              </dependency>
                            </dependencies>
                          </dependencyManagement>
                        </project>
                        """
                )
        );
    }

    @Test
    void renamesMinioWithoutVersion() {
        rewriteRun(
                xml(
                        """
                        <project>
                          <dependencies>
                            <dependency>
                              <groupId>org.testcontainers</groupId>
                              <artifactId>minio</artifactId>
                            </dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <dependencies>
                            <dependency>
                              <groupId>org.testcontainers</groupId>
                              <artifactId>testcontainers-minio</artifactId>
                            </dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }
}
