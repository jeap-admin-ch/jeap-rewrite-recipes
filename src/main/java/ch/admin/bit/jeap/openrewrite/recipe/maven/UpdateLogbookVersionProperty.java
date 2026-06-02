package ch.admin.bit.jeap.openrewrite.recipe.maven;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

/**
 * Updates existing {@code <logbook.version>} Maven properties to a fixed value.
 */
public class UpdateLogbookVersionProperty extends Recipe {

    private static final String PROPERTY_TAG = "logbook.version";
    private static final String TARGET_VERSION = "4.0.4";

    @Override
    public String getDisplayName() {
        return "Update Maven logbook.version property to 4.0.4";
    }

    @Override
    public String getDescription() {
        return "Updates existing <logbook.version> properties in pom.xml files to 4.0.4.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (!isPom() || !PROPERTY_TAG.equals(t.getName())) {
                    return t;
                }
                String current = t.getValue().map(String::trim).orElse("");
                if (TARGET_VERSION.equals(current)) {
                    return t;
                }
                return t.withValue(TARGET_VERSION);
            }

            private boolean isPom() {
                Xml.Document doc = getCursor().firstEnclosing(Xml.Document.class);
                return doc != null && doc.getSourcePath().toString().endsWith("pom.xml");
            }
        };
    }
}
