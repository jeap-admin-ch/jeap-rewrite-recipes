package ch.admin.bit.jeap.openrewrite.recipe.maven;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes dependencyManagement BOM import of org.springframework.cloud:spring-cloud-dependencies.
 * In jEAP Spring Boot 4 this BOM is managed by the parent and should not be explicitly imported.
 */
public class RemoveSpringCloudDependenciesBomImport extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove spring-cloud-dependencies BOM import";
    }

    @Override
    public String getDescription() {
        return "Removes org.springframework.cloud:spring-cloud-dependencies from dependencyManagement. " +
               "The jEAP Spring Boot 4 parent already manages this BOM.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (!"dependencies".equals(t.getName()) || !isInDependencyManagement()) {
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

                    String dep = depTag.print(getCursor());
                    if (dep.contains("<groupId>org.springframework.cloud</groupId>") &&
                        dep.contains("<artifactId>spring-cloud-dependencies</artifactId>")) {
                        changed = true;
                        continue;
                    }
                    updated.add(c);
                }

                return changed ? t.withContent(updated) : t;
            }

            private boolean isInDependencyManagement() {
                Object parent = getCursor().getParentOrThrow().getValue();
                return parent instanceof Xml.Tag parentTag && "dependencyManagement".equals(parentTag.getName());
            }
        };
    }
}
