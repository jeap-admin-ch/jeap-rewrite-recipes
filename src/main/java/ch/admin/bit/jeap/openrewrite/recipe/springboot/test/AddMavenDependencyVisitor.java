package ch.admin.bit.jeap.openrewrite.recipe.springboot.test;

import org.openrewrite.ExecutionContext;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.List;

/**
 * Maven XML visitor that appends a dependency element to the {@code <dependencies>} section.
 * No version is added — the dep is expected to be managed by the parent BOM.
 */
public class AddMavenDependencyVisitor extends MavenIsoVisitor<ExecutionContext> {

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String scope;

    public AddMavenDependencyVisitor(String groupId, String artifactId, String scope) {
        this(groupId, artifactId, null, scope);
    }

    public AddMavenDependencyVisitor(String groupId, String artifactId, String version, String scope) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.scope = scope;
    }

    @Override
    public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
        Xml.Tag t = super.visitTag(tag, ctx);
        if (!"dependencies".equals(t.getName())) {
            return t;
        }
        // Only modify the top-level project <dependencies> (direct child of <project>)
        // Exclude: <dependencyManagement><dependencies>, <plugin><dependencies>, etc.
        if (!isDirectProjectDependencies()) {
            return t;
        }
        return appendDependency(t);
    }

    private boolean isDirectProjectDependencies() {
        // Walk up the cursor: the immediate parent should be the <project> tag
        Object parent = getCursor().getParentOrThrow().getValue();
        if (!(parent instanceof Xml.Tag parentTag)) {
            return false;
        }
        return "project".equals(parentTag.getName());
    }

    private Xml.Tag appendDependency(Xml.Tag dependenciesTag) {
        String indent = detectIndent(dependenciesTag);
        String depXml = "\n" + indent + "    <dependency>\n"
                + indent + "        <groupId>" + groupId + "</groupId>\n"
                + indent + "        <artifactId>" + artifactId + "</artifactId>\n"
                + (version != null ? indent + "        <version>" + version + "</version>\n" : "")
                + (scope != null ? indent + "        <scope>" + scope + "</scope>\n" : "")
                + indent + "    </dependency>";

        Xml.Tag newDep = Xml.Tag.build(depXml);

        List<Content> newContent = new ArrayList<>(dependenciesTag.getContent() != null
                                                   ? dependenciesTag.getContent()
                                                   : List.of());
        newContent.add(newDep);

        return dependenciesTag.withContent(newContent);
    }

    /** Detects indentation of the <dependencies> tag itself to use for children. */
    private String detectIndent(Xml.Tag tag) {
        String prefix = tag.getPrefix();
        if (prefix == null || prefix.isEmpty()) {
            return "    ";
        }
        // Take the last line of the prefix (after the last newline)
        int lastNl = prefix.lastIndexOf('\n');
        return lastNl >= 0 ? prefix.substring(lastNl + 1) : prefix;
    }
}
