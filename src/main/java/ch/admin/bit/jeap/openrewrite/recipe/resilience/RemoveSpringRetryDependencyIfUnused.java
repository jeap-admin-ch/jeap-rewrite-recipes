package ch.admin.bit.jeap.openrewrite.recipe.resilience;

import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.maven.RemoveDependency;

import java.util.concurrent.atomic.AtomicBoolean;

public class RemoveSpringRetryDependencyIfUnused extends ScanningRecipe<AtomicBoolean> {

    private static final String SPRING_RETRY_PACKAGE_PREFIX = "org.springframework.retry.";

    @Override
    public String getDisplayName() {
        return "Remove unused Spring Retry dependency";
    }

    @Override
    public String getDescription() {
        return "Removes Spring Retry after all usages have migrated to Spring Framework 7 resilience.";
    }

    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean springRetryUsed) {
        return new JavaIsoVisitor<>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import imported = super.visitImport(anImport, ctx);
                if (!imported.isStatic() && imported.getQualid().printTrimmed(getCursor())
                        .startsWith(SPRING_RETRY_PACKAGE_PREFIX)) {
                    springRetryUsed.set(true);
                }
                return imported;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean springRetryUsed) {
        if (springRetryUsed.get()) {
            return TreeVisitor.noop();
        }
        return new RemoveDependency("org.springframework.retry", "spring-retry", null).getVisitor();
    }
}
