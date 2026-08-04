package ch.admin.bit.jeap.openrewrite.recipe.resilience;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class RemoveSpringRetryDependencyIfUnusedTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveSpringRetryDependencyIfUnused())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void removesUnusedDependency() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>com.example</groupId>
                            <artifactId>example</artifactId>
                            <version>1.0.0</version>
                            <dependencies>
                                <dependency>
                                    <groupId>org.springframework.retry</groupId>
                                    <artifactId>spring-retry</artifactId>
                                    <version>2.0.13</version>
                                </dependency>
                            </dependencies>
                        </project>
                        """,
                        """
                        <project>
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>com.example</groupId>
                            <artifactId>example</artifactId>
                            <version>1.0.0</version>
                        </project>
                        """),
                java("""
                     import org.springframework.resilience.annotation.Retryable;

                     class MyService {
                         @Retryable
                         void call() {}
                     }
                     """));
    }

    @Test
    void retainsDependencyForRemainingUsage() {
        rewriteRun(
                pomXml("""
                       <project>
                           <modelVersion>4.0.0</modelVersion>
                           <groupId>com.example</groupId>
                           <artifactId>example</artifactId>
                           <version>1.0.0</version>
                           <dependencies>
                               <dependency>
                                   <groupId>org.springframework.retry</groupId>
                                   <artifactId>spring-retry</artifactId>
                                   <version>2.0.13</version>
                               </dependency>
                           </dependencies>
                       </project>
                       """),
                java("""
                     import org.springframework.retry.annotation.Recover;

                     class MyService {
                         @Recover
                         void recover(RuntimeException exception) {}
                     }
                     """));
    }
}
