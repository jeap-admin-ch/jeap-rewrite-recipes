package ch.admin.bit.jeap.openrewrite.recipe.jackson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.RemoveImport;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

/**
 * Fixes a Jackson migration edge case where a project-defined class named {@code JsonMapper}
 * collides with the imported Jackson type {@code JsonMapper}.
 *
 * <p>When both are present in the same compilation unit, Java reports:
 * {@code JsonMapper is already defined in this compilation unit}.
 *
 * <p>This recipe targets only such files and:
 * <ul>
 *   <li>replaces {@code JsonMapper.builder()} with
 *       {@code com.fasterxml.jackson.databind.json.JsonMapper.builder().findAndAddModules()}</li>
 *   <li>removes {@code import com.fasterxml.jackson.databind.json.JsonMapper;}</li>
 *   <li>removes {@code import tools.jackson.databind.json.JsonMapper;}</li>
 * </ul>
 */
public class FixJackson3JsonMapperNameCollision extends Recipe {

    private static final String JACKSON_JSON_MAPPER_FQN = "com.fasterxml.jackson.databind.json.JsonMapper";
    private static final String JACKSON_3_JSON_MAPPER_FQN = "tools.jackson.databind.json.JsonMapper";

    @Override
    public String getDisplayName() {
        return "Fix Jackson JsonMapper import/class name collision";
    }

    @Override
    public String getDescription() {
        return "Replaces JsonMapper.builder() with fully qualified " +
               "tools.jackson.databind.json.JsonMapper.builder().findAndAddModules() and removes the conflicting " +
               "import when a file also declares a class named JsonMapper.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> hasJacksonJsonMapperImport = new JavaIsoVisitor<>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                if (!anImport.isStatic()) {
                    String fqn = anImport.getQualid().printTrimmed(getCursor());
                    if (JACKSON_JSON_MAPPER_FQN.equals(fqn) || JACKSON_3_JSON_MAPPER_FQN.equals(fqn)) {
                        return SearchResult.found(anImport);
                    }
                }
                return anImport;
            }
        };

        return Preconditions.check(hasJacksonJsonMapperImport, new JavaIsoVisitor<>() {
            final JavaTemplate fqBuilderCall = JavaTemplate
                    .builder("tools.jackson.databind.json.JsonMapper.builder().findAndAddModules()")
                    .build();

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                if (!declaresTopLevelJsonMapperClass(cu)) {
                    return cu;
                }
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                method = super.visitMethodInvocation(method, ctx);

                if (!isJsonMapperBuilderCall(method)) {
                    return method;
                }

                J.MethodInvocation replaced = fqBuilderCall.apply(
                        getCursor(), method.getCoordinates().replace());
                doAfterVisit(new RemoveImport<>(JACKSON_JSON_MAPPER_FQN, true));
                doAfterVisit(new RemoveImport<>(JACKSON_3_JSON_MAPPER_FQN, true));
                return replaced;
            }

            private boolean declaresTopLevelJsonMapperClass(J.CompilationUnit cu) {
                return cu.getClasses().stream().anyMatch(c -> "JsonMapper".equals(c.getSimpleName()));
            }

            private boolean isJsonMapperBuilderCall(J.MethodInvocation method) {
                if (!"builder".equals(method.getSimpleName())) {
                    return false;
                }
                if (!(method.getSelect() instanceof J.Identifier identifier)) {
                    return false;
                }
                return "JsonMapper".equals(identifier.getSimpleName());
            }
        });
    }
}
