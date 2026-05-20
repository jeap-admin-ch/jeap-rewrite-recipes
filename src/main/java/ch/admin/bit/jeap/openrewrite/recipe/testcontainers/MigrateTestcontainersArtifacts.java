package ch.admin.bit.jeap.openrewrite.recipe.testcontainers;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

public class MigrateTestcontainersArtifacts extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate Testcontainers artifact IDs to 2.x naming convention";
    }

    @Override
    public String getDescription() {
        return "Adds the 'testcontainers-' prefix to Testcontainers module artifacts if it is missing.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                if ("dependency".equals(tag.getName())) {
                    String groupId = tag.getChildValue("groupId").map(String::trim).orElse("");
                    if ("org.testcontainers".equals(groupId)) {
                        String artifactId = tag.getChildValue("artifactId").map(String::trim).orElse("");
                        if (!artifactId.startsWith("testcontainers-") && !"testcontainers".equals(artifactId) && !artifactId.isEmpty()) {
                            return tag.withChildValue("artifactId", "testcontainers-" + artifactId);
                        }
                    }
                }
                return super.visitTag(tag, ctx);
            }
        };
    }
}
