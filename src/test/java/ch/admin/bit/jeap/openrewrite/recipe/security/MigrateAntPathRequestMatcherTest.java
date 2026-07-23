package ch.admin.bit.jeap.openrewrite.recipe.security;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateAntPathRequestMatcherTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new MigrateAntPathRequestMatcher())
            .parser(JavaParser.fromJavaVersion().classpath("spring-security-web"))
            // PathPatternRequestMatcher lives in Spring Security 7 which is not on the 6.x test
            // classpath, so the output will have unresolved type info — that is expected here.
            .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesOneArgConstructor() {
        rewriteRun(java(
                """
                import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

                class SecurityConfig {
                    void configure() {
                        var matcher = new AntPathRequestMatcher("/api/**");
                    }
                }
                """,
                """
                import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

                class SecurityConfig {
                    void configure() {
                        var matcher = PathPatternRequestMatcher.pathPattern("/api/**");
                    }
                }
                """
        ));
    }

    @Test
    void migratesTwoArgConstructor() {
        rewriteRun(java(
                """
                import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

                class SecurityConfig {
                    void configure() {
                        var matcher = new AntPathRequestMatcher("/api/**", "GET");
                    }
                }
                """,
                """
                import org.springframework.http.HttpMethod;
                import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

                class SecurityConfig {
                    void configure() {
                        var matcher = PathPatternRequestMatcher.pathPattern(HttpMethod.valueOf("GET"), "/api/**");
                    }
                }
                """
        ));
    }

    @Test
    void migratesThreeArgConstructor() {
        rewriteRun(java(
                """
                import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

                class SecurityConfig {
                    void configure() {
                        var matcher = new AntPathRequestMatcher("/api/**", "POST", true);
                    }
                }
                """,
                """
                import org.springframework.http.HttpMethod;
                import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

                class SecurityConfig {
                    void configure() {
                        var matcher = PathPatternRequestMatcher.pathPattern(HttpMethod.valueOf("POST"), "/api/**");
                    }
                }
                """
        ));
    }

    @Test
    void migratesAntMatcherStaticFactory() {
        rewriteRun(java(
                """
                import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

                class SecurityConfig {
                    void configure() {
                        var matcher = AntPathRequestMatcher.antMatcher("/images/**");
                    }
                }
                """,
                """
                import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

                class SecurityConfig {
                    void configure() {
                        var matcher = PathPatternRequestMatcher.pathPattern("/images/**");
                    }
                }
                """
        ));
    }

    @Test
    void migratesMultipleUsagesInOneFile() {
        rewriteRun(java(
                """
                import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
                import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
                import org.springframework.security.web.util.matcher.OrRequestMatcher;

                class FrontendWebSecurityConfig {
                    void configure() {
                        var apiConfigMatcher = new AntPathRequestMatcher("/api/configuration/**");
                        var apiMatcher = new AntPathRequestMatcher("/api/**");
                        var securityMatcher = new OrRequestMatcher(apiConfigMatcher,
                                new NegatedRequestMatcher(apiMatcher));
                    }
                }
                """,
                """
                import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
                import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
                import org.springframework.security.web.util.matcher.OrRequestMatcher;

                class FrontendWebSecurityConfig {
                    void configure() {
                        var apiConfigMatcher = PathPatternRequestMatcher.pathPattern("/api/configuration/**");
                        var apiMatcher = PathPatternRequestMatcher.pathPattern("/api/**");
                        var securityMatcher = new OrRequestMatcher(apiConfigMatcher,
                                new NegatedRequestMatcher(apiMatcher));
                    }
                }
                """
        ));
    }

    @Test
    void migratesWhenTypeIsUnresolved() {
        // Simulates real Spring Boot 4 migration: AntPathRequestMatcher is not on the classpath
        // (Spring Security 7 removed it), so type resolution fails. The recipe must fall back to
        // matching by import text and class simple-name.
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion() /* no spring-security-web classpath */),
                java(
                        """
                        import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
                        import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
                        import org.springframework.security.web.util.matcher.OrRequestMatcher;

                        class FrontendWebSecurityConfig {
                            void configure() {
                                var apiConfigMatcher = new AntPathRequestMatcher("/api/configuration/**");
                                var apiMatcher = new AntPathRequestMatcher("/api/**");
                                var securityMatcher = new OrRequestMatcher(apiConfigMatcher,
                                        new NegatedRequestMatcher(apiMatcher));
                            }
                        }
                        """,
                        """
                        import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
                        import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
                        import org.springframework.security.web.util.matcher.OrRequestMatcher;

                        class FrontendWebSecurityConfig {
                            void configure() {
                                var apiConfigMatcher = PathPatternRequestMatcher.pathPattern("/api/configuration/**");
                                var apiMatcher = PathPatternRequestMatcher.pathPattern("/api/**");
                                var securityMatcher = new OrRequestMatcher(apiConfigMatcher,
                                        new NegatedRequestMatcher(apiMatcher));
                            }
                        }
                        """
                ));
    }

    @Test
    void migratesWildcardImportWhenTypeIsUnresolved() {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion()),
                java(
                        """
                        import org.springframework.security.web.util.matcher.*;

                        class WebSecurityConfig {
                            void configure() {
                                var api = new AntPathRequestMatcher("/api/**");
                                var matcher = new NegatedRequestMatcher(api);
                            }
                        }
                        """,
                        """
                        import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
                        import org.springframework.security.web.util.matcher.*;

                        class WebSecurityConfig {
                            void configure() {
                                var api = PathPatternRequestMatcher.pathPattern("/api/**");
                                var matcher = new NegatedRequestMatcher(api);
                            }
                        }
                        """
                ));
    }

    @Test
    void noChangeWhenAntPathRequestMatcherNotUsed() {
        rewriteRun(java(
                """
                class SecurityConfig {
                    void configure() {
                        String pattern = "/api/**";
                    }
                }
                """
        ));
    }
}
