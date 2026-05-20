package ch.admin.bit.jeap.openrewrite.recipe.boot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

public class NormalizeObjectMapperConfiguration extends Recipe {

    @Override
    public String getDisplayName() {
        return "Normalize ObjectMapper configuration for Spring Boot 4";
    }

    @Override
    public String getDescription() {
        return "Replaces custom ObjectMapper beans that just use Jackson2ObjectMapperBuilder.build() with a JsonMapperBuilderCustomizer bean.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                // Check for @Bean annotation
                if (m.getLeadingAnnotations().stream().noneMatch(this::isBeanAnnotation)) {
                    return m;
                }

                // Check return type is ObjectMapper
                if (m.getReturnTypeExpression() == null || !isObjectMapperType(m.getReturnTypeExpression())) {
                    return m;
                }

                // Check for Jackson2ObjectMapperBuilder parameter
                String builderParamName = null;
                for (Statement p : m.getParameters()) {
                    if (p instanceof J.VariableDeclarations vd) {
                        if (vd.getTypeExpression() != null && isJackson2BuilderType(vd.getTypeExpression())) {
                            builderParamName = vd.getVariables().get(0).getSimpleName();
                            break;
                        }
                    }
                }

                if (builderParamName == null) {
                    return m;
                }

                // Check if the body consists only of 'return builder.build();'
                if (m.getBody() == null || m.getBody().getStatements().size() != 1) {
                    return m;
                }

                Statement stat = m.getBody().getStatements().get(0);
                if (!(stat instanceof J.Return retrn)) {
                    return m;
                }

                if (!(retrn.getExpression() instanceof J.MethodInvocation mi)) {
                    return m;
                }

                if (!"build".equals(mi.getSimpleName()) || mi.getSelect() == null) {
                    return m;
                }

                if (!(mi.getSelect() instanceof J.Identifier select)) {
                    return m;
                }

                if (!builderParamName.equals(select.getSimpleName())) {
                    return m;
                }

                // Match! Now replace.
                maybeAddImport("org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer");
                maybeAddImport("org.springframework.context.annotation.Bean");
                maybeRemoveImport("com.fasterxml.jackson.databind.ObjectMapper");
                maybeRemoveImport("tools.jackson.databind.ObjectMapper");
                maybeRemoveImport("org.springframework.http.converter.json.Jackson2ObjectMapperBuilder");
                maybeRemoveImport("org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer");

                return JavaTemplate.builder(
                        "@Bean\n" +
                        "org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer objectMapperCustomizer() {\n" +
                        "    return builder -> {\n" +
                        "    };\n" +
                        "}")
                        .build()
                        .apply(getCursor(), m.getCoordinates().replace());
            }

            private boolean isBeanAnnotation(J.Annotation ann) {
                JavaType type = ann.getType();
                if (TypeUtils.isOfClassType(type, "org.springframework.context.annotation.Bean")) {
                    return true;
                }
                String anno = ann.getAnnotationType().printTrimmed(getCursor());
                return "Bean".equals(anno) || "org.springframework.context.annotation.Bean".equals(anno);
            }

            private boolean isObjectMapperType(org.openrewrite.java.tree.TypeTree typeTree) {
                JavaType type = typeTree.getType();
                if (TypeUtils.isOfClassType(type, "com.fasterxml.jackson.databind.ObjectMapper") ||
                    TypeUtils.isOfClassType(type, "tools.jackson.databind.ObjectMapper")) {
                    return true;
                }
                String printed = typeTree.printTrimmed(getCursor());
                return "ObjectMapper".equals(printed) ||
                       "com.fasterxml.jackson.databind.ObjectMapper".equals(printed) ||
                       "tools.jackson.databind.ObjectMapper".equals(printed);
            }

            private boolean isJackson2BuilderType(org.openrewrite.java.tree.TypeTree typeTree) {
                JavaType type = typeTree.getType();
                if (TypeUtils.isOfClassType(type, "org.springframework.http.converter.json.Jackson2ObjectMapperBuilder")) {
                    return true;
                }
                String printed = typeTree.printTrimmed(getCursor());
                return "Jackson2ObjectMapperBuilder".equals(printed) ||
                       "org.springframework.http.converter.json.Jackson2ObjectMapperBuilder".equals(printed);
            }
        };
    }
}
