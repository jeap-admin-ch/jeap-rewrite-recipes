package ch.admin.bit.jeap.openrewrite.recipe.jackson;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class FixJackson3JsonMapperNameCollisionTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new FixJackson3JsonMapperNameCollision())
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void removesConflictingImportAndFullyQualifiesBuilderCall() {
        rewriteRun(java(
                """
                package ch.admin.bazg.connex.backoffice.testutil;

                import com.fasterxml.jackson.databind.ObjectMapper;
                import tools.jackson.databind.json.JsonMapper;

                public class JsonMapper {
                    public static final ObjectMapper mapper = JsonMapper.builder().build();
                }
                """,
                """
                package ch.admin.bazg.connex.backoffice.testutil;

                import com.fasterxml.jackson.databind.ObjectMapper;

                public class JsonMapper {
                    public static final ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
                }
                """
        ));
    }

    @Test
    void removesConflictingImportAndFullyQualifiesBuilderCallComFasterxml() {
        rewriteRun(java(
                """
                package ch.admin.bazg.connex.backoffice.testutil;

                import com.fasterxml.jackson.databind.ObjectMapper;
                import com.fasterxml.jackson.databind.json.JsonMapper;

                public class JsonMapper {
                    public static final ObjectMapper mapper = JsonMapper.builder().build();
                }
                """,
                """
                package ch.admin.bazg.connex.backoffice.testutil;

                import com.fasterxml.jackson.databind.ObjectMapper;

                public class JsonMapper {
                    public static final ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
                }
                """
        ));
    }

    @Test
    void noChangeWhenThereIsNoLocalJsonMapperClass() {
        rewriteRun(java(
                """
                import tools.jackson.databind.ObjectMapper;
                import tools.jackson.databind.json.JsonMapper;

                class MapperFactory {
                    ObjectMapper create() {
                        return JsonMapper.builder().build();
                    }
                }
                """
        ));
    }
}
