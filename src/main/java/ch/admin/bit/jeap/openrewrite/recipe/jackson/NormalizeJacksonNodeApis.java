package ch.admin.bit.jeap.openrewrite.recipe.jackson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.Cursor;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

/**
 * Normalizes Jackson imports by migrating com.fasterxml.jackson to tools.jackson.
 * This aligns with Spring Boot 4's preferred Jackson 3 library.
 */
public class NormalizeJacksonNodeApis extends Recipe {

    @Override
    public String getDisplayName() {
        return "Normalize Jackson APIs after Spring Boot 4 migration";
    }

    @Override
    public String getDescription() {
        return "Migrates com.fasterxml.jackson packages to tools.jackson for Spring Boot 4 compatibility.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                new ChangePackage("com.fasterxml.jackson.databind", "tools.jackson.databind", true),
                new ChangePackage("com.fasterxml.jackson.core", "tools.jackson.core", true),
                new ChangePackage("com.fasterxml.jackson.dataformat", "tools.jackson.dataformat", true),
                new ChangePackage("com.fasterxml.jackson.datatype", "tools.jackson.datatype", true),
                new ChangePackage("com.fasterxml.jackson.module", "tools.jackson.module", true),
                new ChangePackage("com.fasterxml.jackson.jaxrs", "tools.jackson.jaxrs", true),
                new ChangePackage("com.fasterxml.jackson.jr", "tools.jackson.jr", true),
                new ChangePackage("tools.jackson.annotation", "com.fasterxml.jackson.annotation", true)
        );
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if ("asString".equals(m.getSimpleName()) && isJsonNode(m.getSelect())) {
                    return m.withName(m.getName().withSimpleName("asText"));
                }
                return m;
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable v = super.visitVariable(variable, ctx);
                if (!isTestSource() || v.getInitializer() == null) {
                    return v;
                }
                Expression rewritten = rewriteRawMapperCreation(v.getInitializer());
                if (rewritten != null) {
                    return v.withInitializer(rewritten);
                }
                return v;
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                J.Assignment a = super.visitAssignment(assignment, ctx);
                if (!isTestSource()) {
                    return a;
                }
                Expression rewritten = rewriteRawMapperCreation(a.getAssignment());
                if (rewritten != null) {
                    return a.withAssignment(rewritten);
                }
                return a;
            }

            @Override
            public J.Return visitReturn(J.Return _return, ExecutionContext ctx) {
                J.Return r = super.visitReturn(_return, ctx);
                if (!isTestSource() || r.getExpression() == null) {
                    return r;
                }
                Expression rewritten = rewriteRawMapperCreation(r.getExpression());
                if (rewritten != null) {
                    return r.withExpression(rewritten);
                }
                return r;
            }

            private Expression rewriteRawMapperCreation(Expression expression) {
                if (!(expression instanceof J.NewClass nc) || !isRawJacksonMapperCreation(nc)) {
                    return null;
                }
                return JavaTemplate.builder("tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build()")
                        .build()
                        .apply(new Cursor(getCursor(), nc), nc.getCoordinates().replace());
            }

            private String selectBuilderCall(J.NewClass nc) {
                return "tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build()";
            }

            private boolean isRawJacksonMapperCreation(J.NewClass nc) {
                String clazz = nc.getClazz().printTrimmed(getCursor());
                return "ObjectMapper".equals(clazz)
                       || "JsonMapper".equals(clazz)
                       || "com.fasterxml.jackson.databind.ObjectMapper".equals(clazz)
                       || "tools.jackson.databind.ObjectMapper".equals(clazz)
                       || "com.fasterxml.jackson.databind.json.JsonMapper".equals(clazz)
                       || "tools.jackson.databind.json.JsonMapper".equals(clazz);
            }

            private boolean isTestSource() {
                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                if (cu == null || cu.getSourcePath() == null) {
                    return false;
                }
                String p = cu.getSourcePath().toString().replace('\\', '/');
                return p.contains("/src/test/java/") || p.startsWith("src/test/java/");
            }


            private boolean isJsonNode(Expression select) {
                if (select == null) {
                    return false;
                }
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(select.getType());
                while (type != null) {
                    if (isJsonNodeType(type)) {
                        return true;
                    }
                    type = type.getSupertype();
                }
                return false;
            }

            private boolean isJsonNodeType(JavaType.FullyQualified type) {
                String fqn = type.getFullyQualifiedName();
                return "com.fasterxml.jackson.databind.JsonNode".equals(fqn) ||
                       "tools.jackson.databind.JsonNode".equals(fqn);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                return super.visitImport(anImport, ctx);
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                return super.visitFieldAccess(fieldAccess, ctx);
            }
        };
    }
}
