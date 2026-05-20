package ch.admin.bit.jeap.openrewrite.recipe.jackson;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateJackson3JsonCreatorPrimitivesToBoxedTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new MigrateJackson3JsonCreatorPrimitivesToBoxed())
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesIntToInteger() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.annotation.JsonCreator;
                import com.fasterxml.jackson.annotation.JsonProperty;

                class Page {
                    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
                    public Page(@JsonProperty("number") int number) {}
                }
                """,
                """
                import com.fasterxml.jackson.annotation.JsonCreator;
                import com.fasterxml.jackson.annotation.JsonProperty;

                class Page {
                    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
                    public Page(@JsonProperty("number") Integer number) {}
                }
                """
        ));
    }

    @Test
    void migratesBooleanAndMultiplePrimitives() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.annotation.JsonCreator;
                import com.fasterxml.jackson.annotation.JsonProperty;
                import java.util.List;

                class CustomPage<T> {
                    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
                    public CustomPage(
                            @JsonProperty("content") List<T> content,
                            @JsonProperty("number") int number,
                            @JsonProperty("size") int size,
                            @JsonProperty("totalElements") Long totalElements,
                            @JsonProperty("last") boolean last,
                            @JsonProperty("totalPages") int totalPages,
                            @JsonProperty("numberOfElements") int numberOfElements) {}
                }
                """,
                """
                import com.fasterxml.jackson.annotation.JsonCreator;
                import com.fasterxml.jackson.annotation.JsonProperty;
                import java.util.List;

                class CustomPage<T> {
                    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
                    public CustomPage(
                            @JsonProperty("content") List<T> content,
                            @JsonProperty("number") Integer number,
                            @JsonProperty("size") Integer size,
                            @JsonProperty("totalElements") Long totalElements,
                            @JsonProperty("last") Boolean last,
                            @JsonProperty("totalPages") Integer totalPages,
                            @JsonProperty("numberOfElements") Integer numberOfElements) {}
                }
                """
        ));
    }

    @Test
    void migratesAllPrimitiveTypes() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.annotation.JsonCreator;
                import com.fasterxml.jackson.annotation.JsonProperty;

                class Dto {
                    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
                    public Dto(
                            @JsonProperty("a") int a,
                            @JsonProperty("b") long b,
                            @JsonProperty("c") boolean c,
                            @JsonProperty("d") float d,
                            @JsonProperty("e") double e,
                            @JsonProperty("f") short f,
                            @JsonProperty("g") byte g,
                            @JsonProperty("h") char h) {}
                }
                """,
                """
                import com.fasterxml.jackson.annotation.JsonCreator;
                import com.fasterxml.jackson.annotation.JsonProperty;

                class Dto {
                    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
                    public Dto(
                            @JsonProperty("a") Integer a,
                            @JsonProperty("b") Long b,
                            @JsonProperty("c") Boolean c,
                            @JsonProperty("d") Float d,
                            @JsonProperty("e") Double e,
                            @JsonProperty("f") Short f,
                            @JsonProperty("g") Byte g,
                            @JsonProperty("h") Character h) {}
                }
                """
        ));
    }

    @Test
    void noChangeWhenNoJsonCreatorAnnotation() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.annotation.JsonCreator;
                import com.fasterxml.jackson.annotation.JsonProperty;

                class Page {
                    public Page(@JsonProperty("number") int number) {}
                }
                """
        ));
    }

    @Test
    void noChangeWhenNoJsonPropertyAnnotation() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.annotation.JsonCreator;

                class Page {
                    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
                    public Page(int number) {}
                }
                """
        ));
    }

    @Test
    void noChangeWhenParamAlreadyBoxed() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.annotation.JsonCreator;
                import com.fasterxml.jackson.annotation.JsonProperty;

                class Page {
                    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
                    public Page(@JsonProperty("number") Integer number) {}
                }
                """
        ));
    }

    @Test
    void noChangeWhenNotAConstructor() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.annotation.JsonCreator;
                import com.fasterxml.jackson.annotation.JsonProperty;

                class Page {
                    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
                    public static Page create(@JsonProperty("number") int number) {
                        return new Page();
                    }

                    private Page() {}
                }
                """
        ));
    }

    @Test
    void noChangeWhenFileHasNoJsonCreatorImport() {
        rewriteRun(java(
                """
                class Unrelated {
                    public Unrelated(int number) {}
                }
                """
        ));
    }
}
