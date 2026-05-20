package ch.admin.bit.jeap.openrewrite.recipe.boot;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class AddTestDatasourceAutoConfigExcludesWhenNoDatasourceUrlTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new AddTestDatasourceAutoConfigExcludesWhenNoDatasourceUrl());
    }

    @Test
    void addsExcludesWhenNoDatasourceOrR2dbcUrlExists() {
        rewriteRun(
                yaml(
                        """
                        spring:
                          application:
                            name: demo
                        """,
                        """
                        spring:
                          application:
                            name: demo
                          autoconfigure:
                            exclude:
                              - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
                              - org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
                              - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
                              - org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
                              - org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration
                        """,
                        spec -> spec.path("src/test/resources/application-test.yml")
                )
        );
    }

    @Test
    void noChangeWhenDatasourceUrlExistsInSameFile() {
        rewriteRun(
                yaml(
                        """
                        spring:
                          datasource:
                            url: jdbc:postgresql://localhost:5432/demo
                        """,
                        spec -> spec.path("src/test/resources/application-test.yml")
                )
        );
    }

    @Test
    void noChangeWhenR2dbcUrlExistsInAnotherFile() {
        rewriteRun(
                yaml(
                        """
                        spring:
                          application:
                            name: demo
                        """,
                        spec -> spec.path("src/test/resources/application-test.yml")
                ),
                yaml(
                        """
                        spring:
                          r2dbc:
                            url: r2dbc:postgresql://localhost:5432/demo
                        """,
                        spec -> spec.path("src/main/resources/application.yml")
                )
        );
    }
}
