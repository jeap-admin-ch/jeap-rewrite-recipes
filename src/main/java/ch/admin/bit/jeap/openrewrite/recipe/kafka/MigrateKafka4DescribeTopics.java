package ch.admin.bit.jeap.openrewrite.recipe.kafka;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/**
 * Migrates {@code DescribeTopicsResult.all()} to {@code allTopicNames()} as required by Kafka 4,
 * where the public no-arg {@code all()} method was removed.
 *
 * <p>Handles the common pattern:
 * <pre>
 *   // Before
 *   adminClient.describeTopics(topics).all().get();
 *
 *   // After
 *   adminClient.describeTopics(topics).allTopicNames().get();
 * </pre>
 */
public class MigrateKafka4DescribeTopics extends Recipe {

    private static final String DESCRIBE_TOPICS_RESULT_FQN =
            "org.apache.kafka.clients.admin.DescribeTopicsResult";
    // Precondition checks for any kafka-admin import since DescribeTopicsResult is often
    // used implicitly (chained) without an explicit import statement.
    private static final String KAFKA_ADMIN_PKG_PREFIX =
            "org.apache.kafka.clients.admin.";

    @Override
    public String getDisplayName() {
        return "Migrate Kafka 4 DescribeTopicsResult.all() to allTopicNames()";
    }

    @Override
    public String getDescription() {
        return "Kafka 4 removed the public no-arg DescribeTopicsResult.all() method. " +
               "Migrates .all() to .allTopicNames() on DescribeTopicsResult instances.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // Use text-based import check for any kafka-admin type — DescribeTopicsResult is often
        // used implicitly (via method chain) without an explicit import statement.
        TreeVisitor<?, ExecutionContext> hasKafkaAdminImport = new JavaIsoVisitor<>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                if (!anImport.isStatic()) {
                    String qualid = anImport.getQualid().printTrimmed(getCursor());
                    if (qualid.startsWith(KAFKA_ADMIN_PKG_PREFIX)) {
                        return SearchResult.found(anImport);
                    }
                }
                return anImport;
            }
        };

        return Preconditions.check(hasKafkaAdminImport, new JavaIsoVisitor<>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                method = super.visitMethodInvocation(method, ctx);

                if (!"all".equals(method.getSimpleName())) {
                    return method;
                }
                // Only no-arg all() calls
                if (!method.getArguments().isEmpty() &&
                        !(method.getArguments().size() == 1 && method.getArguments().get(0) instanceof J.Empty)) {
                    return method;
                }

                if (method.getSelect() != null) {
                    var selectType = method.getSelect().getType();
                    // When type is resolved: only rename if receiver is DescribeTopicsResult.
                    // When type is unresolved (null/Unknown): allow rename — the precondition already
                    // confirmed DescribeTopicsResult is imported, and no-arg all() is unique to that class.
                    if (selectType != null &&
                            !(selectType instanceof JavaType.Unknown) &&
                            !TypeUtils.isOfClassType(selectType, DESCRIBE_TOPICS_RESULT_FQN)) {
                        return method;
                    }
                }

                return method.withName(method.getName().withSimpleName("allTopicNames"));
            }
        });
    }
}
