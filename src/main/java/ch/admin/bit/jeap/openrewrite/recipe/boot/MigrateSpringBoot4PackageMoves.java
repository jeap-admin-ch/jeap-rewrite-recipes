package ch.admin.bit.jeap.openrewrite.recipe.boot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveImport;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

import java.util.Map;

/**
 * Migrates Spring Boot 4 package-moved types that are no longer on the classpath
 * (so standard type-resolution-based recipes like {@code ChangeType} won't find them).
 * Uses text-based import matching instead.
 *
 * <p>Handles:
 * <ul>
 *   <li>{@code org.springframework.boot.web.server.ErrorPage}
 *       → {@code org.springframework.boot.web.error.ErrorPage}</li>
 *   <li>{@code org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory}
 *       → {@code org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory}</li>
 *   <li>{@code org.springframework.boot.web.servlet.error.DefaultErrorAttributes}
 *       → {@code org.springframework.boot.webmvc.error.DefaultErrorAttributes}</li>
 * </ul>
 */
public class MigrateSpringBoot4PackageMoves extends Recipe {

    private static final Map<String, String> TYPE_MOVES = Map.ofEntries(
            Map.entry("org.springframework.boot.web.server.ErrorPage",
                    "org.springframework.boot.web.error.ErrorPage"),
            Map.entry("org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory",
                    "org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory"),
            Map.entry("org.springframework.boot.web.servlet.error.DefaultErrorAttributes",
                    "org.springframework.boot.webmvc.error.DefaultErrorAttributes"),
            // Spring Boot 4 / spring-boot-health: health contributor types moved from actuator
            Map.entry("org.springframework.boot.actuate.health.Health",
                    "org.springframework.boot.health.contributor.Health"),
            Map.entry("org.springframework.boot.actuate.health.HealthIndicator",
                    "org.springframework.boot.health.contributor.HealthIndicator"),
            Map.entry("org.springframework.boot.actuate.health.AbstractHealthIndicator",
                    "org.springframework.boot.health.contributor.AbstractHealthIndicator"),
            Map.entry("org.springframework.boot.actuate.health.ReactiveHealthIndicator",
                    "org.springframework.boot.health.contributor.ReactiveHealthIndicator"),
            Map.entry("org.springframework.boot.actuate.health.AbstractReactiveHealthIndicator",
                    "org.springframework.boot.health.contributor.AbstractReactiveHealthIndicator"),
            Map.entry("org.springframework.boot.actuate.health.Status",
                    "org.springframework.boot.health.contributor.Status"),
            Map.entry("org.springframework.boot.actuate.health.HealthContributor",
                    "org.springframework.boot.health.contributor.HealthContributor"),
            Map.entry("org.springframework.boot.actuate.health.ReactiveHealthContributor",
                    "org.springframework.boot.health.contributor.ReactiveHealthContributor"),
            Map.entry("org.springframework.boot.actuate.health.CompositeHealthContributor",
                    "org.springframework.boot.health.contributor.CompositeHealthContributor"),
            Map.entry("org.springframework.boot.actuate.health.CompositeReactiveHealthContributor",
                    "org.springframework.boot.health.contributor.CompositeReactiveHealthContributor"),
            Map.entry("org.springframework.boot.actuate.health.NamedContributor",
                    "org.springframework.boot.health.contributor.NamedContributor"),
            // Spring Boot 4: ConditionalOnEnabledHealthIndicator moved from actuator-autoconfigure
            Map.entry("org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator",
                    "org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator"),
            // Spring Boot 4: @AutoConfigureMockMvc moved
            Map.entry("org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc",
                    "org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc"),
            // Spring Boot 4: @EntityScan moved from autoconfigure.domain to persistence.autoconfigure
            Map.entry("org.springframework.boot.autoconfigure.domain.EntityScan",
                    "org.springframework.boot.persistence.autoconfigure.EntityScan"),
            // Spring Boot 4: Flyway types moved from autoconfigure to flyway module
            Map.entry("org.springframework.boot.autoconfigure.flyway.FlywayProperties",
                    "org.springframework.boot.flyway.autoconfigure.FlywayProperties"),
            Map.entry("org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
                    "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"),
            Map.entry("org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy",
                    "org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy"),
            Map.entry("org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer",
                    "org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer"),
            Map.entry("org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer",
                    "org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer"),
            // Spring Boot 4 modular test starters: package moves for test annotations
            Map.entry("org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest",
                    "org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest"),
            Map.entry("org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase",
                    "org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase"),
            // Spring Boot 4: @AutoConfigureObservability removed and split, mapping to metrics as common default
            Map.entry("org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability",
                    "org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics"),
            Map.entry("org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics",
                    "org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics"),
            Map.entry("org.springframework.boot.test.autoconfigure.actuate.tracing.AutoConfigureTracing",
                    "org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing"),
            // Spring Data: PropertyReferenceException moved from mapping to core
            Map.entry("org.springframework.data.mapping.PropertyReferenceException",
                    "org.springframework.data.core.PropertyReferenceException"),
            // Spring Boot 4: JpaProperties and JpaBaseConfiguration moved from orm.jpa to jpa.autoconfigure
            Map.entry("org.springframework.boot.autoconfigure.orm.jpa.JpaProperties",
                    "org.springframework.boot.jpa.autoconfigure.JpaProperties"),
            Map.entry("org.springframework.boot.hibernate.autoconfigure.JpaProperties",
                    "org.springframework.boot.jpa.autoconfigure.JpaProperties"),
            Map.entry("org.springframework.boot.autoconfigure.orm.jpa.JpaBaseConfiguration",
                    "org.springframework.boot.jpa.autoconfigure.JpaBaseConfiguration"),
            Map.entry("org.springframework.boot.hibernate.autoconfigure.JpaBaseConfiguration",
                    "org.springframework.boot.jpa.autoconfigure.JpaBaseConfiguration"),
            // Spring Boot 4: HibernateJpaAutoConfiguration moved from orm.jpa to hibernate.autoconfigure
            Map.entry("org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
                    "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"),
            // Spring Framework 7: Spring null-safety annotations deprecated in favour of JSpecify
            Map.entry("org.springframework.lang.Nullable",
                    "org.jspecify.annotations.Nullable"),
            Map.entry("org.springframework.lang.NonNull",
                    "org.jspecify.annotations.NonNull")
    );

