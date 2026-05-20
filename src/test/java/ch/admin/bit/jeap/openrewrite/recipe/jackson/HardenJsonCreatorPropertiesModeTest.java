package ch.admin.bit.jeap.openrewrite.recipe.jackson;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class HardenJsonCreatorPropertiesModeTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new HardenJsonCreatorPropertiesMode())
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void hardensAmbiguousMultiArgJsonCreator() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.annotation.JsonCreator;

                class ProofOfPossession {
                    private final String keyId;
                    private final String algorithm;
                    private final String proofType;
                    private final String value;

                    @JsonCreator
                    ProofOfPossession(String keyId, String algorithm, String proofType, String value) {
                        this.keyId = keyId;
                        this.algorithm = algorithm;
                        this.proofType = proofType;
                        this.value = value;
                    }
                }
                """,
                """
                import com.fasterxml.jackson.annotation.JsonCreator;
                import com.fasterxml.jackson.annotation.JsonProperty;

                class ProofOfPossession {
                    private final String keyId;
                    private final String algorithm;
                    private final String proofType;
                    private final String value;

                    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
                    ProofOfPossession(@JsonProperty("keyId") String keyId, @JsonProperty("algorithm") String algorithm, @JsonProperty("proofType") String proofType, @JsonProperty("value") String value) {
                        this.keyId = keyId;
                        this.algorithm = algorithm;
                        this.proofType = proofType;
                        this.value = value;
                    }
                }
                """
        ));
    }

    @Test
    void addsMissingJsonPropertyOnly() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.annotation.JsonCreator;
                import com.fasterxml.jackson.annotation.JsonProperty;

                class Demo {
                    @JsonCreator(mode = JsonCreator.Mode.DEFAULT)
                    Demo(@JsonProperty("keyId") String keyId, String algorithm) {
                    }
                }
                """,
                """
                import com.fasterxml.jackson.annotation.JsonCreator;
                import com.fasterxml.jackson.annotation.JsonProperty;

                class Demo {
                    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
                    Demo(@JsonProperty("keyId") String keyId, @JsonProperty("algorithm") String algorithm) {
                    }
                }
                """
        ));
    }

    @Test
    void noChangeWhenAlreadyPropertiesAndCovered() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.annotation.JsonCreator;
                import com.fasterxml.jackson.annotation.JsonProperty;

                class Demo {
                    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
                    Demo(@JsonProperty("keyId") String keyId, @JsonProperty("algorithm") String algorithm) {
                    }
                }
                """
        ));
    }

    @Test
    void noChangeWhenDelegatingSingleArg() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.annotation.JsonCreator;

                class Demo {
                    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
                    Demo(String value) {
                    }
                }
                """
        ));
    }
}
