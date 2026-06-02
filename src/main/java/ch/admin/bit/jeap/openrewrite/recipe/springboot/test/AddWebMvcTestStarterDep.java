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
 * Adds {@code spring-boot-starter-webmvc-test} as a test-scoped Maven dependency
 * to modules that use {@code @AutoConfigureMockMvc}.
 *
 * <p>Spring Boot 4 moved {@code AutoConfigureMockMvc} from:
 * {@code org.springframework.boot.test.autoconfigure.web.servlet}
 * to:
 * {@code org.springframework.boot.webmvc.test.autoconfigure}.
 */
public class AddWebMvcTestStarterDep extends ScanningRecipe<Set<String>> {

    private static final String GROUP_ID = "org.springframework.boot";
    private static final String STARTER_ARTIFACT_ID = "spring-boot-starter-webmvc-test";
    private static final String MODULE_ARTIFACT_ID = "spring-boot-webmvc-test";

    @Override
    public String getDisplayName() {
        return "Add spring-boot-starter-webmvc-test when @AutoConfigureMockMvc is used";
    }

    @Override
    public String getDescription() {
        return "Adds spring-boot-starter-webmvc-test (test scope) when @AutoConfigureMockMvc is used " +
               "in test sources (SB3 or SB4 package), using text-based detection.";
    }

    @Override
    public Set<String> getInitialValue(ExecutionContext ctx) {
        return Collections.synchronizedSet(new HashSet<>());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<String> modulesNeedingDep) {
        return new JavaIsoVisitor<>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);

                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                if (cu == null) {
                    return a;
                }

                String sourcePath = cu.getSourcePath().toString();
                if (!sourcePath.endsWith(".java") || !isTestPath(sourcePath)) {
                    return a;
                }

                if (a.getAnnotationType() == null) {
                    return a;
                }

                String annotationName = a.getAnnotationType().printTrimmed(getCursor());
                if (isAutoConfigureMockMvc(annotationName)) {
                    modulesNeedingDep.add(extractModuleRoot(sourcePath));
                }
                return a;
            }

            private boolean isTestPath(String filePath) {
                return filePath.contains("src/test/") || filePath.contains("src\\test\\");
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
                if (isDependencyPresent(document, STARTER_ARTIFACT_ID) || isDependencyPresent(document, MODULE_ARTIFACT_ID)) {
                    return document;
                }
                return (Xml.Document) new AddMavenDependencyVisitor(GROUP_ID, STARTER_ARTIFACT_ID, "test")
                        .visitNonNull(document, ctx);
            }

            private boolean isDependencyPresent(Xml.Document document, String artifactId) {
                for (Xml.Tag tag : document.getRoot().getChildren("dependencies")) {
                    if (tag.getChildren("dependency").stream().anyMatch(d ->
                            GROUP_ID.equals(d.getChildValue("groupId").map(String::trim).orElse(null)) &&
                            artifactId.equals(d.getChildValue("artifactId").map(String::trim).orElse(null)))) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    private static boolean isAutoConfigureMockMvc(String name) {
        return name.equals("AutoConfigureMockMvc") || name.endsWith(".AutoConfigureMockMvc");
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
