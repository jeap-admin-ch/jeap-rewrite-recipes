package ch.admin.bit.jeap.openrewrite.recipe.hibernate;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.RemoveImport;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

import java.util.List;

/**
 * Migrates Hibernate annotations removed in Hibernate 7 (shipped with Spring Boot 4).
 *
 * <p>The existing {@code MigrateToHibernate71} OpenRewrite recipe does not cover these removals.
 *
 * <p>Handles the following migrations:
 * <ul>
 *   <li>{@code @Where(clause = "sql")} → {@code @SQLRestriction("sql")}</li>
 *   <li>{@code @WhereJoinTable(clause = "sql")} → {@code @SQLJoinTableRestriction("sql")}</li>
 *   <li>{@code @Loader(namedQuery = "name")} → removed entirely. In Hibernate 7,
 *       {@code @SQLRestriction} applies automatically during load-by-id, making the
 *       {@code @Loader} + {@code @NamedQuery} soft-delete pattern unnecessary.
 *       The associated {@code @NamedQuery} is left in place for manual cleanup.</li>
 * </ul>
 *
 * <p>Uses text-based import matching since the removed types are no longer on the
 * Hibernate 7 classpath (type-resolution-based recipes would silently skip them).
 */
public class MigrateHibernate7RemovedAnnotations extends Recipe {

    private static final String WHERE_FQN = "org.hibernate.annotations.Where";
    private static final String SQL_RESTRICTION_FQN = "org.hibernate.annotations.SQLRestriction";

    private static final String WHERE_JOIN_TABLE_FQN = "org.hibernate.annotations.WhereJoinTable";
    private static final String SQL_JOIN_TABLE_RESTRICTION_FQN =
            "org.hibernate.annotations.SQLJoinTableRestriction";

    private static final String LOADER_FQN = "org.hibernate.annotations.Loader";

    @Override
    public String getDisplayName() {
        return "Migrate Hibernate 7 removed annotations";
    }

    @Override
    public String getDescription() {
        return "Hibernate 7 removed @Where, @WhereJoinTable, and @Loader from org.hibernate.annotations. " +
               "This recipe migrates @Where → @SQLRestriction, @WhereJoinTable → @SQLJoinTableRestriction, " +
               "and removes @Loader (whose filtering role is now handled automatically by @SQLRestriction). " +
               "Uses text-based import matching so it works even when the old types are no longer on the classpath.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> hasOldImport = new JavaIsoVisitor<>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                if (!anImport.isStatic()) {
                    String fqn = anImport.getQualid().printTrimmed(getCursor());
                    if (WHERE_FQN.equals(fqn) || WHERE_JOIN_TABLE_FQN.equals(fqn) || LOADER_FQN.equals(fqn)) {
                        return SearchResult.found(anImport);
                    }
                }
                return anImport;
            }
        };

        return Preconditions.check(hasOldImport, new JavaIsoVisitor<>() {

            final JavaTemplate sqlRestrictionTemplate = JavaTemplate
                    .builder("@SQLRestriction(#{any(String)})")
                    .imports(SQL_RESTRICTION_FQN)
                    .build();

            final JavaTemplate sqlJoinTableRestrictionTemplate = JavaTemplate
                    .builder("@SQLJoinTableRestriction(#{any(String)})")
                    .imports(SQL_JOIN_TABLE_RESTRICTION_FQN)
                    .build();

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                annotation = super.visitAnnotation(annotation, ctx);

                if (!(annotation.getAnnotationType() instanceof J.Identifier id)) {
                    return annotation;
                }

                String simpleName = id.getSimpleName();
                if ("Where".equals(simpleName) || "WhereJoinTable".equals(simpleName)) {
                    return migrateWhereAnnotation(annotation, simpleName);
                }
                return annotation;
            }

            private J.Annotation migrateWhereAnnotation(J.Annotation annotation, String simpleName) {
                Expression clauseExpr = extractClauseExpression(annotation);
                if (clauseExpr == null) {
                    return annotation;
                }

                boolean isWhere = "Where".equals(simpleName);
                String oldFqn = isWhere ? WHERE_FQN : WHERE_JOIN_TABLE_FQN;
                String newFqn = isWhere ? SQL_RESTRICTION_FQN : SQL_JOIN_TABLE_RESTRICTION_FQN;
                JavaTemplate template = isWhere ? sqlRestrictionTemplate : sqlJoinTableRestrictionTemplate;

                J.Annotation newAnnotation = template.apply(
                        getCursor(), annotation.getCoordinates().replace(), clauseExpr);
                maybeAddImport(newFqn, null, false);
                doAfterVisit(new RemoveImport<>(oldFqn, true));
                return newAnnotation;
            }

            private Expression extractClauseExpression(J.Annotation annotation) {
                if (annotation.getArguments() == null) {
                    return null;
                }
                for (Expression arg : annotation.getArguments()) {
                    if (arg instanceof J.Assignment assignment &&
                            assignment.getVariable() instanceof J.Identifier argId &&
                            "clause".equals(argId.getSimpleName())) {
                        return assignment.getAssignment();
                    }
                }
                return null;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                classDecl = super.visitClassDeclaration(classDecl, ctx);
                return removeLoaderAnnotations(classDecl);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                                                                     ExecutionContext ctx) {
                multiVariable = super.visitVariableDeclarations(multiVariable, ctx);
                return removeLoaderAnnotations(multiVariable);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                method = super.visitMethodDeclaration(method, ctx);
                return removeLoaderAnnotations(method);
            }

            private <T extends J> T removeLoaderAnnotations(T tree) {
                List<J.Annotation> anns = getLeadingAnnotations(tree);
                if (anns == null) {
                    return tree;
                }
                List<J.Annotation> filtered = anns.stream()
                        .filter(ann -> !isLoaderAnnotation(ann))
                        .toList();
                if (filtered.size() < anns.size()) {
                    doAfterVisit(new RemoveImport<>(LOADER_FQN, true));
                    return withLeadingAnnotations(tree, filtered);
                }
                return tree;
            }

            private List<J.Annotation> getLeadingAnnotations(J tree) {
                if (tree instanceof J.ClassDeclaration cd) return cd.getLeadingAnnotations();
                if (tree instanceof J.VariableDeclarations vd) return vd.getLeadingAnnotations();
                if (tree instanceof J.MethodDeclaration md) return md.getLeadingAnnotations();
                return null;
            }

            @SuppressWarnings("unchecked")
            private <T extends J> T withLeadingAnnotations(T tree, List<J.Annotation> annotations) {
                if (tree instanceof J.ClassDeclaration cd) return (T) cd.withLeadingAnnotations(annotations);
                if (tree instanceof J.VariableDeclarations vd) return (T) vd.withLeadingAnnotations(annotations);
                if (tree instanceof J.MethodDeclaration md) return (T) md.withLeadingAnnotations(annotations);
                return tree;
            }

            private boolean isLoaderAnnotation(J.Annotation ann) {
                if (ann.getAnnotationType() instanceof J.Identifier id) {
                    return "Loader".equals(id.getSimpleName());
                }
                return false;
            }
        });
    }
}
