package ch.admin.bit.jeap.openrewrite.recipe.jackson;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class NormalizeJacksonNodeApisTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new NormalizeJacksonNodeApis())
                .parser(JavaParser.fromJavaVersion()
                        .dependsOn(
                                """
                                package com.fasterxml.jackson.databind;
                                public class JsonNode {
                                    public String asText() { return null; }
                                }
                                """,
                                """
                                package tools.jackson.databind;
                                public class JsonNode {
                                    public String asText() { return null; }
                                }
                                """,
                                """
                                package com.fasterxml.jackson.databind.node;
                                import com.fasterxml.jackson.databind.JsonNode;
                                public class ObjectNode extends JsonNode {}
                                """
                        ))
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void normalizeJacksonPackages() {
        rewriteRun(
            java(
                """
                import com.fasterxml.jackson.databind.JsonNode;
                
                class Test {
                    String test(JsonNode node) {
                        return node.asText();
                    }
                }
                """,
                """
                import tools.jackson.databind.JsonNode;
                
                class Test {
                    String test(JsonNode node) {
                        return node.asText();
                    }
                }
                """
            )
        );
    }

    @Test
    void normalizeJacksonPackagesFqn() {
        rewriteRun(
            java(
                """
                class Test {
                    String test(com.fasterxml.jackson.databind.JsonNode node) {
                        return node.asText();
                    }
                }
                """,
                """
                class Test {
                    String test(tools.jackson.databind.JsonNode node) {
                        return node.asText();
                    }
                }
                """
            )
        );
    }



    @Test
    void migratesJsonNodeAsStringToAsText() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.databind.JsonNode;

                class Test {
                    String test(JsonNode node) {
                        return node.asString();
                    }
                }
                """,
                """
                import tools.jackson.databind.JsonNode;

                class Test {
                    String test(JsonNode node) {
                        return node.asText();
                    }
                }
                """
        ));
    }

    @Test
    void replacesRawMapperCreationInTests() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.databind.ObjectMapper;
                import com.fasterxml.jackson.databind.json.JsonMapper;

                class MapperTest {
                    ObjectMapper mapper1 = new ObjectMapper();
                    ObjectMapper mapper2 = new JsonMapper();
                }
                """,
                """
                import tools.jackson.databind.ObjectMapper;
                import tools.jackson.databind.json.JsonMapper;

                class MapperTest {
                    ObjectMapper mapper1 = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
                    ObjectMapper mapper2 = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
                }
                """,
                spec -> spec.path("src/test/java/com/example/MapperTest.java")
        ));
    }

    @Test
    void preservesValidChangeDefaultPropertyInclusionForToolsBuilder() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.annotation.JsonInclude;
                import tools.jackson.databind.ObjectMapper;

                class MapperConfig {
                    static final ObjectMapper mapper =
                            tools.jackson.databind.json.JsonMapper.builder()
                                    .changeDefaultPropertyInclusion(
                                            incl ->
                                                    incl.withContentInclusion(JsonInclude.Include.NON_NULL)
                                                            .withValueInclusion(JsonInclude.Include.NON_NULL))
                                    .build();
                }
                """
        ));
    }

    @Test
    void migratesChangeDefaultPropertyInclusionWithCorrectAnnotationImport() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.annotation.JsonInclude;
                import com.fasterxml.jackson.databind.ObjectMapper;

                class MapperConfig {
                    static final ObjectMapper mapper =
                            com.fasterxml.jackson.databind.json.JsonMapper.builder()
                                    .changeDefaultPropertyInclusion(
                                            incl ->
                                                    incl.withContentInclusion(JsonInclude.Include.NON_NULL)
                                                            .withValueInclusion(JsonInclude.Include.NON_NULL))
                                    .build();
                }
                """,
                """
                import com.fasterxml.jackson.annotation.JsonInclude;
                import tools.jackson.databind.ObjectMapper;

                class MapperConfig {
                    static final ObjectMapper mapper =
                            tools.jackson.databind.json.JsonMapper.builder()
                                    .changeDefaultPropertyInclusion(
                                            incl ->
                                                    incl.withContentInclusion(JsonInclude.Include.NON_NULL)
                                                            .withValueInclusion(JsonInclude.Include.NON_NULL))
                                    .build();
                }
                """
        ));
    }

    @Test
    void avoidsMigratingJacksonAnnotations() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.annotation.JsonCreator;
                import com.fasterxml.jackson.annotation.JsonProperty;
                import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
                import com.fasterxml.jackson.databind.JsonNode;

                class Test {
                    @JsonCreator
                    public Test(@JsonProperty("id") String id) {}
                    
                    JsonNode node;
                }
                """,
                """
                import com.fasterxml.jackson.annotation.JsonCreator;
                import com.fasterxml.jackson.annotation.JsonProperty;
                import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
                import tools.jackson.databind.JsonNode;

                class Test {
                    @JsonCreator
                    public Test(@JsonProperty("id") String id) {}
                    
                    JsonNode node;
                }
                """
        ));
    }

    @Test
    void migratesJacksonAnnotationsBack() {
        rewriteRun(java(
                """
                import tools.jackson.annotation.JsonCreator;
                import tools.jackson.annotation.JsonProperty;
                import tools.jackson.annotation.JsonIgnoreProperties;
                import tools.jackson.databind.JsonNode;

                class Test {
                    @JsonCreator
                    public Test(@JsonProperty("id") String id) {}
                    
                    JsonNode node;
                }
                """,
                """
                import com.fasterxml.jackson.annotation.JsonCreator;
                import com.fasterxml.jackson.annotation.JsonProperty;
                import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
                import tools.jackson.databind.JsonNode;

                class Test {
                    @JsonCreator
                    public Test(@JsonProperty("id") String id) {}
                    
                    JsonNode node;
                }
                """
        ));
    }
}
