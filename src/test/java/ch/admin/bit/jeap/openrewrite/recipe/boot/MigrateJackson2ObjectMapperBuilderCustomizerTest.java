package ch.admin.bit.jeap.openrewrite.recipe.boot;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateJackson2ObjectMapperBuilderCustomizerTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new MigrateJackson2ObjectMapperBuilderCustomizer())
                // Spring Boot autoconfigure jackson package is removed in SB4 — disable type validation
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesImportAndReturnType() {
        rewriteRun(java(
                """
                        import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
                        import org.springframework.context.annotation.Bean;
                        import org.springframework.context.annotation.Configuration;

                        @Configuration
                        public class JacksonConfig {

                            @Bean
                            public Jackson2ObjectMapperBuilderCustomizer namingCustomizer() {
                                return builder -> builder.failOnUnknownProperties(true);
                            }
                        }
                        """,
                """
                        import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
                        import org.springframework.context.annotation.Bean;
                        import org.springframework.context.annotation.Configuration;

                        @Configuration
                        public class JacksonConfig {

                            @Bean
                            public JsonMapperBuilderCustomizer namingCustomizer() {
                                return builder -> builder.failOnUnknownProperties(true);
                            }
                        }
                        """
        ));
    }

    @Test
    void migratesPostConfigurerToAddModule() {
        rewriteRun(java(
                """
                        import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
                        import org.springframework.context.annotation.Bean;
                        import org.springframework.context.annotation.Configuration;

                        @Configuration
                        public class JacksonConfig {

                            @Bean
                            public Jackson2ObjectMapperBuilderCustomizer moduleCustomizer() {
                                return builder ->
                                        builder.postConfigurer(objectMapper -> objectMapper.registerModule(new MyJacksonModule()));
                            }
                        }
                        """,
                """
                        import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
                        import org.springframework.context.annotation.Bean;
                        import org.springframework.context.annotation.Configuration;

                        @Configuration
                        public class JacksonConfig {

                            @Bean
                            public JsonMapperBuilderCustomizer moduleCustomizer() {
                                return builder ->
                                        builder.addModule(new MyJacksonModule());
                            }
                        }
                        """
        ));
    }

    @Test
    void migratesMultipleBeans() {
        rewriteRun(java(
                """
                        import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
                        import org.springframework.context.annotation.Bean;
                        import org.springframework.context.annotation.Configuration;

                        @Configuration
                        public class JacksonConfig {

                            @Bean
                            public Jackson2ObjectMapperBuilderCustomizer namingCustomizer() {
                                return builder -> builder.failOnUnknownProperties(true);
                            }

                            @Bean
                            public Jackson2ObjectMapperBuilderCustomizer moduleCustomizer() {
                                return builder ->
                                        builder.postConfigurer(objectMapper -> objectMapper.registerModule(new MyJacksonModule()));
                            }
                        }
                        """,
                """
                        import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
                        import org.springframework.context.annotation.Bean;
                        import org.springframework.context.annotation.Configuration;

                        @Configuration
                        public class JacksonConfig {

                            @Bean
                            public JsonMapperBuilderCustomizer namingCustomizer() {
                                return builder -> builder.failOnUnknownProperties(true);
                            }

                            @Bean
                            public JsonMapperBuilderCustomizer moduleCustomizer() {
                                return builder ->
                                        builder.addModule(new MyJacksonModule());
                            }
                        }
                        """
        ));
    }

    @Test
    void noChangeWhenImportAbsent() {
        rewriteRun(java(
                """
                        import org.springframework.context.annotation.Bean;
                        import org.springframework.context.annotation.Configuration;

                        @Configuration
                        public class OtherConfig {

                            @Bean
                            public String hello() {
                                return "hello";
                            }
                        }
                        """
        ));
    }

    @Test
    void doesNotTransformPostConfigurerWithoutRegisterModule() {
        rewriteRun(java(
                """
                        import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
                        import org.springframework.context.annotation.Bean;
                        import org.springframework.context.annotation.Configuration;

                        @Configuration
                        public class JacksonConfig {

                            @Bean
                            public Jackson2ObjectMapperBuilderCustomizer otherCustomizer() {
                                return builder ->
                                        builder.postConfigurer(objectMapper -> objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
                            }
                        }
                        """,
                """
                        import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
                        import org.springframework.context.annotation.Bean;
                        import org.springframework.context.annotation.Configuration;

                        @Configuration
                        public class JacksonConfig {

                            @Bean
                            public JsonMapperBuilderCustomizer otherCustomizer() {
                                return builder ->
                                        builder.postConfigurer(objectMapper -> objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
                            }
                        }
                        """
        ));
    }
}
