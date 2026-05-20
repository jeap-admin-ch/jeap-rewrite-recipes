package ch.admin.bit.jeap.openrewrite.recipe.resilience;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateSpringRetryToSpringFramework7Test implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new MigrateSpringRetryToSpringFramework7())
                // No spring-retry on classpath — simulates migration scenario
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesRetryableImport() {
        rewriteRun(java(
                """
                import org.springframework.retry.annotation.Retryable;

                class MyService {
                    @Retryable
                    public void callExternalService() {}
                }
                """,
                """
                import org.springframework.resilience.annotation.Retryable;

                class MyService {
                    @Retryable
                    public void callExternalService() {}
                }
                """
        ));
    }

    @Test
    void migratesEnableRetryToEnableResilientMethods() {
        rewriteRun(java(
                """
                import org.springframework.retry.annotation.EnableRetry;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                @EnableRetry
                public class MyApplication {
                    public static void main(String[] args) {}
                }
                """,
                """
                import org.springframework.resilience.annotation.EnableResilientMethods;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                @EnableResilientMethods
                public class MyApplication {
                    public static void main(String[] args) {}
                }
                """
        ));
    }

    @Test
    void migratesMaxAttemptsToMaxRetries() {
        rewriteRun(java(
                """
                import org.springframework.retry.annotation.Retryable;

                class MyService {
                    @Retryable(maxAttempts = 3)
                    public void callExternalService() {}
                }
                """,
                """
                import org.springframework.resilience.annotation.Retryable;

                class MyService {
                    @Retryable(maxRetries = 2)
                    public void callExternalService() {}
                }
                """
        ));
    }

    @Test
    void migratesMaxAttemptsWithValueOne() {
        // maxAttempts = 1 means no retries; maxRetries = 0 is the equivalent
        rewriteRun(java(
                """
                import org.springframework.retry.annotation.Retryable;

                class MyService {
                    @Retryable(maxAttempts = 1)
                    public void callExternalService() {}
                }
                """,
                """
                import org.springframework.resilience.annotation.Retryable;

                class MyService {
                    @Retryable(maxRetries = 0)
                    public void callExternalService() {}
                }
                """
        ));
    }

    @Test
    void migratesRetryTemplateImport() {
        rewriteRun(java(
                """
                import org.springframework.retry.support.RetryTemplate;

                class RetryConfig {
                    private final RetryTemplate retryTemplate;

                    RetryConfig(RetryTemplate retryTemplate) {
                        this.retryTemplate = retryTemplate;
                    }
                }
                """,
                """
                import org.springframework.core.retry.RetryTemplate;

                class RetryConfig {
                    private final RetryTemplate retryTemplate;

                    RetryConfig(RetryTemplate retryTemplate) {
                        this.retryTemplate = retryTemplate;
                    }
                }
                """
        ));
    }

    @Test
    void migratesAllThreeImportsTogether() {
        rewriteRun(java(
                """
                import org.springframework.retry.annotation.EnableRetry;
                import org.springframework.retry.annotation.Retryable;
                import org.springframework.retry.support.RetryTemplate;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                @EnableRetry
                public class MyApplication {
                    private RetryTemplate retryTemplate;

                    @Retryable(maxAttempts = 5)
                    public void callExternalService() {}
                }
                """,
                """
                import org.springframework.core.retry.RetryTemplate;
                import org.springframework.resilience.annotation.EnableResilientMethods;
                import org.springframework.resilience.annotation.Retryable;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                @EnableResilientMethods
                public class MyApplication {
                    private RetryTemplate retryTemplate;

                    @Retryable(maxRetries = 4)
                    public void callExternalService() {}
                }
                """
        ));
    }

    @Test
    void doesNotChangeWhenNoSpringRetryImports() {
        rewriteRun(java(
                """
                import org.springframework.stereotype.Service;

                @Service
                class MyService {
                    public void callExternalService() {}
                }
                """
        ));
    }

    @Test
    void flattensSimpleBackoffAndMigratesRetryFor() {
        rewriteRun(java(
                """
                import org.springframework.retry.annotation.Backoff;
                import org.springframework.retry.annotation.Recover;
                import org.springframework.retry.annotation.Retryable;
                import org.springframework.web.client.RestClientException;

                class MyService {
                    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
                    public void callExternalService() {}

                    @Recover
                    public void recover(Exception e) {}
                }
                """,
                """
                import org.springframework.resilience.annotation.Retryable;
                import org.springframework.retry.annotation.Backoff;
                import org.springframework.retry.annotation.Recover;
                import org.springframework.web.client.RestClientException;

                class MyService {
                    @Retryable(value = RestClientException.class, maxRetries = 2, delay = 1000)
                    public void callExternalService() {}

                    @Recover
                    public void recover(Exception e) {}
                }
                """
        ));
    }
}
