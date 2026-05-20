package ch.admin.bit.jeap.openrewrite.recipe.jackson;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.yaml.MergeYaml;

import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

/**
 * Tests for the MergeYaml + AddProperty recipe entries added to spring-boot-40-minimal.yml
 * to set {@code spring.jackson.deserialization.fail-on-null-for-primitives=false}.
 */
class SetJacksonFailOnNullForPrimitivesTest implements RewriteTest {

    private static final String TODO_COMMENT =
            "TODO: Jackson 3 changed FAIL_ON_NULL_FOR_PRIMITIVES default to true - evaluate if you want stricter null-for-primitives validation";

    // --- YAML tests ---

    @Test
    void mergeYaml_addsPropertyToEmptyApplicationYml() {
        rewriteRun(
                spec -> spec.recipeFromResource(
                        "/META-INF/rewrite/spring-boot-40-minimal.yml",
                        "ch.admin.bit.jeap.openrewrite.recipe.jackson.AddJacksonFailOnNullForPrimitivesToApplicationYaml"),
                yaml(
                        """
                        server:
                          port: 8080
                        """,
                        """
                        server:
                          port: 8080
                        spring:
                          jackson:
                            deserialization:
                              # TODO: Jackson 3 changed FAIL_ON_NULL_FOR_PRIMITIVES default to true - evaluate if you want stricter null-for-primitives validation
                              fail-on-null-for-primitives: false
                        """,
                        spec -> spec.path("src/main/resources/application.yml")
                )
        );
    }

    @Test
    void mergeYaml_addsPropertyWhenSpringKeyExists() {
        rewriteRun(
                spec -> spec.recipeFromResource(
                        "/META-INF/rewrite/spring-boot-40-minimal.yml",
                        "ch.admin.bit.jeap.openrewrite.recipe.jackson.AddJacksonFailOnNullForPrimitivesToApplicationYaml"),
                yaml(
                        """
                        spring:
                          datasource:
                            url: jdbc:postgresql://localhost/mydb
                        """,
                        """
                        spring:
                          datasource:
                            url: jdbc:postgresql://localhost/mydb
                          jackson:
                            deserialization:
                              # TODO: Jackson 3 changed FAIL_ON_NULL_FOR_PRIMITIVES default to true - evaluate if you want stricter null-for-primitives validation
                              fail-on-null-for-primitives: false
                        """,
                        spec -> spec.path("src/main/resources/application.yml")
                )
        );
    }

    @Test
    void mergeYaml_doesNotOverwriteExistingValue() {
        rewriteRun(
                spec -> spec.recipeFromResource(
                        "/META-INF/rewrite/spring-boot-40-minimal.yml",
                        "ch.admin.bit.jeap.openrewrite.recipe.jackson.AddJacksonFailOnNullForPrimitivesToApplicationYaml"),
                yaml(
                        // acceptTheirs: true means existing value is kept unchanged
                        """
                        spring:
                          jackson:
                            deserialization:
                              fail-on-null-for-primitives: true
                        """,
                        spec -> spec.path("src/main/resources/application.yml")
                )
        );
    }

    @Test
    void mergeYaml_doesNotApplyToNonApplicationYaml() {
        rewriteRun(
                spec -> spec.recipeFromResource(
                        "/META-INF/rewrite/spring-boot-40-minimal.yml",
                        "ch.admin.bit.jeap.openrewrite.recipe.jackson.AddJacksonFailOnNullForPrimitivesToApplicationYaml"),
                yaml(
                        // filePattern is **/application.yml, so other YAML files are unchanged
                        """
                        some:
                          config: value
                        """,
                        spec -> spec.path("src/main/resources/other-config.yml")
                )
        );
    }

    @Test
    void mergeYaml_doesNotApplyToApplicationProfileYaml() {
        rewriteRun(
                spec -> spec.recipeFromResource(
                        "/META-INF/rewrite/spring-boot-40-minimal.yml",
                        "ch.admin.bit.jeap.openrewrite.recipe.jackson.AddJacksonFailOnNullForPrimitivesToApplicationYaml"),
                yaml(
                        // Profile files like application-test.yml must not be modified
                        """
                        some:
                          config: value
                        """,
                        spec -> spec.path("src/test/resources/application-test.yml")
                )
        );
    }

// --- Properties tests ---

    @Test
    void addProperty_addsPropertyToApplicationProperties() {
        rewriteRun(
                spec -> spec.recipeFromResource(
                        "/META-INF/rewrite/spring-boot-40-minimal.yml",
                        "ch.admin.bit.jeap.openrewrite.recipe.jackson.AddJacksonFailOnNullForPrimitivesToApplicationProperties"),
                properties(
                        """
                        server.port=8080
                        """,
                        """
                        server.port=8080
                        # TODO: Jackson 3 changed FAIL_ON_NULL_FOR_PRIMITIVES default to true - evaluate if you want stricter null-for-primitives validation
                        spring.jackson.deserialization.fail-on-null-for-primitives=false
                        """,
                        spec -> spec.path("src/main/resources/application.properties")
                )
        );
    }

    @Test
    void addProperty_doesNotDuplicateIfAlreadyPresent() {
        rewriteRun(
                spec -> spec.recipeFromResource(
                        "/META-INF/rewrite/spring-boot-40-minimal.yml",
                        "ch.admin.bit.jeap.openrewrite.recipe.jackson.AddJacksonFailOnNullForPrimitivesToApplicationProperties"),
                properties(
                        """
                        spring.jackson.deserialization.fail-on-null-for-primitives=false
                        """,
                        spec -> spec.path("src/main/resources/application.properties")
                )
        );
    }

    @Test
    void addProperty_doesNotApplyToProfileProperties() {
        rewriteRun(
                spec -> spec.recipeFromResource(
                        "/META-INF/rewrite/spring-boot-40-minimal.yml",
                        "ch.admin.bit.jeap.openrewrite.recipe.jackson.AddJacksonFailOnNullForPrimitivesToApplicationProperties"),
                properties(
                        // Profile-specific files like application-test.properties must not be modified
                        """
                        server.port=8080
                        """,
                        spec -> spec.path("src/test/resources/application-test.properties")
                )
        );
    }

    private MergeYaml mergeYamlRecipe() {
        return new MergeYaml(
                "$",
                "spring:\n  jackson:\n    deserialization:\n      # " + TODO_COMMENT + "\n      fail-on-null-for-primitives: false\n",
                true,   // acceptTheirs
                null,   // objectIdentifyingProperty
                "**/application.yml",
                null,   // insertMode
                null,   // insertProperty
                null    // createNewKeys
        );
    }
}
