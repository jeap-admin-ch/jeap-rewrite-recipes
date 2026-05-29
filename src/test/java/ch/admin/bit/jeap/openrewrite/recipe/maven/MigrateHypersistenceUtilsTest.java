package ch.admin.bit.jeap.openrewrite.recipe.maven;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;

class MigrateHypersistenceUtilsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateHypersistenceUtils());
    }

    @Test
    void migrateHypersistenceUtils() {
        rewriteRun(
            pomXml(
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>io.hypersistence</groupId>
                            <artifactId>hypersistence-utils-hibernate-63</artifactId>
                            <version>3.8.2</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>io.hypersistence</groupId>
                            <artifactId>hypersistence-utils-hibernate-71</artifactId>
                            <version>3.15.2</version>
                            <scope>compile</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
        );
    }
}
