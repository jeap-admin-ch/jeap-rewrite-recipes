package ch.admin.bit.jeap.openrewrite.recipe.springboot;

import ch.admin.bit.jeap.openrewrite.recipe.springboot.test.AddMavenDependencyVisitor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AddSpringBootStarterCache extends ScanningRecipe<Map<String, Boolean>> {

    private static final String ENABLE_CACHING_FQN = "org.springframework.cache.annotation.EnableCaching";
    private static final String ENABLE_CACHING_SIMPLE_NAME = "EnableCaching";
    private static final String DATA_JPA_TEST_FQN = "org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest";
    private static final String AUTO_CONFIGURE_CACHE_FQN = "org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache";
    private static final String AUTO_CONFIGURE_CACHE_ANNOTATION = "@AutoConfigureCache";
    private static final String GROUP_ID = "org.springframework.boot";
    private static final String ARTIFACT_ID = "spring-boot-starter-cache";
    private static final String TEST_ARTIFACT_ID = "spring-boot-cache-test";

    @Override
    public String getDisplayName() {
        return "Add `spring-boot-starter-cache` dependency";
    }

    @Override
    public String getDescription() {
        return "Adds `spring-boot-starter-cache` and `spring-boot-cache-test` to the `pom.xml` if `@EnableCaching` is used and the dependencies are missing.";
    }

    @Override
    public Map<String, Boolean> getInitialValue(ExecutionContext ctx) {
        return new ConcurrentHashMap<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Map<String, Boolean> moduleNeedsCache) {
        return new JavaIsoVisitor<>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                boolean isEnableCaching = (a.getType() != null && ENABLE_CACHING_FQN.equals(a.getType().toString())) ||
                        ENABLE_CACHING_SIMPLE_NAME.equals(a.getSimpleName());
                if (isEnableCaching) {
                    String sourcePath = getCursor().firstEnclosingOrThrow(J.CompilationUnit.class).getSourcePath().toString();
                    String moduleRoot = extractModuleRoot(sourcePath);
                    moduleNeedsCache.put(moduleRoot, true);
                }
                return a;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Map<String, Boolean> moduleNeedsCache) {
        return new TreeVisitor<>() {
            @Override
            public org.openrewrite.Tree visit(org.openrewrite.Tree tree, ExecutionContext ctx) {
                if (tree instanceof Xml.Document) {
                    Xml.Document doc = (Xml.Document) tree;
                    return new MavenVisitor(moduleNeedsCache).visitNonNull(doc, ctx);
                } else if (tree instanceof J.CompilationUnit) {
                    J.CompilationUnit cu = (J.CompilationUnit) tree;
                    return new JavaTestVisitor(moduleNeedsCache).visitNonNull(cu, ctx);
                }
                return tree;
            }
        };
    }

    private static class MavenVisitor extends MavenIsoVisitor<ExecutionContext> {
        private final Map<String, Boolean> moduleNeedsCache;

        MavenVisitor(Map<String, Boolean> moduleNeedsCache) {
            this.moduleNeedsCache = moduleNeedsCache;
        }

        @Override
        public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
            String pomPath = document.getSourcePath().toString();
            String pomModuleRoot = extractModuleRoot(pomPath);

            if (Boolean.TRUE.equals(moduleNeedsCache.get(pomModuleRoot))) {
                if (!isDependencyPresent(document, ARTIFACT_ID)) {
                    document = (Xml.Document) new AddMavenDependencyVisitor(GROUP_ID, ARTIFACT_ID, null)
                            .visitNonNull(document, ctx);
                }
                if (!isDependencyPresent(document, TEST_ARTIFACT_ID)) {
                    document = (Xml.Document) new AddMavenDependencyVisitor(GROUP_ID, TEST_ARTIFACT_ID, "test")
                            .visitNonNull(document, ctx);
                }
            }

            return document;
        }

        private boolean isDependencyPresent(Xml.Document document, String artifactId) {
            for (Xml.Tag tag : document.getRoot().getChildren("dependencies")) {
                if (tag.getChildren("dependency").stream().anyMatch(d ->
                        GROUP_ID.equals(d.getChildValue("groupId").map(String::trim).orElse(null)) &&
                                artifactId.equals(d.getChildValue("artifactId").map(String::trim).orElse(null)))) {
                    return true;
                }
            }
            for (Xml.Tag tag : document.getRoot().getChildren("dependencyManagement")) {
                for (Xml.Tag deps : tag.getChildren("dependencies")) {
                    if (deps.getChildren("dependency").stream().anyMatch(d ->
                            GROUP_ID.equals(d.getChildValue("groupId").map(String::trim).orElse(null)) &&
                                    artifactId.equals(d.getChildValue("artifactId").map(String::trim).orElse(null)))) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static class JavaTestVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final Map<String, Boolean> moduleNeedsCache;
        private final AnnotationMatcher dataJpaTestMatcher = new AnnotationMatcher("@" + DATA_JPA_TEST_FQN);
        private final AnnotationMatcher autoConfigureCacheMatcher = new AnnotationMatcher("@" + AUTO_CONFIGURE_CACHE_FQN);

        JavaTestVisitor(Map<String, Boolean> moduleNeedsCache) {
            this.moduleNeedsCache = moduleNeedsCache;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            String sourcePath = getCursor().firstEnclosingOrThrow(J.CompilationUnit.class).getSourcePath().toString();
            String moduleRoot = extractModuleRoot(sourcePath);

            if (Boolean.TRUE.equals(moduleNeedsCache.get(moduleRoot))) {
                boolean hasDataJpaTest = cd.getLeadingAnnotations().stream().anyMatch(dataJpaTestMatcher::matches);
                if (!hasDataJpaTest) {
                    hasDataJpaTest = cd.getLeadingAnnotations().stream()
                            .anyMatch(a -> "DataJpaTest".equals(a.getSimpleName()));
                }
                boolean hasAutoConfigureCache = cd.getLeadingAnnotations().stream().anyMatch(a ->
                        (a.getType() != null && AUTO_CONFIGURE_CACHE_FQN.equals(a.getType().toString())) ||
                        "AutoConfigureCache".equals(a.getSimpleName()));

                if (hasDataJpaTest && !hasAutoConfigureCache) {
                    maybeAddImport(AUTO_CONFIGURE_CACHE_FQN);
                    cd = JavaTemplate.builder(AUTO_CONFIGURE_CACHE_ANNOTATION)
                            .javaParser(org.openrewrite.java.JavaParser.fromJavaVersion()
                                    .dependsOn("package org.springframework.boot.cache.test.autoconfigure; public @interface AutoConfigureCache {}"))
                            .imports(AUTO_CONFIGURE_CACHE_FQN)
                            .build()
                            .apply(getCursor(), cd.getCoordinates().addAnnotation((a, b) -> 0));
                }
            }
            return cd;
        }
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