    @Override
    public String getDisplayName() {
        return "Migrate Spring Boot 4 package-moved types";
    }

    @Override
    public String getDescription() {
        return "Spring Boot 4 moved several types to new packages. This recipe updates imports " +
               "using text-based matching so it works even when the old types are no longer on " +
               "the classpath (type-resolution-based recipes like ChangeType would silently skip them).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // Text-based precondition: at least one of the old imports must be present.
        TreeVisitor<?, ExecutionContext> hasOldImport = new JavaIsoVisitor<>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                if (!anImport.isStatic() &&
                        TYPE_MOVES.containsKey(anImport.getQualid().printTrimmed(getCursor()))) {
                    return SearchResult.found(anImport);
                }
                return anImport;
            }
        };

        return Preconditions.check(hasOldImport, new JavaIsoVisitor<>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                if (anImport.isStatic()) {
                    return anImport;
                }
                String oldFqn = anImport.getQualid().printTrimmed(getCursor());
                String newFqn = TYPE_MOVES.get(oldFqn);
                if (newFqn != null) {
                    // Force-add the new import (onlyIfReferenced=false so it's added even
                    // when the old type isn't resolved on the classpath).
                    maybeAddImport(newFqn, null, false);
                    // Force-remove the old import.
                    doAfterVisit(new RemoveImport<>(oldFqn, true));
                }
                return anImport;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier ident = super.visitIdentifier(identifier, ctx);
                // Also handle class name changes if any
                for (Map.Entry<String, String> move : TYPE_MOVES.entrySet()) {
                    String oldFqn = move.getKey();
                    String newFqn = move.getValue();
                    String oldSimpleName = getSimpleName(oldFqn);
                    String newSimpleName = getSimpleName(newFqn);
                    if (!oldSimpleName.equals(newSimpleName) && ident.getSimpleName().equals(oldSimpleName)) {
                        return ident.withSimpleName(newSimpleName);
                    }
                }
                return ident;
            }

            private String getSimpleName(String fqn) {
                int lastDot = fqn.lastIndexOf('.');
                return lastDot == -1 ? fqn : fqn.substring(lastDot + 1);
            }
        });
    }
}
