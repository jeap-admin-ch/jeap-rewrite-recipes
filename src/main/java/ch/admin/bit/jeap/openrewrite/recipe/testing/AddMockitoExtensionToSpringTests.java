package ch.admin.bit.jeap.openrewrite.recipe.testing;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

/**
 * Adds the Mockito JUnit Jupiter extension to Spring tests that use plain {@code @Mock} or {@code @Captor} fields.
 */
public class AddMockitoExtensionToSpringTests extends Recipe {

    private static final String MOCKITO_SETTINGS_FQN = "org.mockito.junit.jupiter.MockitoSettings";
    private static final String STRICTNESS_FQN = "org.mockito.quality.Strictness";

    @Override
    public String getDisplayName() {
        return "Initialize Mockito mocks in Spring tests";
    }

    @Override
    public String getDescription() {
        return "Adds a lenient @ExtendWith(MockitoExtension.class) setup to Spring-context tests that contain " +
               "plain Mockito @Mock or @Captor fields, which are no longer initialized by Spring Boot 4.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration,
                                                             ExecutionContext ctx) {
                J.ClassDeclaration classDecl = super.visitClassDeclaration(classDeclaration, ctx);
                if (!isSpringTest(classDecl) || !hasMockitoField(classDecl) || hasMockitoExtension(classDecl)) {
                    return classDecl;
                }

                maybeAddImport(MOCKITO_SETTINGS_FQN);
                maybeAddImport(STRICTNESS_FQN);
                return JavaTemplate.builder("@MockitoSettings(strictness = Strictness.LENIENT)")
                        .javaParser(JavaParser.fromJavaVersion().dependsOn(
                                "package org.mockito.junit.jupiter; " +
                                "public @interface MockitoSettings { " +
                                "org.mockito.quality.Strictness strictness(); }",
                                "package org.mockito.quality; public enum Strictness { LENIENT }"))
                        .imports(MOCKITO_SETTINGS_FQN, STRICTNESS_FQN)
                        .build()
                        .apply(getCursor(), classDecl.getCoordinates().addAnnotation((left, right) -> 0));
            }

            private boolean isSpringTest(J.ClassDeclaration classDecl) {
                return classDecl.getLeadingAnnotations().stream().anyMatch(annotation -> {
                    String name = annotation.getSimpleName();
                    if ("SpringJUnitConfig".equals(name) || "SpringBootTest".equals(name) ||
                            "DataJpaTest".equals(name)) {
                        return true;
                    }
                    return "ExtendWith".equals(name) &&
                           annotation.printTrimmed(getCursor()).contains("SpringExtension.class");
                });
            }

            private boolean hasMockitoField(J.ClassDeclaration classDecl) {
                for (Statement statement : classDecl.getBody().getStatements()) {
                    if (statement instanceof J.VariableDeclarations variables &&
                            variables.getLeadingAnnotations().stream()
                                    .anyMatch(annotation -> "Mock".equals(annotation.getSimpleName()) ||
                                            "Captor".equals(annotation.getSimpleName()))) {
                        return true;
                    }
                }
                return false;
            }

            private boolean hasMockitoExtension(J.ClassDeclaration classDecl) {
                return classDecl.getLeadingAnnotations().stream()
                        .anyMatch(annotation ->
                                "MockitoSettings".equals(annotation.getSimpleName()) ||
                                ("ExtendWith".equals(annotation.getSimpleName()) &&
                                 annotation.printTrimmed(getCursor()).contains("MockitoExtension.class")));
            }
        };
    }
}
