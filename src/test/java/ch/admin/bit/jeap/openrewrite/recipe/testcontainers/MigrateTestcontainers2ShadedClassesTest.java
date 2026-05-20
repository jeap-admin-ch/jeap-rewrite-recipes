package ch.admin.bit.jeap.openrewrite.recipe.testcontainers;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateTestcontainers2ShadedClassesTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new MigrateTestcontainers2ShadedClasses())
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesFieldUtilsShadedImport() {
        rewriteRun(java(
                """
                package com.example;

                import org.testcontainers.shaded.org.apache.commons.lang3.reflect.FieldUtils;

                class MyTest {
                    void test() throws Exception {
                        FieldUtils.writeField(new Object(), "field", "value", true);
                    }
                }
                """,
                """
                package com.example;

                import org.apache.commons.lang3.reflect.FieldUtils;

                class MyTest {
                    void test() throws Exception {
                        FieldUtils.writeField(new Object(), "field", "value", true);
                    }
                }
                """));
    }

    @Test
    void migratesMultipleShadedImports() {
        rewriteRun(java(
                """
                package com.example;

                import org.testcontainers.shaded.org.apache.commons.lang3.reflect.FieldUtils;
                import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils;

                class MyTest {
                    void test() throws Exception {
                        FieldUtils.writeField(new Object(), "field", "value", true);
                        StringUtils.isEmpty("x");
                    }
                }
                """,
                """
                package com.example;

                import org.apache.commons.lang3.StringUtils;
                import org.apache.commons.lang3.reflect.FieldUtils;

                class MyTest {
                    void test() throws Exception {
                        FieldUtils.writeField(new Object(), "field", "value", true);
                        StringUtils.isEmpty("x");
                    }
                }
                """));
    }

    @Test
    void noChangeWhenNoShadedImports() {
        rewriteRun(java(
                """
                package com.example;

                import org.apache.commons.lang3.reflect.FieldUtils;

                class MyTest {
                    void test() throws Exception {
                        FieldUtils.writeField(new Object(), "field", "value", true);
                    }
                }
                """));
    }

    @Test
    void noChangeWhenNoTestcontainersImports() {
        rewriteRun(java(
                """
                package com.example;

                import java.util.List;

                class MyTest {
                    List<String> items;
                }
                """));
    }
}
