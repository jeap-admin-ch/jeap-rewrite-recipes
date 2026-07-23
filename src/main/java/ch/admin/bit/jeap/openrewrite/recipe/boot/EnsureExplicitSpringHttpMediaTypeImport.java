package ch.admin.bit.jeap.openrewrite.recipe.boot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adds an explicit Spring {@code MediaType} import when a wildcard import would be ambiguous.
 */
public class EnsureExplicitSpringHttpMediaTypeImport extends Recipe {

    private static final String SPRING_HTTP_WILDCARD = "org.springframework.http.*";
    private static final String SPRING_MEDIA_TYPE = "org.springframework.http.MediaType";

    @Override
    public String getDisplayName() {
        return "Explicitly import Spring HTTP MediaType";
    }

    @Override
    public String getDescription() {
        return "Adds an explicit org.springframework.http.MediaType import when MediaType is referenced " +
               "through an org.springframework.http wildcard import, preventing ambiguous type resolution.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit,
                                                           ExecutionContext ctx) {
                J.CompilationUnit cu = super.visitCompilationUnit(compilationUnit, ctx);
                if (!usesMediaType(cu) || hasExplicitMediaTypeImport(cu)) {
                    return cu;
                }

                List<J.Import> imports = new ArrayList<>();
                boolean changed = false;
                for (J.Import anImport : cu.getImports()) {
                    if (!changed && !anImport.isStatic() &&
                            SPRING_HTTP_WILDCARD.equals(anImport.getQualid().printTrimmed(getCursor()))) {
                        J.Import explicitImport = anImport
                                .withId(Tree.randomId())
                                .withQualid(anImport.getQualid()
                                        .withName(anImport.getQualid().getName().withSimpleName("MediaType"))
                                        .withType(JavaType.ShallowClass.build(SPRING_MEDIA_TYPE)));
                        imports.add(explicitImport);
                        imports.add(anImport.withPrefix(Space.format("\n")));
                        changed = true;
                    } else {
                        imports.add(anImport);
                    }
                }
                return changed ? cu.withImports(imports) : cu;
            }

            private boolean hasExplicitMediaTypeImport(J.CompilationUnit cu) {
                return cu.getImports().stream()
                        .filter(anImport -> !anImport.isStatic())
                        .map(anImport -> anImport.getQualid().printTrimmed(getCursor()))
                        .anyMatch(importedType -> importedType.endsWith(".MediaType"));
            }

            private boolean usesMediaType(J.CompilationUnit cu) {
                AtomicBoolean found = new AtomicBoolean();
                new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess,
                                                          AtomicBoolean mediaTypeFound) {
                        J.FieldAccess access = super.visitFieldAccess(fieldAccess, mediaTypeFound);
                        if (access.getTarget() instanceof J.Identifier identifier &&
                                "MediaType".equals(identifier.getSimpleName())) {
                            mediaTypeFound.set(true);
                        }
                        return access;
                    }
                }.visit(cu, found);
                return found.get();
            }
        };
    }
}
