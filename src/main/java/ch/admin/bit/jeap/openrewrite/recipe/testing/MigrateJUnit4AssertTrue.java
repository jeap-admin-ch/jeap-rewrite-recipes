package ch.admin.bit.jeap.openrewrite.recipe.testing;

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
 * Migrates the remaining JUnit 4 {@code assertTrue} static import to JUnit Jupiter.
 */
public class MigrateJUnit4AssertTrue extends Recipe {

    private static final String OLD_IMPORT = "org.junit.Assert.assertTrue";
    private static final String NEW_OWNER = "org.junit.jupiter.api.Assertions";

    @Override
    public String getDisplayName() {
        return "Migrate JUnit 4 assertTrue to JUnit Jupiter";
    }

    @Override
    public String getDescription() {
        return "Migrates an explicit org.junit.Assert.assertTrue static import to JUnit Jupiter, " +
               "including the changed message argument order.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> hasOldImport = new JavaIsoVisitor<>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                if (anImport.isStatic() &&
                        OLD_IMPORT.equals(anImport.getQualid().printTrimmed(getCursor()))) {
                    return SearchResult.found(anImport);
                }
                return anImport;
            }
        };

        return Preconditions.check(hasOldImport, new JavaIsoVisitor<>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit,
                                                           ExecutionContext ctx) {
                J.CompilationUnit cu = super.visitCompilationUnit(compilationUnit, ctx);
                maybeAddImport(NEW_OWNER, "assertTrue", false);
                doAfterVisit(new RemoveImport<>(OLD_IMPORT, true));
                return cu;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                             ExecutionContext ctx) {
                J.MethodInvocation invocation = super.visitMethodInvocation(method, ctx);
                if (invocation.getSelect() != null ||
                        !"assertTrue".equals(invocation.getSimpleName()) ||
                        invocation.getArguments().size() != 2) {
                    return invocation;
                }

                Expression message = invocation.getArguments().get(0);
                Expression condition = invocation.getArguments().get(1);
                return invocation.withArguments(List.of(
                        condition.withPrefix(message.getPrefix()),
                        message.withPrefix(condition.getPrefix())));
            }
        });
    }
}
