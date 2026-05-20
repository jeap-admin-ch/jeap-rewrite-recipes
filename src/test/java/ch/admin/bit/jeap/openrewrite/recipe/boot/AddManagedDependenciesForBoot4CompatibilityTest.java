package ch.admin.bit.jeap.openrewrite.recipe.boot;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class AddManagedDependenciesForBoot4CompatibilityTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new AddManagedDependenciesForBoot4Compatibility());
    }

    @Test
    void addsManagedDependenciesAndJpaModelGenWhenDatasourceUrlExists() {
        rewriteRun(
                xml(
                        """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>demo</artifactId>
                          <version>1.0.0</version>
                        </project>
                        """,
                        """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>demo</artifactId>
                          <version>1.0.0</version><dependencyManagement><dependencies><dependency><groupId>org.apache.commons</groupId><artifactId>commons-compress</artifactId><version>1.28.0</version></dependency><!-- TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management and whether version 2.22.0 is appropriate for your project. This dependency was previously managed by Spring Boot or the jeap-parent, which is why it is now in dependency management. --><dependency><groupId>commons-io</groupId><artifactId>commons-io</artifactId><version>2.22.0</version></dependency><!-- TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management and whether version 1.11.0 is appropriate for your project. This dependency was previously managed by Spring Boot or the jeap-parent, which is why it is now in dependency management. --><dependency><groupId>commons-beanutils</groupId><artifactId>commons-beanutils</artifactId><version>1.11.0</version></dependency><!-- TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management and whether version 1.9-inv is appropriate for your project. This dependency was previously managed by Spring Boot or the jeap-parent, which is why it is now in dependency management. --><dependency><groupId>org.lz4</groupId><artifactId>lz4-java</artifactId><version>1.9-inv</version></dependency><!-- TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management and whether version 1.11.0 is appropriate for your project. This dependency was previously managed by Spring Boot or the jeap-parent, which is why it is now in dependency management. --><dependency><groupId>at.yawk.lz4</groupId><artifactId>lz4-java</artifactId><version>1.11.0</version></dependency><!-- TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management and whether version 0.9.6-oracle-00001 is appropriate for your project. This dependency was previously managed by Spring Boot or the jeap-parent, which is why it is now in dependency management. --><dependency><groupId>org.bitbucket.b_c</groupId><artifactId>jose4j</artifactId><version>0.9.6-oracle-00001</version></dependency><dependency><groupId>org.hibernate.orm</groupId><artifactId>hibernate-jpamodelgen</artifactId><version>7.1.5.Final</version></dependency></dependencies></dependencyManagement>
                        </project>
                        """,
                        spec -> spec.path("pom.xml")
                ),
                yaml(
                        """
                        spring:
                          datasource:
                            url: jdbc:postgresql://localhost:5432/demo
                        """,
                        spec -> spec.path("src/main/resources/application.yml")
                )
        );
    }

    @Test
    void doesNotAddJpaModelGenWhenDatasourceUrlMissing() {
        rewriteRun(
                xml(
                        """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>demo</artifactId>
                          <version>1.0.0</version>
                        </project>
                        """,
                        """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>demo</artifactId>
                          <version>1.0.0</version><dependencyManagement><dependencies><dependency><groupId>org.apache.commons</groupId><artifactId>commons-compress</artifactId><version>1.28.0</version></dependency><!-- TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management and whether version 2.22.0 is appropriate for your project. This dependency was previously managed by Spring Boot or the jeap-parent, which is why it is now in dependency management. --><dependency><groupId>commons-io</groupId><artifactId>commons-io</artifactId><version>2.22.0</version></dependency><!-- TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management and whether version 1.11.0 is appropriate for your project. This dependency was previously managed by Spring Boot or the jeap-parent, which is why it is now in dependency management. --><dependency><groupId>commons-beanutils</groupId><artifactId>commons-beanutils</artifactId><version>1.11.0</version></dependency><!-- TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management and whether version 1.9-inv is appropriate for your project. This dependency was previously managed by Spring Boot or the jeap-parent, which is why it is now in dependency management. --><dependency><groupId>org.lz4</groupId><artifactId>lz4-java</artifactId><version>1.9-inv</version></dependency><!-- TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management and whether version 1.11.0 is appropriate for your project. This dependency was previously managed by Spring Boot or the jeap-parent, which is why it is now in dependency management. --><dependency><groupId>at.yawk.lz4</groupId><artifactId>lz4-java</artifactId><version>1.11.0</version></dependency><!-- TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management and whether version 0.9.6-oracle-00001 is appropriate for your project. This dependency was previously managed by Spring Boot or the jeap-parent, which is why it is now in dependency management. --><dependency><groupId>org.bitbucket.b_c</groupId><artifactId>jose4j</artifactId><version>0.9.6-oracle-00001</version></dependency></dependencies></dependencyManagement>
                        </project>
                        """,
                        spec -> spec.path("pom.xml")
                ),
                yaml(
                        """
                        spring:
                          application:
                            name: demo
                        """,
                        spec -> spec.path("src/main/resources/application.yml")
                )
        );
    }

    @Test
    void addsJpaModelGenWhenDatasourceUrlExistsInApplicationLocal() {
        rewriteRun(
                xml(
                        """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>demo</artifactId>
                          <version>1.0.0</version>
                        </project>
                        """,
                        """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>demo</artifactId>
                          <version>1.0.0</version><dependencyManagement><dependencies><dependency><groupId>org.apache.commons</groupId><artifactId>commons-compress</artifactId><version>1.28.0</version></dependency><!-- TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management and whether version 2.22.0 is appropriate for your project. This dependency was previously managed by Spring Boot or the jeap-parent, which is why it is now in dependency management. --><dependency><groupId>commons-io</groupId><artifactId>commons-io</artifactId><version>2.22.0</version></dependency><!-- TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management and whether version 1.11.0 is appropriate for your project. This dependency was previously managed by Spring Boot or the jeap-parent, which is why it is now in dependency management. --><dependency><groupId>commons-beanutils</groupId><artifactId>commons-beanutils</artifactId><version>1.11.0</version></dependency><!-- TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management and whether version 1.9-inv is appropriate for your project. This dependency was previously managed by Spring Boot or the jeap-parent, which is why it is now in dependency management. --><dependency><groupId>org.lz4</groupId><artifactId>lz4-java</artifactId><version>1.9-inv</version></dependency><!-- TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management and whether version 1.11.0 is appropriate for your project. This dependency was previously managed by Spring Boot or the jeap-parent, which is why it is now in dependency management. --><dependency><groupId>at.yawk.lz4</groupId><artifactId>lz4-java</artifactId><version>1.11.0</version></dependency><!-- TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management and whether version 0.9.6-oracle-00001 is appropriate for your project. This dependency was previously managed by Spring Boot or the jeap-parent, which is why it is now in dependency management. --><dependency><groupId>org.bitbucket.b_c</groupId><artifactId>jose4j</artifactId><version>0.9.6-oracle-00001</version></dependency><dependency><groupId>org.hibernate.orm</groupId><artifactId>hibernate-jpamodelgen</artifactId><version>7.1.5.Final</version></dependency></dependencies></dependencyManagement>
                        </project>
                        """,
                        spec -> spec.path("pom.xml")
                ),
                yaml(
                        """
                        spring:
                          datasource:
                            url: jdbc:postgresql://localhost:5432/demo
                        """,
                        spec -> spec.path("src/main/resources/application-local.yml")
                )
        );
    }
}
