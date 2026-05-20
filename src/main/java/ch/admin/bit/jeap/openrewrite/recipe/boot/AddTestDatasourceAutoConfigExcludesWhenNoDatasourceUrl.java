package ch.admin.bit.jeap.openrewrite.recipe.boot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.MergeYaml;

import java.util.Locale;
import java.util.regex.Pattern;

public class AddTestDatasourceAutoConfigExcludesWhenNoDatasourceUrl extends ScanningRecipe<AddTestDatasourceAutoConfigExcludesWhenNoDatasourceUrl.Signals> {

    private static final Pattern DATASOURCE_URL_PATTERN = Pattern.compile("(?s)datasource\\s*:\\s*.*?url\\s*:");
    private static final Pattern R2DBC_URL_PATTERN = Pattern.compile("(?s)r2dbc\\s*:\\s*.*?url\\s*:");

    private static final String EXCLUDES_YAML = """
            spring:
              autoconfigure:
                exclude:
                  - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
                  - org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
                  - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
                  - org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
                  - org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration
            """;

    @Override
    public String getDisplayName() {
        return "Add test datasource auto-config excludes when no datasource URL exists";
    }

    @Override
    public String getDescription() {
        return "Adds spring.autoconfigure.exclude entries to application-test.yml only when no " +
               "spring.datasource.url or spring.r2dbc.url is configured anywhere in the project.";
    }

    @Override
    public Signals getInitialValue(ExecutionContext ctx) {
        return new Signals();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Signals acc) {
        return new TreeVisitor<>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                Tree visited = super.visit(tree, ctx);
                if (acc.hasDatasourceUrl) {
                    return visited;
                }
                if (!(visited instanceof SourceFile sf)) {
                    return visited;
                }
                String text = sf.printAll().toLowerCase(Locale.ROOT);
                if (hasDatasourceMarker(text)) {
                    acc.hasDatasourceUrl = true;
                }
                return visited;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Signals acc) {
        if (acc.hasDatasourceUrl) {
            return TreeVisitor.noop();
        }
        return new MergeYaml(
                "$",
                EXCLUDES_YAML,
                true,
                null,
                "**/application-test.yml",
                null,
                null,
                true
        ).getVisitor();
    }

    public static class Signals {
        boolean hasDatasourceUrl;
    }

    private static boolean hasDatasourceMarker(String text) {
        return text.contains("spring.datasource.url")
               || text.contains("spring.r2dbc.url")
               || DATASOURCE_URL_PATTERN.matcher(text).find()
               || R2DBC_URL_PATTERN.matcher(text).find();
    }
}
