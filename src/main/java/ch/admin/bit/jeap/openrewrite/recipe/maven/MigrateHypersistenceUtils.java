package ch.admin.bit.jeap.openrewrite.recipe.maven;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.List;

public class MigrateHypersistenceUtils extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate Hypersistence Utils to Hibernate 7.1 compatible version";
    }

    @Override
    public String getDescription() {
        return "Replaces any io.hypersistence dependency with an artifactId containing 'hypersistence-utils' with hypersistence-utils-hibernate-71:3.15.2.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if ("dependency".equals(t.getName())) {
                    String groupId = t.getChildValue("groupId").map(String::trim).orElse("");
                    String artifactId = t.getChildValue("artifactId").map(String::trim).orElse("");

                    if ("io.hypersistence".equals(groupId) && artifactId.contains("hypersistence-utils")) {
                        if ("hypersistence-utils-hibernate-71".equals(artifactId) && 
                            "3.15.2".equals(t.getChildValue("version").map(String::trim).orElse("")) &&
                            "compile".equals(t.getChildValue("scope").map(String::trim).orElse(""))) {
                            return t;
                        }

                        Xml.Tag updatedTag = t.withChildValue("artifactId", "hypersistence-utils-hibernate-71");
                        
                        if (updatedTag.getChild("version").isPresent()) {
                            updatedTag = updatedTag.withChildValue("version", "3.15.2");
                        } else {
                            updatedTag = addChild(updatedTag, "version", "3.15.2");
                        }

                        if (updatedTag.getChild("scope").isPresent()) {
                             updatedTag = updatedTag.withChildValue("scope", "compile");
                        } else {
                             updatedTag = addChild(updatedTag, "scope", "compile");
                        }
                        
                        return autoFormat(updatedTag, ctx);
                    }
                }
                return t;
            }

            private Xml.Tag addChild(Xml.Tag tag, String name, String value) {
                List<Content> content = new ArrayList<>(tag.getContent() != null ? tag.getContent() : List.of());
                content.add(Xml.Tag.build("<" + name + ">" + value + "</" + name + ">"));
                return tag.withContent(content);
            }
        };
    }
}
