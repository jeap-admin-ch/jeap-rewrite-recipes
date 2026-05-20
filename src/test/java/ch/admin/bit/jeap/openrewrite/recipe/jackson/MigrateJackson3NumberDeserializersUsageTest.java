package ch.admin.bit.jeap.openrewrite.recipe.jackson;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateJackson3NumberDeserializersUsageTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new MigrateJackson3NumberDeserializersUsage());
    }

    @Test
    void migratesToolsStdToToolsJdk() {
        rewriteRun(java(
                """
                import tools.jackson.databind.deser.std.NumberDeserializers;

                class MyDeserializerConfig {
                    Object deserializer = NumberDeserializers.NumberDeserializer.instance;
                }
                """,
                """
                import tools.jackson.databind.deser.jdk.NumberDeserializers;

                class MyDeserializerConfig {
                    Object deserializer = NumberDeserializers.NumberDeserializer.instance;
                }
                """
        ));
    }

    @Test
    void migratesFasterxmlStdToToolsJdk() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.databind.deser.std.NumberDeserializers;

                class MyDeserializerConfig {
                    Object deserializer = NumberDeserializers.NumberDeserializer.instance;
                }
                """,
                """
                import tools.jackson.databind.deser.jdk.NumberDeserializers;

                class MyDeserializerConfig {
                    Object deserializer = NumberDeserializers.NumberDeserializer.instance;
                }
                """
        ));
    }
}
