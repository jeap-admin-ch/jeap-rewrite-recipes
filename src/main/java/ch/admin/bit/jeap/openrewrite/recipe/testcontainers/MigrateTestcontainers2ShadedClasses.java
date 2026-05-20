package ch.admin.bit.jeap.openrewrite.recipe.testcontainers;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveImport;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

/**
 * Migrates testcontainers shaded library imports removed in testcontainers 2.x.
 *
 * <p>In testcontainers 1.x, several third-party libraries (e.g. Apache Commons Lang3)
 * were shaded under the {@code org.testcontainers.shaded.*} package, allowing test code
 * to use them without declaring an explicit dependency. Testcontainers 2.x removed all
 * shaded packages.
 *
 * <p>This recipe strips the {@code org.testcontainers.shaded.} prefix from such imports,
 * so that code using e.g.
 * {@code org.testcontainers.shaded.org.apache.commons.lang3.reflect.FieldUtils}
 * is updated to use {@code org.apache.commons.lang3.reflect.FieldUtils} directly.
 *
 * <p><strong>Note:</strong> After applying this recipe, the project must declare the
 * previously-shaded libraries as explicit dependencies (e.g. {@code commons-lang3}
 * in test scope). This can be handled by a companion {@code AddDependency} recipe entry
 * in the same recipe list.
 */
public class MigrateTestcontainers2ShadedClasses extends Recipe {

    private static final String SHADED_PREFIX = "org.testcontainers.shaded.";

    @Override
    public String getDisplayName() {
        return "Migrate testcontainers shaded class imports";
    }

    @Override
    public String getDescription() {
        return "Testcontainers 2.x removed all shaded packages (org.testcontainers.shaded.*). " +
               "This recipe strips the shaded prefix from imports so that e.g. " +
               "org.testcontainers.shaded.org.apache.commons.lang3.reflect.FieldUtils " +
               "becomes org.apache.commons.lang3.reflect.FieldUtils. " +
               "The de-shaded libraries must then be added as direct project dependencies.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> hasShadedImport = new JavaIsoVisitor<>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                if (!anImport.isStatic() &&
                        anImport.getQualid().printTrimmed(getCursor()).startsWith(SHADED_PREFIX)) {
                    return SearchResult.found(anImport);
                }
                return anImport;
            }
        };

        return Preconditions.check(hasShadedImport, new JavaIsoVisitor<>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                if (anImport.isStatic()) {
                    return anImport;
                }
                String fqn = anImport.getQualid().printTrimmed(getCursor());
                if (fqn.startsWith(SHADED_PREFIX)) {
                    String newFqn = fqn.substring(SHADED_PREFIX.length());
                    maybeAddImport(newFqn, null, false);
                    doAfterVisit(new RemoveImport<>(fqn, true));
                }
                return anImport;
            }
        });
    }
}
