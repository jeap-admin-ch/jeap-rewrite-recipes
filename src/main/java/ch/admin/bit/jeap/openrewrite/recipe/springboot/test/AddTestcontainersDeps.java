package ch.admin.bit.jeap.openrewrite.recipe.springboot.test;

import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Adds Testcontainers module dependencies based on imports found in Java sources.
 */
public class AddTestcontainersDeps extends ScanningRecipe<Map<String, Set<String>>> {

    private static final String GROUP_ID = "org.testcontainers";

    private static final Map<String, String> IMPORT_PREFIX_TO_ARTIFACT = Map.ofEntries(
            Map.entry("org.testcontainers.junit.jupiter.", "testcontainers-junit-jupiter"),
            Map.entry("org.testcontainers.containers.PostgreSQLContainer", "testcontainers-postgresql"),
            Map.entry("org.testcontainers.containers.MySQLContainer", "testcontainers-mysql"),
            Map.entry("org.testcontainers.containers.MariaDBContainer", "testcontainers-mariadb"),
            Map.entry("org.testcontainers.containers.MongoDBContainer", "testcontainers-mongodb"),
            Map.entry("org.testcontainers.containers.KafkaContainer", "testcontainers-kafka"),
            Map.entry("org.testcontainers.containers.Neo4jContainer", "testcontainers-neo4j"),
            Map.entry("org.testcontainers.elasticsearch.", "testcontainers-elasticsearch"),
            Map.entry("org.testcontainers.containers.localstack.", "testcontainers-localstack"),
            Map.entry("org.testcontainers.containers.MinIOContainer", "testcontainers-minio"),
            Map.entry("org.testcontainers.containers.MSSQLServerContainer", "testcontainers-mssqlserver"),
            Map.entry("org.testcontainers.containers.OracleContainer", "testcontainers-oracle-xe"),
            Map.entry("org.testcontainers.containers.RabbitMQContainer", "testcontainers-rabbitmq"),
            Map.entry("org.testcontainers.containers.RedisContainer", "testcontainers-redis"),
            Map.entry("org.testcontainers.containers.GenericContainer", ""),
            Map.entry("org.testcontainers.postgresql.", "testcontainers-postgresql"),
            Map.entry("org.testcontainers.mysql.", "testcontainers-mysql"),
            Map.entry("org.testcontainers.mariadb.", "testcontainers-mariadb"),
            Map.entry("org.testcontainers.mongodb.", "testcontainers-mongodb"),
            Map.entry("org.testcontainers.kafka.", "testcontainers-kafka"),
            Map.entry("org.testcontainers.neo4j.", "testcontainers-neo4j"),
            Map.entry("org.testcontainers.localstack.", "testcontainers-localstack"),
            Map.entry("org.testcontainers.minio.", "testcontainers-minio")
    );

    @Override
    public String getDisplayName() {
        return "Add Testcontainers module dependencies from imports";
    }

    @Override
    public String getDescription() {
        return "Detects org.testcontainers imports and adds corresponding test-scoped Maven dependencies " +
                "(including junit-jupiter and module artifacts such as postgresql, kafka, localstack and minio).";
    }

    @Override
    public Map<String, Set<String>> getInitialValue(ExecutionContext ctx) {
        return Collections.synchronizedMap(new HashMap<>());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Map<String, Set<String>> moduleToArtifacts) {
        return new JavaIsoVisitor<>() {

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);

                String sourcePath = getJavaTestSourcePathOrNull();
                if (sourcePath == null) {
                    return a;
                }

                if (a.getAnnotationType() == null) {
                    return a;
                }

                String annotationName = a.getAnnotationType().printTrimmed(getCursor());
                if ("Testcontainers".equals(annotationName) || annotationName.endsWith(".Testcontainers") ||
                        "Container".equals(annotationName) || annotationName.endsWith(".Container")) {
                    addArtifact(sourcePath, "testcontainers-junit-jupiter");
                }

                return a;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier i = super.visitIdentifier(identifier, ctx);

                if (getCursor().firstEnclosing(J.Import.class) != null) {
                    return i;
                }

                String sourcePath = getJavaTestSourcePathOrNull();
                if (sourcePath == null) {
                    return i;
                }

                String name = i.getSimpleName();
                for (Map.Entry<String, String> entry : IMPORT_PREFIX_TO_ARTIFACT.entrySet()) {
                    String prefix = entry.getKey();
                    if (prefix.endsWith("." + name) || prefix.equals(name)) {
                        String artifactId = entry.getValue();
                        if (artifactId != null && !artifactId.isEmpty()) {
                            addArtifact(sourcePath, artifactId);
                        }
                    }
                }

                return i;
            }

            private String getJavaTestSourcePathOrNull() {
                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                if (cu == null) {
                    return null;
                }

                String sourcePath = cu.getSourcePath().toString();
                if (!sourcePath.endsWith(".java")) {
                    return null;
                }

                if (!isTestPath(sourcePath)) {
                    return null;
                }

                return sourcePath;
            }

            private void addArtifact(String sourcePath, String artifactId) {
                String moduleRoot = extractModuleRoot(sourcePath);
                moduleToArtifacts.computeIfAbsent(
                                moduleRoot,
                                k -> Collections.synchronizedSet(new LinkedHashSet<>())
                        )
                        .add(artifactId);
            }

            private boolean isTestPath(String filePath) {
                return filePath.contains("src/test/") || filePath.contains("src\\test\\");
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Map<String, Set<String>> moduleToArtifacts) {
        if (moduleToArtifacts.isEmpty()) {
            return TreeVisitor.noop();
        }

        return new MavenIsoVisitor<>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                String pomPath = document.getSourcePath().toString();
                String pomModuleRoot = pomPath.contains("/")
                        ? pomPath.substring(0, pomPath.lastIndexOf('/'))
                        : "";

                Set<String> artifacts = moduleToArtifacts.get(pomModuleRoot);
                if (artifacts == null || artifacts.isEmpty()) {
                    return document;
                }

                Xml.Document out = document;
                for (String artifactId : artifacts) {
                    if (isDependencyPresent(out, artifactId)) {
                        continue;
                    }

                    out = (Xml.Document) new AddMavenDependencyVisitor(GROUP_ID, artifactId, "test")
                            .visitNonNull(out, ctx);
                }

                return out;
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
