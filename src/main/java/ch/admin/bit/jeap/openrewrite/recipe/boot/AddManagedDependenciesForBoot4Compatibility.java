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
 * Adds managed dependency versions required by Spring Boot 4 migration.
 *
 * <p>Always adds a known set of managed dependencies. Additionally, it adds
 * {@code org.hibernate.orm:hibernate-jpamodelgen} only when datasource URL
 * configuration is detected in an {@code application.yml}.
 */
public class AddManagedDependenciesForBoot4Compatibility extends ScanningRecipe<AddManagedDependenciesForBoot4Compatibility.Signals> {

    @Override
    public String getDisplayName() {
        return "Add Spring Boot 4 compatibility managed dependencies";
    }

    @Override
    public String getDescription() {
        return "Adds managed dependency versions required during Spring Boot 4 migration and " +
               "adds hibernate-jpamodelgen management only when datasource URL configuration exists.";
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
                if (acc.hasDatasourceUrl || !(visited instanceof SourceFile sf)) {
                    return visited;
                }
                String sourcePath = sf.getSourcePath().toString();
                if (!(sourcePath.endsWith("application.yml") || sourcePath.endsWith("application-local.yml"))) {
                    return visited;
                }
                String text = sf.printAll().toLowerCase(Locale.ROOT);
                if (text.contains("spring.datasource.url") ||
                    (text.contains("datasource:") && text.contains("url:"))) {
                    acc.hasDatasourceUrl = true;
                }
                return visited;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Signals acc) {
        return new XmlIsoVisitor<>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (!"project".equals(t.getName()) || !isPom()) {
                    return t;
                }

                List<DependencySpec> specs = new ArrayList<>(List.of(
                        new DependencySpec("org.apache.commons", "commons-compress", "1.28.0", null),
                        new DependencySpec("commons-io", "commons-io", "2.22.0", todoComment("2.22.0")),
                        new DependencySpec("commons-beanutils", "commons-beanutils", "1.11.0", todoComment("1.11.0")),
                        new DependencySpec("org.lz4", "lz4-java", "1.9-inv", todoComment("1.9-inv")),
                        new DependencySpec("at.yawk.lz4", "lz4-java", "1.11.0", todoComment("1.11.0")),
                        new DependencySpec("org.bitbucket.b_c", "jose4j", "0.9.6-oracle-00001", todoComment("0.9.6-oracle-00001"))
                ));

                if (acc.hasDatasourceUrl) {
                    specs.add(new DependencySpec("org.hibernate.orm", "hibernate-jpamodelgen", "7.1.5.Final", null));
                }

                return ensureManagedDependencies(t, specs);
            }

            private boolean isPom() {
                Xml.Document doc = getCursor().firstEnclosing(Xml.Document.class);
                return doc != null && doc.getSourcePath().toString().endsWith("pom.xml");
            }
        };
    }

    private static Xml.Tag ensureManagedDependencies(Xml.Tag projectTag, List<DependencySpec> specs) {
        Xml.Tag dependencyManagement = findDirectChild(projectTag, "dependencyManagement");
        if (dependencyManagement == null) {
            StringBuilder block = new StringBuilder("<dependencyManagement><dependencies>");
            for (DependencySpec spec : specs) {
                block.append(spec.toXml());
            }
            block.append("</dependencies></dependencyManagement>");

            List<Content> updated = new ArrayList<>();
            List<? extends Content> content = projectTag.getContent();
            if (content != null) {
                updated.addAll(content);
            }
            updated.add(Xml.Tag.build(block.toString()));
            return projectTag.withContent(updated);
        }

        Xml.Tag dependencies = findDirectChild(dependencyManagement, "dependencies");
        if (dependencies == null) {
            Xml.Tag newDependencies = Xml.Tag.build("<dependencies></dependencies>");
            List<Content> dmContent = new ArrayList<>();
            if (dependencyManagement.getContent() != null) {
                dmContent.addAll(dependencyManagement.getContent());
            }
            dmContent.add(newDependencies);
            dependencyManagement = dependencyManagement.withContent(dmContent);
            dependencies = newDependencies;
        }

        List<Content> depContent = new ArrayList<>();
        if (dependencies.getContent() != null) {
            depContent.addAll(dependencies.getContent());
        }

        boolean changed = false;
        for (DependencySpec spec : specs) {
            if (!containsDependency(depContent, spec.groupId, spec.artifactId)) {
                depContent.add(Xml.Tag.build(spec.toXml()));
                changed = true;
            }
        }

        if (!changed) {
            return projectTag;
        }

        Xml.Tag updatedDependencies = dependencies.withContent(depContent);
        Xml.Tag updatedDm = replaceDirectChild(dependencyManagement, dependencies, updatedDependencies);
        return replaceDirectChild(projectTag, dependencyManagement, updatedDm);
    }

    private static boolean containsDependency(List<Content> depContent, String groupId, String artifactId) {
        for (Content c : depContent) {
            if (!(c instanceof Xml.Tag dependencyTag) || !"dependency".equals(dependencyTag.getName())) {
                continue;
            }
            Xml.Tag groupTag = findDirectChild(dependencyTag, "groupId");
            Xml.Tag artifactTag = findDirectChild(dependencyTag, "artifactId");
            String existingGroupId = groupTag == null ? null : groupTag.getValue().orElse(null);
            String existingArtifactId = artifactTag == null ? null : artifactTag.getValue().orElse(null);
            if (groupId.equals(existingGroupId) && artifactId.equals(existingArtifactId)) {
                return true;
            }
        }
        return false;
    }

    private static Xml.Tag findDirectChild(Xml.Tag parent, String childName) {
        List<? extends Content> content = parent.getContent();
        if (content == null) {
            return null;
        }
        for (Content c : content) {
            if (c instanceof Xml.Tag child && childName.equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    private static Xml.Tag replaceDirectChild(Xml.Tag parent, Xml.Tag oldChild, Xml.Tag newChild) {
        List<? extends Content> content = parent.getContent();
        if (content == null) {
            return parent;
        }
        List<Content> updated = new ArrayList<>();
        for (Content c : content) {
            if (c == oldChild) {
                updated.add(newChild);
            } else {
                updated.add(c);
            }
        }
        return parent.withContent(updated);
    }

    public static class Signals {
        boolean hasDatasourceUrl;
    }

    private static String todoComment(String version) {
        return "TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management and whether version " +
               version + " is appropriate for your project. This dependency was previously managed by Spring Boot or the jeap-parent, which is why it is now in dependency management.";
    }

    private record DependencySpec(String groupId, String artifactId, String version, String comment) {
        String toXml() {
            String commentXml = comment == null ? "" : "<!-- " + comment + " -->";
            return commentXml + "<dependency><groupId>" + groupId + "</groupId><artifactId>" + artifactId +
                   "</artifactId><version>" + version + "</version></dependency>";
        }
    }
}
