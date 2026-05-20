package ch.admin.bit.jeap.openrewrite.recipe.kafka;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateKafka4DescribeTopicsTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        // No kafka-clients on classpath — simulates the migration scenario where
        // the code doesn't compile due to the removed all() method.
        spec.recipe(new MigrateKafka4DescribeTopics())
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesAllToAllTopicNamesInChain() {
        rewriteRun(java(
                """
                import org.apache.kafka.clients.admin.AdminClient;
                import org.apache.kafka.clients.admin.DescribeTopicsResult;
                import org.apache.kafka.clients.admin.TopicDescription;

                import java.util.List;
                import java.util.Map;

                class TopicConfig {
                    void checkTopics(AdminClient adminClient) throws Exception {
                        Map<String, TopicDescription> result =
                            adminClient.describeTopics(List.of("my-topic")).all().get();
                    }
                }
                """,
                """
                import org.apache.kafka.clients.admin.AdminClient;
                import org.apache.kafka.clients.admin.DescribeTopicsResult;
                import org.apache.kafka.clients.admin.TopicDescription;

                import java.util.List;
                import java.util.Map;

                class TopicConfig {
                    void checkTopics(AdminClient adminClient) throws Exception {
                        Map<String, TopicDescription> result =
                            adminClient.describeTopics(List.of("my-topic")).allTopicNames().get();
                    }
                }
                """
        ));
    }

    @Test
    void migratesAllOnLocalVariable() {
        rewriteRun(java(
                """
                import org.apache.kafka.clients.admin.AdminClient;
                import org.apache.kafka.clients.admin.DescribeTopicsResult;
                import org.apache.kafka.clients.admin.TopicDescription;

                import java.util.List;
                import java.util.Map;

                class TopicConfig {
                    void checkTopics(AdminClient adminClient) throws Exception {
                        DescribeTopicsResult describeResult = adminClient.describeTopics(List.of("my-topic"));
                        Map<String, TopicDescription> topics = describeResult.all().get();
                    }
                }
                """,
                """
                import org.apache.kafka.clients.admin.AdminClient;
                import org.apache.kafka.clients.admin.DescribeTopicsResult;
                import org.apache.kafka.clients.admin.TopicDescription;

                import java.util.List;
                import java.util.Map;

                class TopicConfig {
                    void checkTopics(AdminClient adminClient) throws Exception {
                        DescribeTopicsResult describeResult = adminClient.describeTopics(List.of("my-topic"));
                        Map<String, TopicDescription> topics = describeResult.allTopicNames().get();
                    }
                }
                """
        ));
    }

    @Test
    void migratesWhenOnlyAdminClientImported() {
        // Real-world case: DescribeTopicsResult is used implicitly via method chain
        // without an explicit import statement.
        rewriteRun(java(
                """
                import org.apache.kafka.clients.admin.AdminClient;
                import org.apache.kafka.clients.admin.NewTopic;

                import java.util.List;

                class TopicConfig {
                    void checkTopics(AdminClient adminClient) throws Exception {
                        adminClient.describeTopics(List.of("my-topic")).all().get();
                    }
                }
                """,
                """
                import org.apache.kafka.clients.admin.AdminClient;
                import org.apache.kafka.clients.admin.NewTopic;

                import java.util.List;

                class TopicConfig {
                    void checkTopics(AdminClient adminClient) throws Exception {
                        adminClient.describeTopics(List.of("my-topic")).allTopicNames().get();
                    }
                }
                """
        ));
    }

    @Test
    void noChangeWhenDescribeTopicsResultNotImported() {
        rewriteRun(java(
                """
                class Unrelated {
                    void doSomething() {
                    }
                }
                """
        ));
    }

    @Test
    void noChangeWhenAllHasArguments() {
        rewriteRun(java(
                """
                import org.apache.kafka.clients.admin.DescribeTopicsResult;

                class TopicConfig {
                    void checkTopics(DescribeTopicsResult result, Object arg) throws Exception {
                        result.all(arg);
                    }
                }
                """
        ));
    }
}
