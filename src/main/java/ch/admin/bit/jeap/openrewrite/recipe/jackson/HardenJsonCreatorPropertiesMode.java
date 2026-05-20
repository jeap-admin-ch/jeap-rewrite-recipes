package ch.admin.bit.jeap.openrewrite.recipe.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/**
 * Hardens ambiguous multi-parameter {@code @JsonCreator} constructors by forcing PROPERTIES mode
 * and adding missing {@code @JsonProperty("<paramName>")} annotations.
 */
public class HardenJsonCreatorPropertiesMode extends Recipe {

    private static final String JSON_CREATOR_FQN = "com.fasterxml.jackson.annotation.JsonCreator";
    private static final String JACKSON_3_JSON_CREATOR_FQN = "tools.jackson.annotation.JsonCreator";
    private static final String JSON_PROPERTY_FQN = "com.fasterxml.jackson.annotation.JsonProperty";
    private static final String JACKSON_3_JSON_PROPERTY_FQN = "tools.jackson.annotation.JsonProperty";
    private final @Nullable String classNamePattern;

    public HardenJsonCreatorPropertiesMode() {
        this(null);
    }

    @JsonCreator
    public HardenJsonCreatorPropertiesMode(
            @JsonProperty("classNamePattern")
            @Nullable
            @Option(displayName = "Class name regex",
                    description = "Optional regex. If set, only classes whose fully-qualified name matches are rewritten.",
                    example = ".*ProofOfPossession.*",
                    required = false)
            String classNamePattern
    ) {
        this.classNamePattern = classNamePattern == null || classNamePattern.isBlank() ? null : classNamePattern.trim();
    }

    @Override
    public String getDisplayName() {
        return "Harden @JsonCreator constructors for property-based deserialization";
    }

