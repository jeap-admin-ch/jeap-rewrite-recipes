package ch.admin.bit.jeap.openrewrite.recipe.boot;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateSpringBoot4PackageMovesTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new MigrateSpringBoot4PackageMoves())
                // No Spring Boot classes on classpath — simulates the migration scenario
                // where old types are gone after upgrading to Spring Boot 4.
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesErrorPage() {
        rewriteRun(java(
                """
                import org.springframework.boot.web.server.ErrorPage;
                import org.springframework.http.HttpStatus;

                class WebConfig {
                    void configure() {
                        var page = new ErrorPage(HttpStatus.NOT_FOUND, "/notFound");
                    }
                }
                """,
                """
                import org.springframework.boot.web.error.ErrorPage;
                import org.springframework.http.HttpStatus;

                class WebConfig {
                    void configure() {
                        var page = new ErrorPage(HttpStatus.NOT_FOUND, "/notFound");
                    }
                }
                """
        ));
    }

    @Test
    void migratesConfigurableServletWebServerFactory() {
        rewriteRun(java(
                """
                import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;

                class WebConfig {
                    void configure(ConfigurableServletWebServerFactory factory) {
                    }
                }
                """,
                """
                import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;

                class WebConfig {
                    void configure(ConfigurableServletWebServerFactory factory) {
                    }
                }
                """
        ));
    }

    @Test
    void migratesDefaultErrorAttributes() {
        rewriteRun(java(
                """
                import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;

                class ErrorHandler extends DefaultErrorAttributes {
                }
                """,
                """
                import org.springframework.boot.webmvc.error.DefaultErrorAttributes;

                class ErrorHandler extends DefaultErrorAttributes {
                }
                """
        ));
    }

    @Test
    void migratesMultipleTypesInOneFile() {
        rewriteRun(java(
                """
                import org.springframework.boot.web.server.ErrorPage;
                import org.springframework.boot.web.server.WebServerFactoryCustomizer;
                import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
                import org.springframework.http.HttpStatus;

                class FrontendWebConfig {
                    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> containerCustomizer() {
                        return container -> container.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND, "/notFound"));
                    }
                }
                """,
                """
                import org.springframework.boot.web.error.ErrorPage;
                import org.springframework.boot.web.server.WebServerFactoryCustomizer;
                import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
                import org.springframework.http.HttpStatus;

                class FrontendWebConfig {
                    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> containerCustomizer() {
                        return container -> container.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND, "/notFound"));
                    }
                }
                """
        ));
    }

    @Test
    void noChangeWhenOldTypesNotUsed() {
        rewriteRun(java(
                """
                class Unchanged {
                    void doSomething() {
                    }
                }
                """
        ));
    }

    @Test
    void migratesHealthIndicator() {
        rewriteRun(java(
                """
                import org.springframework.boot.actuate.health.Health;
                import org.springframework.boot.actuate.health.HealthIndicator;
                import org.springframework.boot.actuate.health.Status;
                import org.springframework.stereotype.Component;

                @Component
                class MyHealthIndicator implements HealthIndicator {
                    @Override
                    public Health health() {
                        return Health.status(Status.UP).build();
                    }
                }
                """,
                """
                import org.springframework.boot.health.contributor.Health;
                import org.springframework.boot.health.contributor.HealthIndicator;
                import org.springframework.boot.health.contributor.Status;
                import org.springframework.stereotype.Component;

                @Component
                class MyHealthIndicator implements HealthIndicator {
                    @Override
                    public Health health() {
                        return Health.status(Status.UP).build();
                    }
                }
                """
        ));
    }

    @Test
    void migratesWildcardHealthContributorImport() {
        rewriteRun(java(
                """
                import org.springframework.boot.actuate.health.*;

                class MyHealthIndicator implements HealthIndicator {
                    @Override
                    public Health health() {
                        return Health.up().build();
                    }
                }
                """,
                """
                import org.springframework.boot.health.contributor.*;

                class MyHealthIndicator implements HealthIndicator {
                    @Override
                    public Health health() {
                        return Health.up().build();
                    }
                }
                """
        ));
    }

    @Test
    void migratesCompositeHealthContributor() {
        rewriteRun(java(
                """
                import org.springframework.boot.actuate.health.CompositeHealthContributor;
                import org.springframework.boot.actuate.health.HealthContributor;
                import org.springframework.boot.actuate.health.NamedContributor;
                import java.util.Iterator;

                class MyComposite implements CompositeHealthContributor {
                    @Override
                    public HealthContributor getContributor(String name) {
                        return null;
                    }
                    @Override
                    public Iterator<NamedContributor<HealthContributor>> iterator() {
                        return null;
                    }
                }
                """,
                """
                import org.springframework.boot.health.contributor.CompositeHealthContributor;
                import org.springframework.boot.health.contributor.HealthContributor;
                import org.springframework.boot.health.contributor.NamedContributor;

                import java.util.Iterator;

                class MyComposite implements CompositeHealthContributor {
                    @Override
                    public HealthContributor getContributor(String name) {
                        return null;
                    }
                    @Override
                    public Iterator<NamedContributor<HealthContributor>> iterator() {
                        return null;
                    }
                }
                """
        ));
    }

    @Test
    void migratesConditionalOnEnabledHealthIndicator() {
        rewriteRun(java(
                """
                import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
                import org.springframework.boot.actuate.health.Health;
                import org.springframework.boot.actuate.health.HealthIndicator;
                import org.springframework.stereotype.Component;

                @Component
                @ConditionalOnEnabledHealthIndicator("myService")
                class MyHealthIndicator implements HealthIndicator {
                    @Override
                    public Health health() {
                        return Health.up().build();
                    }
                }
                """,
                """
                import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
                import org.springframework.boot.health.contributor.Health;
                import org.springframework.boot.health.contributor.HealthIndicator;
                import org.springframework.stereotype.Component;

                @Component
                @ConditionalOnEnabledHealthIndicator("myService")
                class MyHealthIndicator implements HealthIndicator {
                    @Override
                    public Health health() {
                        return Health.up().build();
                    }
                }
                """
        ));
    }

    @Test
    void migratesFlywayTypes() {
        rewriteRun(java(
                """
                import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
                import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

                class FlywayCfg {
                    FlywayProperties properties;
                    FlywayMigrationStrategy strategy;
                }
                """,
                """
                import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
                import org.springframework.boot.flyway.autoconfigure.FlywayProperties;

                class FlywayCfg {
                    FlywayProperties properties;
                    FlywayMigrationStrategy strategy;
                }
                """
        ));
    }

    @Test
    void migratesDataJpaTestAndAutoConfigureTestDatabase() {
        rewriteRun(java(
                """
                import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
                import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

                @DataJpaTest
                @AutoConfigureTestDatabase
                class RepositoryTest {
                }
                """,
                """
                import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
                import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

                @DataJpaTest
                @AutoConfigureTestDatabase
                class RepositoryTest {
                }
                """
        ));
    }
    @Test
    void migratesJpaAndHibernateTypes() {
        rewriteRun(java(
                """
                import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
                import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
                import org.springframework.boot.hibernate.autoconfigure.JpaProperties;

                class JpaCfg {
                    JpaProperties jpaProperties;
                    HibernateJpaAutoConfiguration hibernateJpaAutoConfiguration;
                    JpaProperties otherJpaProperties;
                }
                """,
                """
                import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
                import org.springframework.boot.jpa.autoconfigure.JpaProperties;

                class JpaCfg {
                    JpaProperties jpaProperties;
                    HibernateJpaAutoConfiguration hibernateJpaAutoConfiguration;
                    JpaProperties otherJpaProperties;
                }
                """
        ));
    }

    @Test
    void migratesPropertyReferenceException() {
        rewriteRun(java(
                """
                import org.springframework.data.mapping.PropertyReferenceException;

                class RepoTest {
                    PropertyReferenceException ex;
                }
                """,
                """
                import org.springframework.data.core.PropertyReferenceException;

                class RepoTest {
                    PropertyReferenceException ex;
                }
                """
        ));
    }

    @Test
    void migratesAutoConfigureObservability() {
        rewriteRun(java(
                """
                import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;

                @AutoConfigureObservability
                class MyTest {
                }
                """,
                """
                import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;

                @AutoConfigureMetrics
                class MyTest {
                }
                """
        ));
    }

    @Test
    void migratesAutoConfigureMetricsAndTracing() {
        rewriteRun(java(
                """
                import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics;
                import org.springframework.boot.test.autoconfigure.actuate.tracing.AutoConfigureTracing;

                @AutoConfigureMetrics
                @AutoConfigureTracing
                class MyTest {
                }
                """,
                """
                import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
                import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;

                @AutoConfigureMetrics
                @AutoConfigureTracing
                class MyTest {
                }
                """
        ));
    }

    @Test
    void migratesPrometheusTypes() {
        rewriteRun(java(
                """
                import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusOutputFormat;
                import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;

                class PrometheusCfg {
                    PrometheusOutputFormat format;
                    PrometheusScrapeEndpoint endpoint;
                }
                """,
                """
                import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusOutputFormat;
                import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusScrapeEndpoint;

                class PrometheusCfg {
                    PrometheusOutputFormat format;
                    PrometheusScrapeEndpoint endpoint;
                }
                """
        ));
    }

    @Test
    void migratesUserDetailsServiceAutoConfiguration() {
        rewriteRun(java(
                """
                import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

                class SecurityCfg {
                    UserDetailsServiceAutoConfiguration autoConfiguration;
                }
                """,
                """
                import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

                class SecurityCfg {
                    UserDetailsServiceAutoConfiguration autoConfiguration;
                }
                """
        ));
    }
}
