package ch.admin.bit.jeap.openrewrite.recipe.maven;

import org.junit.jupiter.api.Test;
import org.openrewrite.maven.MavenParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;

class UpdateLogbookVersionPropertyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpdateLogbookVersionProperty())
                .parser(MavenParser.builder().skipDependencyResolution(true));
    }

    @Test
    void updatesExistingLogbookVersionProperty() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>demo</artifactId>
                          <version>1.0.0</version>
                          <properties>
                            <logbook.version>3.9.0</logbook.version>
                          </properties>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>demo</artifactId>
                          <version>1.0.0</version>
                          <properties>
                            <logbook.version>4.0.4</logbook.version>
                          </properties>
                        </project>
                        """
                )
        );
    }

    @Test
    void noChangeWhenPropertyMissing() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>demo</artifactId>
                          <version>1.0.0</version>
                          <properties>
                            <other.version>1.2.3</other.version>
                          </properties>
                        </project>
                        """
                )
        );
    }

    @Test
    void noChangeWhenAlreadyTargetVersion() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>demo</artifactId>
                          <version>1.0.0</version>
                          <properties>
                            <logbook.version>4.0.4</logbook.version>
                          </properties>
                        </project>
                        """
                )
        );
    }
}
