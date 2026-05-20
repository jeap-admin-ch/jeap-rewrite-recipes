package ch.admin.bit.jeap.openrewrite.recipe.boot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Spring Boot 4 often fails startup when JPA/Flyway dependencies remain but no driver
 * and no datasource URL are configured.
 *
 * <p>If this recipe detects neither DB driver artifacts nor datasource URL configuration
 * in the project, it rewrites Maven dependencies by:
 * <ul>
 *   <li>removing {@code org.flywaydb:flyway-core}</li>
 *   <li>removing {@code org.liquibase:liquibase-core}</li>
 *   <li>changing {@code org.springframework.data:spring-data-jpa}
 *       to {@code org.springframework.data:spring-data-commons}</li>
 *   <li>changing {@code org.springframework.boot:spring-boot-starter-data-jpa}
 *       to {@code org.springframework.data:spring-data-commons}</li>
 * </ul>
 */
public class RemoveDatabaseAutoConfigDependenciesWhenNoDriverOrDatasource extends ScanningRecipe<RemoveDatabaseAutoConfigDependenciesWhenNoDriverOrDatasource.DbSignals> {

    @Override
    public String getDisplayName() {
        return "Remove DB auto-config dependencies when no driver/datasource is configured";
    }

    @Override
    public String getDescription() {
        return "When a project has neither a DB driver dependency nor a datasource URL configuration, " +
               "remove Flyway/Liquibase and replace Spring Data JPA dependencies with Spring Data Commons.";
    }

    @Override
    public DbSignals getInitialValue(ExecutionContext ctx) {
        return new DbSignals();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(DbSignals acc) {
        return new TreeVisitor<>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                Tree visited = super.visit(tree, ctx);
                if (acc.hasDriver && acc.hasDatasourceUrl) {
                    return visited;
                }
                if (!(visited instanceof SourceFile sf)) {
                    return visited;
                }
                String text = sf.printAll().toLowerCase(Locale.ROOT);
                if (!acc.hasDriver && hasDriverMarker(text)) {
                    acc.hasDriver = true;
                }
                if (!acc.hasDatasourceUrl && hasDatasourceUrlMarker(text)) {
                    acc.hasDatasourceUrl = true;
                }
                return visited;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(DbSignals acc) {
        if (acc.hasDriver || acc.hasDatasourceUrl) {
            return TreeVisitor.noop();
        }
        return new XmlIsoVisitor<>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (!"dependencies".equals(t.getName())) {
                    return t;
                }
                List<? extends Content> content = t.getContent();
                if (content == null || content.isEmpty()) {
                    return t;
                }

                List<Content> updated = new ArrayList<>();
                boolean changed = false;

                for (Content c : content) {
                    if (!(c instanceof Xml.Tag depTag) || !"dependency".equals(depTag.getName())) {
                        updated.add(c);
                        continue;
                    }

                    String depXml = depTag.print(getCursor());
                    String lower = depXml.toLowerCase(Locale.ROOT);

                    if (containsArtifact(lower, "flyway-core") || containsArtifact(lower, "liquibase-core")) {
                        changed = true;
                        continue;
                    }

                    if (containsGroupAndArtifact(lower,
                            "org.springframework.data", "spring-data-jpa")) {
                        String rewritten = depXml.replace(
                                "<artifactId>spring-data-jpa</artifactId>",
                                "<artifactId>spring-data-commons</artifactId>");
                        updated.add(Xml.Tag.build(rewritten));
                        changed = true;
                        continue;
                    }

                    if (containsGroupAndArtifact(lower,
                            "org.springframework.boot", "spring-boot-starter-data-jpa")) {
                        String rewritten = depXml
                                .replace("<groupId>org.springframework.boot</groupId>",
                                        "<groupId>org.springframework.data</groupId>")
                                .replace("<artifactId>spring-boot-starter-data-jpa</artifactId>",
                                        "<artifactId>spring-data-commons</artifactId>");
                        updated.add(Xml.Tag.build(rewritten));
                        changed = true;
                        continue;
                    }

                    updated.add(c);
                }

                return changed ? t.withContent(updated) : t;
            }
        };
    }

    private static boolean hasDriverMarker(String text) {
        return text.contains("<artifactid>postgresql</artifactid>")
               || text.contains("<artifactid>h2</artifactid>")
               || text.contains("<artifactid>mysql-connector-j</artifactid>")
               || text.contains("<artifactid>mariadb-java-client</artifactid>")
               || text.contains("<artifactid>ojdbc")
               || text.contains("<artifactid>mssql-jdbc</artifactid>")
               || text.contains("jdbc:postgresql:")
               || text.contains("jdbc:h2:");
    }

    private static boolean hasDatasourceUrlMarker(String text) {
        return text.contains("spring.datasource.url")
               || text.contains("spring.r2dbc.url")
               || text.contains("datasource.url");
    }

    private static boolean containsArtifact(String depXmlLower, String artifactId) {
        return depXmlLower.contains("<artifactid>" + artifactId + "</artifactid>");
    }

    private static boolean containsGroupAndArtifact(String depXmlLower, String groupId, String artifactId) {
        return depXmlLower.contains("<groupid>" + groupId + "</groupid>")
               && depXmlLower.contains("<artifactid>" + artifactId + "</artifactid>");
    }

    public static class DbSignals {
        boolean hasDriver;
        boolean hasDatasourceUrl;
    }
}
