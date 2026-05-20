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
 * Adds observability test dependencies when respective annotations are used.
 */
public class AddSpringBootObservabilityTestDeps extends ScanningRecipe<AddSpringBootObservabilityTestDeps.Accumulator> {

    private static final String GROUP_ID = "org.springframework.boot";
    private static final String METRICS_ARTIFACT_ID = "spring-boot-micrometer-metrics-test";
    private static final String TRACING_ARTIFACT_ID = "spring-boot-micrometer-tracing-test";
    private static final String METRICS_STARTER_ARTIFACT_ID = "spring-boot-starter-micrometer-metrics-test";
    private static final String TRACING_STARTER_ARTIFACT_ID = "spring-boot-starter-micrometer-tracing-test";

    private static final Set<String> METRICS_TYPES = Set.of(
            "org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability",
            "org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics",
            "org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics"
    );

    private static final Set<String> TRACING_TYPES = Set.of(
            "org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability",
            "org.springframework.boot.test.autoconfigure.actuate.tracing.AutoConfigureTracing",
            "org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing"
    );

    public static class Accumulator {
        Set<String> metricsModules = Collections.synchronizedSet(new HashSet<>());
        Set<String> tracingModules = Collections.synchronizedSet(new HashSet<>());
    }

    @Override
    public String getDisplayName() {
        return "Add Spring Boot observability test dependencies";
    }

    @Override
    public String getDescription() {
        return "Adds spring-boot-micrometer-metrics-test and spring-boot-micrometer-tracing-test when their annotations are used.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new JavaIsoVisitor<>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                String annotationName = a.getAnnotationType().printTrimmed(getCursor());
                String sourcePath = getCursor().firstEnclosingOrThrow(J.CompilationUnit.class).getSourcePath().toString();

                if (!isTestPath(sourcePath)) {
                    return a;
                }

                if (isAnnotationMatch(annotationName, METRICS_TYPES)) {
                    acc.metricsModules.add(getModuleRoot());
                }
                if (isAnnotationMatch(annotationName, TRACING_TYPES)) {
                    acc.tracingModules.add(getModuleRoot());
                }
                return a;
            }

            private boolean isTestPath(String filePath) {
                return filePath.contains("src/test/") || filePath.contains("src\\test\\");
            }

            private boolean isAnnotationMatch(String name, Set<String> types) {
                return types.contains(name) || types.stream().anyMatch(t -> t.endsWith("." + name));
            }

            private String getModuleRoot() {
                return extractModuleRoot(getCursor().firstEnclosingOrThrow(J.CompilationUnit.class)
                        .getSourcePath().toString());
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new MavenIsoVisitor<>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                String pomPath = document.getSourcePath().toString();
                String pomModuleRoot = pomPath.contains("/") ? pomPath.substring(0, pomPath.lastIndexOf('/')) : "";

                Xml.Document doc = document;
                if (acc.metricsModules.contains(pomModuleRoot) && !isMetricsDependencyPresent(doc)) {
                    doc = (Xml.Document) new AddMavenDependencyVisitor(GROUP_ID, METRICS_ARTIFACT_ID, "test")
                            .visitNonNull(doc, ctx);
                }
                if (acc.tracingModules.contains(pomModuleRoot) && !isTracingDependencyPresent(doc)) {
                    doc = (Xml.Document) new AddMavenDependencyVisitor(GROUP_ID, TRACING_ARTIFACT_ID, "test")
                            .visitNonNull(doc, ctx);
                }
                return doc;
            }

            private boolean isMetricsDependencyPresent(Xml.Document document) {
                return isDependencyPresent(document, METRICS_ARTIFACT_ID) || isDependencyPresent(document, METRICS_STARTER_ARTIFACT_ID);
            }

            private boolean isTracingDependencyPresent(Xml.Document document) {
                return isDependencyPresent(document, TRACING_ARTIFACT_ID) || isDependencyPresent(document, TRACING_STARTER_ARTIFACT_ID);
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
