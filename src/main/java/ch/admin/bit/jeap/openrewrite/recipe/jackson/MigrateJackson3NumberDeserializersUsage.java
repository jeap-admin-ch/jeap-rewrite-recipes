package ch.admin.bit.jeap.openrewrite.recipe.jackson;

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
 * Jackson 3 moved NumberDeserializers from deser.std to deser.jdk.
 */
public class MigrateJackson3NumberDeserializersUsage extends Recipe {

    private static final Map<String, String> TYPE_MOVES = Map.of(
            "com.fasterxml.jackson.databind.deser.std.NumberDeserializers",
            "tools.jackson.databind.deser.jdk.NumberDeserializers",
            "tools.jackson.databind.deser.std.NumberDeserializers",
            "tools.jackson.databind.deser.jdk.NumberDeserializers"
    );

    @Override
    public String getDisplayName() {
        return "Migrate Jackson NumberDeserializers import package";
    }

    @Override
    public String getDescription() {
        return "Replaces NumberDeserializers imports from deser.std to deser.jdk for Jackson 3 " +
               "using text-based import matching.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> hasOldImport = new JavaIsoVisitor<>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                if (!anImport.isStatic() && TYPE_MOVES.containsKey(anImport.getQualid().printTrimmed(getCursor()))) {
                    return SearchResult.found(anImport);
                }
                return anImport;
            }
        };

        return Preconditions.check(hasOldImport, new JavaIsoVisitor<>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                if (extendsAbstractObjectMapperHelper(cu)) {
                    return cu;
                }
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                if (anImport.isStatic()) {
                    return anImport;
                }
                String oldFqn = anImport.getQualid().printTrimmed(getCursor());
                String newFqn = TYPE_MOVES.get(oldFqn);
                if (newFqn != null) {
                    maybeAddImport(newFqn, null, false);
                    doAfterVisit(new RemoveImport<>(oldFqn, true));
                }
                return anImport;
            }

            private boolean extendsAbstractObjectMapperHelper(J.CompilationUnit cu) {
                return cu.getClasses().stream().anyMatch(cd ->
                        cd.getExtends() != null && "AbstractObjectMapperHelper".equals(cd.getExtends().printTrimmed(getCursor())));
            }
        });
    }
}
