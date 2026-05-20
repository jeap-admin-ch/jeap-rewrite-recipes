package ch.admin.bit.jeap.openrewrite.recipe.resilience;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveImport;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Migrates {@code spring-retry} annotations to Spring Framework 7's built-in resilience support,
 * as required when upgrading to Spring Boot 4 where {@code spring-retry} is no longer managed.
 *
 * <p>Handles the following migrations:
 * <ul>
 *   <li>{@code org.springframework.retry.annotation.Retryable}
 *       → {@code org.springframework.resilience.annotation.Retryable} (same class name)</li>
 *   <li>{@code org.springframework.retry.annotation.EnableRetry}
 *       → {@code org.springframework.resilience.annotation.EnableResilientMethods}
 *       (annotation usage also renamed)</li>
 *   <li>{@code org.springframework.retry.support.RetryTemplate}
 *       → {@code org.springframework.core.retry.RetryTemplate} (import only —
 *       note: the {@code builder()} API was removed; manual fixes may be needed)</li>
 *   <li>{@code @Retryable(maxAttempts = N)}
 *       → {@code @Retryable(maxRetries = N-1)} (semantics changed: old
 *       {@code maxAttempts} counted the initial attempt; new {@code maxRetries} counts
 *       only retries after the first attempt)</li>
 * </ul>
 *
 * <p><strong>Partially handled / not handled:</strong> simple {@code @Backoff} attributes
 * ({@code delay}, {@code value}, {@code maxDelay}, {@code multiplier}) are flattened to
 * {@code @Retryable} attributes. {@code @Recover}, {@code RetryCallback}, and
 * {@code RetryContext} remain as-is and may require manual migration.
 */
public class MigrateSpringRetryToSpringFramework7 extends Recipe {

    private static final String OLD_RETRYABLE_FQN =
            "org.springframework.retry.annotation.Retryable";
    private static final String NEW_RETRYABLE_FQN =
            "org.springframework.resilience.annotation.Retryable";

    private static final String OLD_ENABLE_RETRY_FQN =
            "org.springframework.retry.annotation.EnableRetry";
    private static final String NEW_ENABLE_RESILIENT_METHODS_FQN =
            "org.springframework.resilience.annotation.EnableResilientMethods";

    private static final String OLD_RETRY_TEMPLATE_FQN =
            "org.springframework.retry.support.RetryTemplate";
    private static final String NEW_RETRY_TEMPLATE_FQN =
            "org.springframework.core.retry.RetryTemplate";

    private static final String OLD_RETRY_PACKAGE_PREFIX = "org.springframework.retry.";

    @Override
    public String getDisplayName() {
        return "Migrate spring-retry to Spring Framework 7 resilience";
    }

