package ch.admin.bit.jeap.openrewrite.recipe.springboot.test;

import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Adds {@code spring-boot-flyway} when FlywayProperties is imported.
 */
public class AddSpringBootFlywayModuleDep extends ScanningRecipe<Set<String>> {

    private static final String SB3_FLYWAY_PROPERTIES = "org.springframework.boot.autoconfigure.flyway.FlywayProperties";
    private static final String SB4_FLYWAY_PROPERTIES = "org.springframework.boot.flyway.autoconfigure.FlywayProperties";

    private static final String GROUP_ID = "org.springframework.boot";
    private static final String ARTIFACT_ID = "spring-boot-flyway";

    @Override
    public String getDisplayName() {
        return "Add spring-boot-flyway when FlywayProperties is used";
    }

    @Override
    public String getDescription() {
        return "Spring Boot 4 split Flyway support into spring-boot-flyway. " +
               "This recipe adds that dependency to Maven modules importing FlywayProperties.";
    }

    @Override
    public Set<String> getInitialValue(ExecutionContext ctx) {
        return Collections.synchronizedSet(new HashSet<>());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<String> modulesNeedingDep) {
        return new JavaIsoVisitor<>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier i = super.visitIdentifier(identifier, ctx);
                if (getCursor().firstEnclosing(J.Import.class) != null) {
                    return i;
                }
                if ("FlywayProperties".equals(i.getSimpleName())) {
                    String sourcePath = getCursor().firstEnclosingOrThrow(J.CompilationUnit.class)
                                                   .getSourcePath().toString();
                    modulesNeedingDep.add(extractModuleRoot(sourcePath));
                }
                return i;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<String> modulesNeedingDep) {
        if (modulesNeedingDep.isEmpty()) {
            return TreeVisitor.noop();
        }
        return new MavenIsoVisitor<>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                String pomPath = document.getSourcePath().toString();
                String pomModuleRoot = pomPath.contains("/") ? pomPath.substring(0, pomPath.lastIndexOf('/')) : "";

                if (!modulesNeedingDep.contains(pomModuleRoot)) {
                    return document;
                }
                if (isDependencyPresent(document)) {
                    return document;
                }
                return (Xml.Document) new AddMavenDependencyVisitor(GROUP_ID, ARTIFACT_ID, null)
                        .visitNonNull(document, ctx);
            }
        };
    }

    private static boolean isDependencyPresent(Xml.Document document) {
        for (Xml.Tag tag : document.getRoot().getChildren("dependencies")) {
            if (tag.getChildren("dependency").stream().anyMatch(d ->
                    GROUP_ID.equals(d.getChildValue("groupId").map(String::trim).orElse(null)) &&
                    ARTIFACT_ID.equals(d.getChildValue("artifactId").map(String::trim).orElse(null)))) {
                return true;
            }
        }
        return false;
    }

    private static String extractModuleRoot(String filePath) {
        int srcIndex = filePath.indexOf("/src/");
        if (srcIndex < 0) {
            srcIndex = filePath.indexOf("\\src\\");
        }
        if (srcIndex <= 0) {
            return "";
        }
        return filePath.substring(0, srcIndex);
    }
}
