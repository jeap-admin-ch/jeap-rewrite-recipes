package ch.admin.bit.jeap.openrewrite.recipe.boot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveImport;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

import java.util.List;

/**
 * Migrates {@code Jackson2ObjectMapperBuilderCustomizer} to {@code JsonMapperBuilderCustomizer}
 * as required by Spring Boot 4, where the old interface was removed.
 *
 * <p>Spring Boot 4 removed {@code org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer}
 * (and the underlying {@code Jackson2ObjectMapperBuilder}) in favour of
 * {@code org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer}
 * whose lambda parameter is {@code com.fasterxml.jackson.databind.json.JsonMapper.Builder}.
 *
 * <p>The following changes are applied:
 * <ul>
 *   <li>Import: {@code org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer}
 *       → {@code org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer}</li>
 *   <li>All usages of the simple name {@code Jackson2ObjectMapperBuilderCustomizer}
 *       → {@code JsonMapperBuilderCustomizer} (return types, variable types, etc.)</li>
 *   <li>{@code builder.postConfigurer(x -> x.registerModule(expr))}
 *       → {@code builder.addModule(expr)} — the {@code postConfigurer}+{@code registerModule}
 *       idiom is replaced by the direct {@code addModule} method on {@code JsonMapper.Builder}.</li>
 * </ul>
 */
public class MigrateJackson2ObjectMapperBuilderCustomizer extends Recipe {

    private static final String OLD_FQN =
            "org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer";
    private static final String NEW_FQN =
            "org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer";
    private static final String OLD_SIMPLE_NAME = "Jackson2ObjectMapperBuilderCustomizer";
    private static final String NEW_SIMPLE_NAME = "JsonMapperBuilderCustomizer";

    @Override
    public String getDisplayName() {
        return "Migrate Jackson2ObjectMapperBuilderCustomizer to JsonMapperBuilderCustomizer";
    }

    @Override
    public String getDescription() {
        return "Spring Boot 4 removed Jackson2ObjectMapperBuilderCustomizer in favour of " +
               "JsonMapperBuilderCustomizer (org.springframework.boot.jackson.autoconfigure). " +
               "Also migrates builder.postConfigurer(x -> x.registerModule(y)) to builder.addModule(y).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> hasOldImport = new JavaIsoVisitor<>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                if (!anImport.isStatic() &&
                        OLD_FQN.equals(anImport.getQualid().printTrimmed(getCursor()))) {
                    return SearchResult.found(anImport);
                }
                return anImport;
            }
        };

        return Preconditions.check(hasOldImport, new JavaIsoVisitor<>() {

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                if (!anImport.isStatic() &&
                        OLD_FQN.equals(anImport.getQualid().printTrimmed(getCursor()))) {
                    maybeAddImport(NEW_FQN, null, false);
                    doAfterVisit(new RemoveImport<>(OLD_FQN, true));
                }
                return anImport;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier ident, ExecutionContext ctx) {
                ident = super.visitIdentifier(ident, ctx);
                if (OLD_SIMPLE_NAME.equals(ident.getSimpleName())) {
                    return ident.withSimpleName(NEW_SIMPLE_NAME);
                }
                return ident;
            }

            /**
             * Transforms {@code builder.postConfigurer(x -> x.registerModule(expr))}
             * into {@code builder.addModule(expr)}.
             */
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                method = super.visitMethodInvocation(method, ctx);

                if (!"postConfigurer".equals(method.getSimpleName())) {
                    return method;
                }
                List<Expression> args = method.getArguments();
                if (args.size() != 1 || !(args.get(0) instanceof J.Lambda)) {
                    return method;
                }
                J.Lambda lambda = (J.Lambda) args.get(0);
                if (!(lambda.getBody() instanceof J.MethodInvocation)) {
                    return method;
                }
                J.MethodInvocation lambdaBody = (J.MethodInvocation) lambda.getBody();
                if (!"registerModule".equals(lambdaBody.getSimpleName())) {
                    return method;
                }
                List<Expression> moduleArgs = lambdaBody.getArguments();
                if (moduleArgs.size() != 1) {
                    return method;
                }
                // Replace postConfigurer(x -> x.registerModule(MODULE)) with addModule(MODULE)
                return method
                        .withName(method.getName().withSimpleName("addModule"))
                        .withArguments(moduleArgs);
            }
        });
    }
}