    @Override
    public String getDescription() {
        return "Spring Boot 4 no longer manages spring-retry. Spring Framework 7 provides built-in " +
               "retry support via org.springframework.resilience.annotation.Retryable. " +
               "This recipe migrates @Retryable, @EnableRetry, RetryTemplate and the maxAttempts " +
               "attribute to their Spring Framework 7 equivalents using text-based import matching " +
               "(required because the old types are removed from the classpath in Spring Boot 4).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // Text-based precondition: at least one spring.retry import must be present.
        TreeVisitor<?, ExecutionContext> hasSpringRetryImport = new JavaIsoVisitor<>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                if (!anImport.isStatic() &&
                        anImport.getQualid().printTrimmed(getCursor()).startsWith(OLD_RETRY_PACKAGE_PREFIX)) {
                    return SearchResult.found(anImport);
                }
                return anImport;
            }
        };

        return Preconditions.check(hasSpringRetryImport, new JavaIsoVisitor<>() {

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                if (anImport.isStatic()) {
                    return anImport;
                }
                String fqn = anImport.getQualid().printTrimmed(getCursor());
                if (OLD_RETRYABLE_FQN.equals(fqn)) {
                    maybeAddImport(NEW_RETRYABLE_FQN, null, false);
                    doAfterVisit(new RemoveImport<>(OLD_RETRYABLE_FQN, true));
                } else if (OLD_ENABLE_RETRY_FQN.equals(fqn)) {
                    maybeAddImport(NEW_ENABLE_RESILIENT_METHODS_FQN, null, false);
                    doAfterVisit(new RemoveImport<>(OLD_ENABLE_RETRY_FQN, true));
                } else if (OLD_RETRY_TEMPLATE_FQN.equals(fqn)) {
                    maybeAddImport(NEW_RETRY_TEMPLATE_FQN, null, false);
                    doAfterVisit(new RemoveImport<>(OLD_RETRY_TEMPLATE_FQN, true));
                }
                return anImport;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                annotation = super.visitAnnotation(annotation, ctx);

                if (!(annotation.getAnnotationType() instanceof J.Identifier id)) {
                    return annotation;
                }

                // @EnableRetry → @EnableResilientMethods (also changes the identifier name)
                if ("EnableRetry".equals(id.getSimpleName())) {
                    J.Identifier newId = id.withSimpleName("EnableResilientMethods")
                            .withType(JavaType.ShallowClass.build(NEW_ENABLE_RESILIENT_METHODS_FQN));
                    return annotation.withAnnotationType(newId);
                }

                // @Retryable(maxAttempts = N) → @Retryable(maxRetries = N-1)
                if ("Retryable".equals(id.getSimpleName()) && annotation.getArguments() != null) {
                    List<Expression> args = annotation.getArguments();
                    List<Expression> newArgs = new ArrayList<>();
                    boolean changed = false;

                    for (Expression arg : args) {
                        if (arg instanceof J.Assignment assignment &&
                            assignment.getVariable() instanceof J.Identifier argId) {

                            if ("maxAttempts".equals(argId.getSimpleName())) {
                                Expression value = assignment.getAssignment();
                                Expression newValue = adjustMaxAttempts(value);
                                newArgs.add(renameAssignment(assignment, "maxRetries", newValue));
                                changed = true;
                                continue;
                            }

                            if ("retryFor".equals(argId.getSimpleName())) {
                                // spring-retry: retryFor -> spring-resilience: value/includes
                                newArgs.add(renameAssignment(assignment, "value", assignment.getAssignment()));
                                changed = true;
                                continue;
                            }

                            if ("backoff".equals(argId.getSimpleName()) &&
                                assignment.getAssignment() instanceof J.Annotation backoffAnnotation) {
                                List<Expression> flattened = flattenBackoffArgs(backoffAnnotation, assignment.getPrefix());
                                if (!flattened.isEmpty()) {
                                    newArgs.addAll(flattened);
                                    changed = true;
                                    continue;
                                }
                            }
                        }

                        newArgs.add(arg);
                    }

                    if (changed) {
                        return annotation.withArguments(newArgs);
                    }
                }

                return annotation;
            }

            private J.Assignment renameAssignment(J.Assignment assignment, String newName, Expression newValue) {
                J.Identifier oldId = (J.Identifier) assignment.getVariable();
                J.Identifier newArgId = new J.Identifier(
                        UUID.randomUUID(),
                        oldId.getPrefix(),
                        Markers.EMPTY,
                        List.of(),
                        newName,
                        oldId.getType(),
                        null
                );
                return assignment.withVariable(newArgId).withAssignment(newValue);
            }

            private List<Expression> flattenBackoffArgs(J.Annotation backoff, Space prefix) {
                List<Expression> flattened = new ArrayList<>();
                if (backoff.getArguments() == null) {
                    return flattened;
                }
                for (Expression bArg : backoff.getArguments()) {
                    if (!(bArg instanceof J.Assignment bAssign) ||
                        !(bAssign.getVariable() instanceof J.Identifier bId)) {
                        continue;
                    }

                    String sourceName = bId.getSimpleName();
                    String targetName = switch (sourceName) {
                        case "value", "delay" -> "delay";
                        case "maxDelay" -> "maxDelay";
                        case "multiplier" -> "multiplier";
                        default -> null;
                    };
                    if (targetName == null) {
                        continue;
                    }

                    J.Identifier targetId = new J.Identifier(
                            UUID.randomUUID(),
                            prefix,
                            Markers.EMPTY,
                            List.of(),
                            targetName,
                            bId.getType(),
                            null
                    );
                    flattened.add(bAssign.withVariable(targetId).withAssignment(bAssign.getAssignment()));
                }
                return flattened;
            }

            /**
             * Converts a {@code maxAttempts = N} value to {@code maxRetries = N-1}.
             * Old semantics: maxAttempts counted the initial attempt.
             * New semantics: maxRetries counts only retries after the first attempt.
             * Only adjusts literal integer values; leaves expressions unchanged.
             */
            private Expression adjustMaxAttempts(Expression value) {
                if (value instanceof J.Literal literal && literal.getValue() instanceof Integer n) {
                    int newValue = Math.max(0, n - 1);
                    return literal.withValue(newValue).withValueSource(String.valueOf(newValue));
                }
                // Non-literal (variable or SpEL): rename only, don't touch value
                return value;
            }
        });
    }
}
