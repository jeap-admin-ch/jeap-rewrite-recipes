package ch.admin.bit.jeap.openrewrite.recipe.jackson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import java.util.Collections;
import java.util.UUID;

/**
 * Jackson 3 (shipped with Spring Boot 4) changed the default behavior for mapping JSON {@code null}
 * to primitive types. In Jackson 2, a missing/null JSON field mapped to a primitive
 * {@code int}/{@code boolean}/etc. was silently coerced to {@code 0}/{@code false}.
 * In Jackson 3, this throws a {@code MismatchedInputException} by default.
 *
 * <p>This recipe fixes constructors annotated with
 * {@code @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)} by widening primitive
 * {@code @JsonProperty}-annotated parameters to their boxed equivalents:
 * <ul>
 *   <li>{@code int} → {@code Integer}</li>
 *   <li>{@code long} → {@code Long}</li>
 *   <li>{@code boolean} → {@code Boolean}</li>
 *   <li>{@code float} → {@code Float}</li>
 *   <li>{@code double} → {@code Double}</li>
 *   <li>{@code short} → {@code Short}</li>
 *   <li>{@code byte} → {@code Byte}</li>
 *   <li>{@code char} → {@code Character}</li>
 * </ul>
 *
 * <p><b>Note:</b> The constructor body is not modified. After applying this recipe, review
 * usages of the boxed parameters in the constructor body for potential {@link NullPointerException}
 * if the JSON value is absent.
 *
 * <p>Common pattern this fixes:
 * <pre>
 * // Before (Jackson 2 — worked silently)
 * {@literal @}JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
 * public CustomPageImpl(
 *     {@literal @}JsonProperty("number") int number,
 *     {@literal @}JsonProperty("size") int size) { ... }
 *
 * // After (Jackson 3 compatible)
 * {@literal @}JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
 * public CustomPageImpl(
 *     {@literal @}JsonProperty("number") Integer number,
 *     {@literal @}JsonProperty("size") Integer size) { ... }
 * </pre>
 */
public class MigrateJackson3JsonCreatorPrimitivesToBoxed extends Recipe {

    private static final String JSON_CREATOR_FQN = "com.fasterxml.jackson.annotation.JsonCreator";
    private static final String JACKSON_3_JSON_CREATOR_FQN = "tools.jackson.annotation.JsonCreator";
    private static final String JSON_PROPERTY_FQN = "com.fasterxml.jackson.annotation.JsonProperty";

    @Override
    public String getDisplayName() {
        return "Migrate Jackson 3 @JsonCreator primitive params to boxed types";
    }

    @Override
    public String getDescription() {
        return "Jackson 3 no longer silently maps JSON null to primitive types in @JsonCreator " +
               "constructors. This recipe widens @JsonProperty-annotated primitive parameters " +
               "to their boxed equivalents (int→Integer, boolean→Boolean, etc.) so that absent " +
               "JSON fields are represented as null rather than causing a MismatchedInputException.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // com.fasterxml.jackson.annotation.* did NOT move in Jackson 3, so this import
        // is present in both Jackson 2 and 3 code. We check for it to scope the recipe.
        // We also check for tools.jackson.annotation.JsonCreator in case it was already migrated.
        TreeVisitor<?, ExecutionContext> hasJsonCreatorImport = new JavaIsoVisitor<>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                if (!anImport.isStatic()) {
                    String fqn = anImport.getQualid().printTrimmed(getCursor());
                    if (JSON_CREATOR_FQN.equals(fqn) || JACKSON_3_JSON_CREATOR_FQN.equals(fqn)) {
                        return SearchResult.found(anImport);
                    }
                }
                return anImport;
            }
        };

        return Preconditions.check(hasJsonCreatorImport, new JavaIsoVisitor<>() {

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                                                                    ExecutionContext ctx) {
                multiVariable = super.visitVariableDeclarations(multiVariable, ctx);

                // Only process parameters with a primitive type
                if (!(multiVariable.getTypeExpression() instanceof J.Primitive primitiveType)) {
                    return multiVariable;
                }

                // Must have @JsonProperty annotation on the parameter
                if (!hasAnnotation(multiVariable, "JsonProperty")) {
                    return multiVariable;
                }

                // Must be inside a constructor annotated with @JsonCreator
                J.MethodDeclaration enclosingMethod =
                        getCursor().firstEnclosing(J.MethodDeclaration.class);
                if (enclosingMethod == null ||
                        !enclosingMethod.isConstructor() ||
                        !hasAnnotation(enclosingMethod, "JsonCreator")) {
                    return multiVariable;
                }

                String boxedName = getBoxedTypeName(primitiveType.getType());
                if (boxedName == null) {
                    return multiVariable;
                }

                // Replace the J.Primitive type expression with a J.Identifier for the boxed type.
                // All boxed types are in java.lang, so no import is needed.
                JavaType.FullyQualified boxedClassType =
                        JavaType.ShallowClass.build("java.lang." + boxedName);
                J.Identifier boxedTypeId = new J.Identifier(
                        UUID.randomUUID(),
                        primitiveType.getPrefix(),
                        Markers.EMPTY,
                        Collections.emptyList(),
                        boxedName,
                        boxedClassType,
                        null);

                return multiVariable.withTypeExpression(boxedTypeId);
            }

            private boolean hasAnnotation(J.VariableDeclarations variable, String simpleAnnotationName) {
                return variable.getLeadingAnnotations().stream()
                        .anyMatch(a -> matchesAnnotationName(a, simpleAnnotationName));
            }

            private boolean hasAnnotation(J.MethodDeclaration method, String simpleAnnotationName) {
                return method.getLeadingAnnotations().stream()
                        .anyMatch(a -> matchesAnnotationName(a, simpleAnnotationName));
            }

            private boolean matchesAnnotationName(J.Annotation annotation, String simpleAnnotationName) {
                if (annotation.getAnnotationType() instanceof J.Identifier id) {
                    return simpleAnnotationName.equals(id.getSimpleName());
                }
                if (annotation.getAnnotationType() instanceof J.FieldAccess fa) {
                    return simpleAnnotationName.equals(fa.getSimpleName());
                }
                return false;
            }

            private String getBoxedTypeName(JavaType.Primitive primitive) {
                return switch (primitive) {
                    case Int -> "Integer";
                    case Long -> "Long";
                    case Boolean -> "Boolean";
                    case Float -> "Float";
                    case Double -> "Double";
                    case Short -> "Short";
                    case Byte -> "Byte";
                    case Char -> "Character";
                    default -> null;
                };
            }
        });
    }
}
