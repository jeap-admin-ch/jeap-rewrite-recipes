package ch.admin.bit.jeap.openrewrite.recipe.boot;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class EnsureExplicitSpringHttpMediaTypeImportTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new EnsureExplicitSpringHttpMediaTypeImport())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void addsExplicitMediaTypeImportAlongsideWildcard() {
        rewriteRun(java(
                """
                import org.springframework.http.*;

                class PactTest {
                    String contentType() {
                        return MediaType.APPLICATION_JSON_VALUE;
                    }
                }
                """,
                """
                import org.springframework.http.MediaType;
                import org.springframework.http.*;

                class PactTest {
                    String contentType() {
                        return MediaType.APPLICATION_JSON_VALUE;
                    }
                }
                """
        ));
    }

    @Test
    void leavesFileWithoutMediaTypeReferenceUntouched() {
        rewriteRun(java(
                """
                import org.springframework.http.*;

                class PactTest {
                    HttpHeaders headers() {
                        return new HttpHeaders();
                    }
                }
                """
        ));
    }

    @Test
    void leavesExistingExplicitMediaTypeImportUntouched() {
        rewriteRun(java(
                """
                import org.springframework.http.MediaType;
                import org.springframework.http.*;

                class PactTest {
                    String contentType() {
                        return MediaType.APPLICATION_JSON_VALUE;
                    }
                }
                """
        ));
    }

    @Test
    void leavesDifferentHttpWildcardUntouched() {
        rewriteRun(java(
                """
                import example.http.*;

                class PactTest {
                    String contentType() {
                        return MediaType.APPLICATION_JSON_VALUE;
                    }
                }
                """
        ));
    }
}