    @Override
    public String getDescription() {
        return "For multi-argument constructors annotated with @JsonCreator in DEFAULT/omitted mode, " +
               "sets mode=JsonCreator.Mode.PROPERTIES and adds missing @JsonProperty annotations to parameters.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> hasJsonCreator = new JavaIsoVisitor<>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                if (isJsonCreatorNoCursor(annotation)) {
                    return SearchResult.found(annotation);
                }
                return super.visitAnnotation(annotation, ctx);
            }
        };

        return Preconditions.check(hasJsonCreator, new JavaIsoVisitor<>() {

            private final JavaTemplate jsonCreatorPropertiesTemplate = JavaTemplate.builder(
                            "@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)")
                    .imports(JSON_CREATOR_FQN)
                    .build();

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                method = super.visitMethodDeclaration(method, ctx);
                if (!isTargetConstructor(method)) {
                    return method;
                }
                String ctorName = method.getSimpleName();
                int arity = method.getParameters().size();
                return SearchResult.found(method, "Hardened @JsonCreator constructor: " + ctorName + "(" + arity + " args)");
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                annotation = super.visitAnnotation(annotation, ctx);
                if (!isJsonCreator(annotation)) {
                    return annotation;
                }
                J.MethodDeclaration enclosingMethod = getCursor().firstEnclosing(J.MethodDeclaration.class);
                if (enclosingMethod == null || !isTargetConstructor(enclosingMethod)) {
                    return annotation;
                }
                maybeAddImport(JSON_CREATOR_FQN, null, false);
                return jsonCreatorPropertiesTemplate.apply(getCursor(), annotation.getCoordinates().replace());
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                multiVariable = super.visitVariableDeclarations(multiVariable, ctx);
                J.MethodDeclaration enclosingMethod = getCursor().firstEnclosing(J.MethodDeclaration.class);
                if (enclosingMethod == null || !isTargetConstructor(enclosingMethod)) {
                    return multiVariable;
                }
                if (!isMethodParameter(enclosingMethod, multiVariable) || hasJsonProperty(multiVariable)) {
                    return multiVariable;
                }
                if (multiVariable.getVariables().isEmpty()) {
                    return multiVariable;
                }
                String paramName = multiVariable.getVariables().get(0).getSimpleName();
                maybeAddImport(JSON_PROPERTY_FQN, null, false);
                JavaTemplate jsonPropertyTemplate = JavaTemplate.builder("@JsonProperty(\"" + paramName + "\")")
                        .imports(JSON_PROPERTY_FQN)
                        .build();
                return jsonPropertyTemplate.apply(
                        getCursor(),
                        multiVariable.getCoordinates().addAnnotation((a, b) -> 0)
                );
            }

            private boolean isTargetConstructor(J.MethodDeclaration method) {
                if (!method.isConstructor() || method.getParameters().size() < 2) {
                    return false;
                }
                if (!classMatches()) {
                    return false;
                }
                J.Annotation creator = findJsonCreatorAnnotation(method);
                if (creator == null) {
                    return false;
                }
                return hasDefaultOrOmittedMode(creator);
            }

            private boolean classMatches() {
                if (classNamePattern == null) {
                    return true;
                }
                J.ClassDeclaration cd = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (cd == null) {
                    return false;
                }
                String fqn = cd.getType() != null ? cd.getType().getFullyQualifiedName() : cd.getSimpleName();
                return fqn != null && fqn.matches(classNamePattern);
            }

            private boolean isMethodParameter(J.MethodDeclaration method, J.VariableDeclarations vd) {
                for (Statement p : method.getParameters()) {
                    if (p == vd) {
                        return true;
                    }
                }
                return false;
            }

            private J.Annotation findJsonCreatorAnnotation(J.MethodDeclaration method) {
                for (J.Annotation ann : method.getLeadingAnnotations()) {
                    if (isJsonCreator(ann)) {
                        return ann;
                    }
                }
                return null;
            }

            private boolean isJsonCreator(J.Annotation ann) {
                if (TypeUtils.isOfClassType(ann.getType(), JSON_CREATOR_FQN) ||
                    TypeUtils.isOfClassType(ann.getType(), JACKSON_3_JSON_CREATOR_FQN)) {
                    return true;
                }
                String name = ann.getAnnotationType().printTrimmed(getCursor());
                return "JsonCreator".equals(name) || JSON_CREATOR_FQN.equals(name) || JACKSON_3_JSON_CREATOR_FQN.equals(name);
            }

            private boolean hasJsonProperty(J.VariableDeclarations param) {
                return param.getLeadingAnnotations().stream().anyMatch(ann -> {
                    if (TypeUtils.isOfClassType(ann.getType(), JSON_PROPERTY_FQN) ||
                        TypeUtils.isOfClassType(ann.getType(), JACKSON_3_JSON_PROPERTY_FQN)) {
                        return true;
                    }
                    String name = ann.getAnnotationType().printTrimmed(getCursor());
                    return "JsonProperty".equals(name) || JSON_PROPERTY_FQN.equals(name) || JACKSON_3_JSON_PROPERTY_FQN.equals(name);
                });
            }

            private boolean hasDefaultOrOmittedMode(J.Annotation creator) {
                if (creator.getArguments() == null || creator.getArguments().isEmpty()) {
                    return true;
                }
                String printed = creator.printTrimmed(getCursor());
                if (printed.contains("Mode.PROPERTIES")) {
                    return false;
                }
                if (printed.contains("Mode.DELEGATING")) {
                    return false;
                }
                if (printed.contains("Mode.DEFAULT")) {
                    return true;
                }
                // Any other creator argument style (e.g. @JsonCreator("x")) is treated as ambiguous.
                return true;
            }
        });
    }

    private static boolean isJsonCreatorNoCursor(J.Annotation ann) {
        if (TypeUtils.isOfClassType(ann.getType(), JSON_CREATOR_FQN) ||
            TypeUtils.isOfClassType(ann.getType(), JACKSON_3_JSON_CREATOR_FQN)) {
            return true;
        }
        String name = ann.getAnnotationType().toString();
        return name.endsWith("JsonCreator") || JSON_CREATOR_FQN.equals(name) || JACKSON_3_JSON_CREATOR_FQN.equals(name);
    }
}
