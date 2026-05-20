package ch.admin.bit.jeap.openrewrite.recipe.security;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.RemoveImport;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.List;

/**
 * Migrates {@code AntPathRequestMatcher} usages to {@code PathPatternRequestMatcher},
 * as required by Spring Security 7 where {@code AntPathRequestMatcher} was removed.
 *
 * <p>Handles the following patterns:
 * <ul>
 *   <li>{@code new AntPathRequestMatcher(pattern)} →
 *       {@code PathPatternRequestMatcher.pathPattern(pattern)}</li>
 *   <li>{@code new AntPathRequestMatcher(pattern, httpMethod)} →
 *       {@code PathPatternRequestMatcher.pathPattern(HttpMethod.valueOf(httpMethod), pattern)}</li>
 *   <li>{@code new AntPathRequestMatcher(pattern, httpMethod, caseSensitive)} →
 *       {@code PathPatternRequestMatcher.pathPattern(HttpMethod.valueOf(httpMethod), pattern)}</li>
 *   <li>{@code AntPathRequestMatcher.antMatcher(pattern)} →
 *       {@code PathPatternRequestMatcher.pathPattern(pattern)}</li>
 * </ul>
 */
public class MigrateAntPathRequestMatcher extends Recipe {

    private static final String ANT_FQN =
            "org.springframework.security.web.util.matcher.AntPathRequestMatcher";
    private static final String PATH_PATTERN_FQN =
            "org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher";
    private static final String HTTP_METHOD_FQN =
            "org.springframework.http.HttpMethod";

    @Override
    public String getDisplayName() {
        return "Migrate AntPathRequestMatcher to PathPatternRequestMatcher";
    }

    @Override
    public String getDescription() {
        return "Spring Security 7 removed AntPathRequestMatcher in favor of " +
               "PathPatternRequestMatcher. This recipe migrates constructor and " +
               "static factory method usages to the new API.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // Use a text-based import check so this recipe works even when AntPathRequestMatcher
        // is no longer on the classpath (Spring Security 7 removed it — exactly when we need
        // this recipe to run). UsesType relies on resolved type info and would return false here.
        TreeVisitor<?, ExecutionContext> hasAntImport = new JavaIsoVisitor<>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                if (!anImport.isStatic() &&
                        ANT_FQN.equals(anImport.getQualid().printTrimmed(getCursor()))) {
                    return SearchResult.found(anImport);
                }
                return anImport;
            }
        };
        return Preconditions.check(hasAntImport, new JavaVisitor<>() {

            // new AntPathRequestMatcher(pattern)
            final JavaTemplate oneArgTemplate = JavaTemplate
                    .builder("PathPatternRequestMatcher.pathPattern(#{any(String)})")
                    .imports(PATH_PATTERN_FQN)
                    .build();

            // new AntPathRequestMatcher(pattern, httpMethod)  →  args are (method, pattern) in new API
            final JavaTemplate twoArgTemplate = JavaTemplate
                    .builder("PathPatternRequestMatcher.pathPattern(HttpMethod.valueOf(#{any(String)}), #{any(String)})")
                    .imports(PATH_PATTERN_FQN, HTTP_METHOD_FQN)
                    .build();

            @Override
            public J visitMethodInvocation(J.MethodInvocation method,
                                           ExecutionContext ctx) {
                method = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

                // AntPathRequestMatcher.antMatcher(pattern) → PathPatternRequestMatcher.pathPattern(pattern)
                if (isAntMatcherStaticCall(method)) {
                    List<Expression> args = method.getArguments();
                    J result = oneArgTemplate.apply(
                            getCursor(), method.getCoordinates().replace(), args.get(0));
                    maybeAddImport(PATH_PATTERN_FQN, null, false);
                    doAfterVisit(new RemoveImport<>(ANT_FQN, true));
                    return result;
                }
                return method;
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                newClass = (J.NewClass) super.visitNewClass(newClass, ctx);

                if (!isAntPathRequestMatcherConstructor(newClass)) {
                    return newClass;
                }

                List<Expression> args = newClass.getArguments();
                int argCount = args.size();

                if (argCount == 1) {
                    // new AntPathRequestMatcher(pattern) → PathPatternRequestMatcher.pathPattern(pattern)
                    J result = oneArgTemplate.apply(
                            getCursor(), newClass.getCoordinates().replace(), args.get(0));
                    maybeAddImport(PATH_PATTERN_FQN, null, false);
                    doAfterVisit(new RemoveImport<>(ANT_FQN, true));
                    return result;
                }

                if (argCount >= 2) {
                    // new AntPathRequestMatcher(pattern, httpMethod[, caseSensitive])
                    // → PathPatternRequestMatcher.pathPattern(HttpMethod.valueOf(httpMethod), pattern)
                    // Note: argument order is intentionally swapped — new API takes (method, pattern)
                    Expression pattern = args.get(0);
                    Expression httpMethod = args.get(1);

                    J result = twoArgTemplate.apply(
                            getCursor(), newClass.getCoordinates().replace(),
                            httpMethod, pattern);
                    maybeAddImport(PATH_PATTERN_FQN, null, false);
                    maybeAddImport(HTTP_METHOD_FQN, null, false);
                    doAfterVisit(new RemoveImport<>(ANT_FQN, true));
                    return result;
                }

                return newClass;
            }

            private boolean isAntPathRequestMatcherConstructor(J.NewClass newClass) {
                // Primary: use resolved type info
                if (TypeUtils.isOfClassType(newClass.getType(), ANT_FQN)) {
                    return true;
                }
                // Fallback: when Spring Security 7 has removed the class and the type is unresolved,
                // match by class identifier simple name
                if (newClass.getClazz() instanceof J.Identifier identifier) {
                    return "AntPathRequestMatcher".equals(identifier.getSimpleName());
                }
                return false;
            }

            private boolean isAntMatcherStaticCall(J.MethodInvocation method) {
                if (!"antMatcher".equals(method.getSimpleName())) {
                    return false;
                }
                // Use resolved type info when available
                if (method.getMethodType() != null &&
                        method.getMethodType().getDeclaringType() != null) {
                    return ANT_FQN.equals(method.getMethodType().getDeclaringType().getFullyQualifiedName());
                }
                // Fall back to simple-name check when types are not fully resolved
                if (method.getSelect() instanceof J.Identifier identifier) {
                    return "AntPathRequestMatcher".equals(identifier.getSimpleName());
                }
                return false;
            }
        });
    }
}
