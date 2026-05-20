package ch.admin.bit.jeap.openrewrite.recipe.boot;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class NormalizeObjectMapperConfigurationTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new NormalizeObjectMapperConfiguration())
                .parser(JavaParser.fromJavaVersion()
                        .dependsOn(
                                """
                                package com.fasterxml.jackson.databind;
                                public class ObjectMapper {}
                                """,
                                """
                                package org.springframework.context.annotation;
                                public @interface Bean {}
                                """,
                                """
                                package org.springframework.context.annotation;
                                public @interface Configuration {}
                                """,
                                """
                                package org.springframework.http.converter.json;
                                import com.fasterxml.jackson.databind.ObjectMapper;
                                public class Jackson2ObjectMapperBuilder {
                                    public ObjectMapper build() { return null; }
                                }
                                """,
                                """
                                package org.springframework.boot.jackson.autoconfigure;
                                public interface JsonMapperBuilderCustomizer {}
                                """
                        ))
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void shouldReplaceRedundantObjectMapperBeanWithCustomizer() {
        rewriteRun(
            java(
                """
                import com.fasterxml.jackson.databind.ObjectMapper;
                import org.springframework.context.annotation.Bean;
                import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
                
                public class ObjectMapperConfig {
                    @Bean
                    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
                        return builder.build();
                    }
                }
                """,
                """
                import org.springframework.context.annotation.Bean;
                
                public class ObjectMapperConfig {
                
                    @Bean
                    org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer objectMapperCustomizer() {
                        return builder -> {
                        };
                    }
                }
                """
            )
        );
    }

    @Test
    void shouldNotReplaceIfMethodDoesMoreThanJustBuilding() {
        rewriteRun(
            java(
                """
                package ch.admin.bj.swiyu.core.business.common.infrastructure.jackson;
                
                import com.fasterxml.jackson.databind.ObjectMapper;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
                
                @Configuration
                public class ObjectMapperConfig {
                
                    @Bean
                    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
                        ObjectMapper mapper = builder.build();
                        mapper.findAndRegisterModules();
                        return mapper;
                    }
                }
                """
            )
        );
    }

    @Test
    void shouldAlsoWorkWithoutTypeAttribution() {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion()).typeValidationOptions(TypeValidation.none()),
                java(
                        """
                        import com.fasterxml.jackson.databind.ObjectMapper;
                        import org.springframework.context.annotation.Bean;
                        import org.springframework.context.annotation.Configuration;
                        import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

                        @Configuration
                        public class ObjectMapperConfig {
                            @Bean
                            public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
                                return builder.build();
                            }
                        }
                        """,
                        """
                        import org.springframework.context.annotation.Bean;
                        import org.springframework.context.annotation.Configuration;

                        @Configuration
                        public class ObjectMapperConfig {

                            @Bean
                            org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer objectMapperCustomizer() {
                                return builder -> {
                                };
                            }
                        }
                        """
                )
        );
    }
}
