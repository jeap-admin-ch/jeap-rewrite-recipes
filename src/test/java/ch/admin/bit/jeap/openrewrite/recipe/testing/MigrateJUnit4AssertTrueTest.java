package ch.admin.bit.jeap.openrewrite.recipe.testing;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateJUnit4AssertTrueTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new MigrateJUnit4AssertTrue())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesSingleArgumentAssertionWithoutTypeAttribution() {
        rewriteRun(java(
                """
                import static org.junit.Assert.assertTrue;

                class ExampleTest {
                    void test() {
                        assertTrue(true);
                    }
                }
                """,
                """
                import static org.junit.jupiter.api.Assertions.assertTrue;

                class ExampleTest {
                    void test() {
                        assertTrue(true);
                    }
                }
                """
        ));
    }

    @Test
    void swapsJUnit4MessageAndConditionArguments() {
        rewriteRun(java(
                """
                import static org.junit.Assert.assertTrue;

                class ExampleTest {
                    void test() {
                        assertTrue("expected condition", true);
                    }
                }
                """,
                """
                import static org.junit.jupiter.api.Assertions.assertTrue;

                class ExampleTest {
                    void test() {
                        assertTrue(true, "expected condition");
                    }
                }
                """
        ));
    }

    @Test
    void leavesJUnit4WildcardImportUntouched() {
        rewriteRun(java(
                """
                import static org.junit.Assert.*;

                class ExampleTest {
                    void test() {
                        assertTrue(true);
                    }
                }
                """
        ));
    }
}
