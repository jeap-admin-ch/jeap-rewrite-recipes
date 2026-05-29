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
 * Adds {@code spring-boot-starter-data-jpa-test} as a test-scoped Maven dependency
 * to any Maven module that uses the Spring Boot 4 modular test annotations
 * ({@code @DataJpaTest} or {@code @AutoConfigureTestDatabase}).
 *
 * <p>Spring Boot 4 modularised its test auto-configuration into separate jars.
 * The upstream {@code MigrateToModularStarters} recipe adds the dependency using
 * {@code onlyIfUsing} with the old SB3 package, which relies on type resolution.
 * After the project's parent has been upgraded to Spring Boot 4 the old SB3
 * types are gone from the classpath, so the type-resolution check fails silently
 * and the dependency is never added.
 *
 * <p>This recipe performs <em>text-based</em> import scanning, which is immune to
 * the missing-classpath problem.  It detects both:
 * <ul>
 *   <li>the old SB3 package {@code org.springframework.boot.test.autoconfigure.orm.jpa.*}</li>
 *   <li>the new SB4 package {@code org.springframework.boot.data.jpa.test.autoconfigure.*}</li>
 * </ul>
 *
 * <p>The recipe also handles {@code @AutoConfigureTestDatabase} (moved to
 * {@code org.springframework.boot.jdbc.test.autoconfigure}) because
 * {@code spring-boot-starter-data-jpa-test} transitively brings in
 * {@code spring-boot-starter-jdbc-test}, which contains that class.
 */
public class AddDataJpaTestStarterDep extends ScanningRecipe<Set<String>> {

    // Old SB3 packages (detected when migration hasn't run yet, or in the first cycle)
    private static final String SB3_ORM_JPA_PREFIX = "org.springframework.boot.test.autoconfigure.orm.jpa.";
    private static final String SB3_JDBC_PREFIX = "org.springframework.boot.test.autoconfigure.jdbc.";
    // New SB4 packages (detected after MigrateToModularStarters has run)
    private static final String SB4_DATA_JPA_PREFIX = "org.springframework.boot.data.jpa.test.autoconfigure.";
    private static final String SB4_JDBC_PREFIX = "org.springframework.boot.jdbc.test.autoconfigure.";

    private static final String GROUP_ID = "org.springframework.boot";
    private static final String ARTIFACT_ID = "spring-boot-starter-data-jpa-test";

    @Override
    public String getDisplayName() {
        return "Add spring-boot-starter-data-jpa-test when @DataJpaTest or @AutoConfigureTestDatabase is used";
    }

    @Override
    public String getDescription() {
        return "Spring Boot 4 splits test auto-configuration into separate modular jars. " +
               "This recipe adds spring-boot-starter-data-jpa-test (test scope) to any Maven module " +
               "that uses @DataJpaTest or @AutoConfigureTestDatabase (SB3 or SB4 packages), " +
               "using text-based import detection so it works even when the type is not on the classpath.";
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
                if (!sourcePath.endsWith(".java")) {
                    return a;
                }

                if (!isTestPath(sourcePath)) {
                    return a;
                }

                if (a.getAnnotationType() == null) {
                    return a;
                }

                String annotationName = a.getAnnotationType().printTrimmed(getCursor());
                if (isDataJpaTestAnnotation(annotationName)) {
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
                // Check if the dep is already present to avoid duplicates
                if (isDependencyPresent(document)) {
                    return document;
                }
                return addDependency(document, ctx);
            }

            private boolean isDependencyPresent(Xml.Document document) {
                for (Xml.Tag tag : document.getRoot().getChildren("dependencies")) {
                    if (tag.getChildren("dependency").stream().anyMatch(d ->
                            GROUP_ID.equals(d.getChildValue("groupId").map(String::trim).orElse(null)) &&
                            ARTIFACT_ID.equals(d.getChildValue("artifactId").map(String::trim).orElse(null)))) {
                        return true;
                    }
                }
                return false;
            }

            private Xml.Document addDependency(Xml.Document document, ExecutionContext ctx) {
                return (Xml.Document) new AddMavenDependencyVisitor(GROUP_ID, ARTIFACT_ID, "test")
                        .visitNonNull(document, ctx);
            }
        };
    }

    private static boolean isDataJpaTestAnnotation(String name) {
        return name.equals("DataJpaTest") || name.endsWith(".DataJpaTest") ||
               name.equals("AutoConfigureTestDatabase") || name.endsWith(".AutoConfigureTestDatabase");
    }

    /**
     * Extracts the Maven module root from a Java source file path.
     * Examples:
     *   "src/test/java/.../ITPersistenceBase.java"                   → ""
     *   "my-module/src/test/java/.../ITPersistenceBase.java"          → "my-module"
     */
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
