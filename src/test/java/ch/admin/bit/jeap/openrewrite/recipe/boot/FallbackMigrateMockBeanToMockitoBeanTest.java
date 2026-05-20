package ch.admin.bit.jeap.openrewrite.recipe.boot;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class FallbackMigrateMockBeanToMockitoBeanTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new FallbackMigrateMockBeanToMockitoBean())
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesMockBeanImportAndAnnotation() {
        rewriteRun(java(
                """
                import org.springframework.boot.test.mock.mockito.MockBean;

                class MyTest {
                    @MockBean
                    private MyService service;
                }
                """,
                """
                import org.springframework.test.context.bean.override.mockito.MockitoBean;

                class MyTest {
                    @MockitoBean
                    private MyService service;
                }
                """
        ));
    }
}
