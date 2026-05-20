package ch.admin.bit.jeap.openrewrite.recipe.boot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveImport;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

/**
 * Fallback migration for @MockBean/@SpyBean when type-attribution-based recipe does not trigger.
 */
public class FallbackMigrateMockBeanToMockitoBean extends Recipe {

    private static final String OLD_MOCK_BEAN_FQN = "org.springframework.boot.test.mock.mockito.MockBean";
    private static final String NEW_MOCKITO_BEAN_FQN = "org.springframework.test.context.bean.override.mockito.MockitoBean";
    private static final String OLD_SPY_BEAN_FQN = "org.springframework.boot.test.mock.mockito.SpyBean";
    private static final String NEW_MOCKITO_SPY_BEAN_FQN = "org.springframework.test.context.bean.override.mockito.MockitoSpyBean";

    @Override
    public String getDisplayName() {
        return "Fallback migrate @MockBean/@SpyBean to Mockito annotations";
    }

    @Override
    public String getDescription() {
        return "Performs text-based import/identifier migration from @MockBean/@SpyBean to @MockitoBean/@MockitoSpyBean when " +
               "the standard Spring Boot 4 recipe cannot resolve types.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> hasOldImport = new JavaIsoVisitor<>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                if (anImport.isStatic()) {
                    return anImport;
                }
                String fqn = anImport.getQualid().printTrimmed(getCursor());
                if (OLD_MOCK_BEAN_FQN.equals(fqn) || OLD_SPY_BEAN_FQN.equals(fqn)) {
                    return SearchResult.found(anImport);
                }
                return anImport;
            }
        };

        return Preconditions.check(hasOldImport, new JavaIsoVisitor<>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                if (anImport.isStatic()) {
                    return anImport;
                }
                String fqn = anImport.getQualid().printTrimmed(getCursor());
                if (OLD_MOCK_BEAN_FQN.equals(fqn)) {
                    maybeAddImport(NEW_MOCKITO_BEAN_FQN, null, false);
                    doAfterVisit(new RemoveImport<>(OLD_MOCK_BEAN_FQN, true));
                } else if (OLD_SPY_BEAN_FQN.equals(fqn)) {
                    maybeAddImport(NEW_MOCKITO_SPY_BEAN_FQN, null, false);
                    doAfterVisit(new RemoveImport<>(OLD_SPY_BEAN_FQN, true));
                }
                return anImport;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier ident, ExecutionContext ctx) {
                ident = super.visitIdentifier(ident, ctx);
                if ("MockBean".equals(ident.getSimpleName())) {
                    return ident.withSimpleName("MockitoBean");
                }
                if ("SpyBean".equals(ident.getSimpleName())) {
                    return ident.withSimpleName("MockitoSpyBean");
                }
                return ident;
            }
        });
    }
}
