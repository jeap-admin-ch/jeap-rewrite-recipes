package ch.admin.bit.jeap.openrewrite.recipe.flyway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.Markers;
import org.openrewrite.text.PlainText;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates an additive Flyway migration when runtime outbox schema requirements are missing from migration history.
 */
public class AddMissingOutboxColumnMigration extends ScanningRecipe<AddMissingOutboxColumnMigration.Accumulator> {

    private static final Pattern FLYWAY_SQL_FILE = Pattern.compile("^V(.+)__([^.]+)\\.sql$");

    private final String tableName;
    private final String columnName;
    private final String sqlType;
    private final String defaultExpression;
    private final String migrationDescriptionSuffix;
    private final String dependencyMatchers;
    private final String migrationRoots;

    public AddMissingOutboxColumnMigration() {
        this(null, null, null, null, null, null, null);
    }

    @JsonCreator
    public AddMissingOutboxColumnMigration(
            @JsonProperty("tableName")
            @Nullable
            @Option(displayName = "Table name",
                    description = "Target table name.",
                    example = "deferred_message",
                    required = false)
            String tableName,
            @JsonProperty("columnName")
            @Nullable
            @Option(displayName = "Column name",
                    description = "Missing column to add.",
                    example = "sampled",
                    required = false)
            String columnName,
            @JsonProperty("sqlType")
            @Nullable
            @Option(displayName = "SQL type",
                    description = "Column SQL type.",
                    example = "BOOLEAN",
                    required = false)
            String sqlType,
            @JsonProperty("defaultExpression")
            @Nullable
            @Option(displayName = "Default expression",
                    description = "Default SQL expression (without DEFAULT keyword).",
                    example = "FALSE",
                    required = false)
            String defaultExpression,
            @JsonProperty("migrationDescriptionSuffix")
            @Nullable
            @Option(displayName = "Migration description suffix",
                    description = "Suffix after '__' in the generated Flyway filename.",
                    example = "add_deferred_message_sampled_column",
                    required = false)
            String migrationDescriptionSuffix,
            @JsonProperty("dependencyMatchers")
            @Nullable
            @Option(displayName = "Dependency matchers",
                    description = "Comma-separated dependency matchers in groupId:artifactId form. '*' wildcard supported.",
                    example = "ch.admin.bit.jeap:jeap-messaging-api,org.springframework.integration:spring-integration-jdbc",
                    required = false)
            String dependencyMatchers,
            @JsonProperty("migrationRoots")
            @Nullable
            @Option(displayName = "Migration roots",
                    description = "Comma-separated Flyway migration roots, relative to each Maven module root.",
                    example = "src/main/resources/db/migration,src/main/resources/db/outbox/migration",
                    required = false)
            String migrationRoots
    ) {
        this.tableName = blankToDefault(tableName, "deferred_message");
        this.columnName = blankToDefault(columnName, "sampled");
        this.sqlType = blankToDefault(sqlType, "BOOLEAN");
        this.defaultExpression = blankToDefault(defaultExpression, "FALSE");
        this.migrationDescriptionSuffix = blankToDefault(migrationDescriptionSuffix, "add_deferred_message_sampled_column");
        this.dependencyMatchers = blankToDefault(dependencyMatchers,
                "ch.admin.bit.jeap:*outbox*,ch.admin.bit.jeap:*db-migration*,ch.admin.bit.jeap:jeap-messaging-*,org.springframework.integration:spring-integration-jdbc,org.flywaydb:flyway-core");
        this.migrationRoots = blankToDefault(migrationRoots, "src/main/resources/db/migration");
    }

    @Override
    public String getDisplayName() {
        return "Add missing Flyway migration for outbox table column";
    }

    @Override
    public String getDescription() {
        return "Detects missing outbox column migrations and generates a safe additive Flyway migration with " +
               "ALTER TABLE .. ADD COLUMN IF NOT EXISTS ..";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<>() {
            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                Tree visited = super.visit(tree, ctx);
                if (!(visited instanceof SourceFile sf)) {
                    return visited;
                }
                String sourcePath = normalizePath(sf.getSourcePath());
                String moduleRoot = extractModuleRoot(sourcePath);
                ModuleData module = acc.modules.computeIfAbsent(moduleRoot, ignored -> new ModuleData(moduleRoot));

                if (sourcePath.endsWith("/pom.xml") || "pom.xml".equals(sourcePath)) {
                    String pom = sf.printAll(getCursor());
                    if (matchesAnyDependency(pom)) {
                        module.dependencyMatched = true;
                    }
                    module.pomPaths.add(sourcePath);
                }

                List<String> roots = parseCsv(migrationRoots);
                for (String root : roots) {
                    String rootPrefix = moduleRoot.isEmpty() ? root + "/" : moduleRoot + "/" + root + "/";
                    if (sourcePath.startsWith(rootPrefix) && sourcePath.endsWith(".sql")) {
                        RootData rd = module.roots.computeIfAbsent(root, ignored -> new RootData(root));
                        String sql = sf.printAll(getCursor());
                        String filename = sourcePath.substring(sourcePath.lastIndexOf('/') + 1);
                        rd.files.put(filename, sql);
                        if (containsCreateTable(sql, tableName)) {
                            rd.tableCreated = true;
                        }
                        if (containsColumnDefinition(sql, tableName, columnName) || containsAddColumn(sql, tableName, columnName)) {
                            rd.columnAlreadyPresent = true;
                        }
                    }
                }

                return visited;
            }
        };
    }

    @Override
    public Collection<SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        List<SourceFile> generated = new ArrayList<>();
        for (ModuleData module : acc.modules.values()) {
            if (!module.dependencyMatched) {
                continue;
            }
            for (RootData root : module.roots.values()) {
                if (!root.tableCreated || root.columnAlreadyPresent || root.files.isEmpty()) {
                    continue;
                }
                String nextVersion = nextVersion(root.files.keySet());
                if (nextVersion == null) {
                    continue;
                }
                String filename = "V" + nextVersion + "__" + migrationDescriptionSuffix + ".sql";
                if (root.files.containsKey(filename)) {
                    continue;
                }
                Path target = module.moduleRoot.isEmpty()
                        ? Path.of(root.root, filename)
                        : Path.of(module.moduleRoot, root.root, filename);
                String sql = "ALTER TABLE " + tableName + " ADD COLUMN IF NOT EXISTS " + columnName + " " +
                             sqlType + " NOT NULL DEFAULT " + defaultExpression + ";\n";
                generated.add(PlainText.builder()
                        .id(Tree.randomId())
                        .sourcePath(target)
                        .markers(Markers.EMPTY)
                        .text(sql)
                        .build());
            }
        }
        return generated;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return TreeVisitor.noop();
    }

    private boolean matchesAnyDependency(String pomXml) {
        for (String matcher : parseCsv(dependencyMatchers)) {
            String[] parts = matcher.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            if (containsDependency(pomXml, parts[0], parts[1])) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsDependency(String pomXml, String groupIdPattern, String artifactPattern) {
        Pattern dep = Pattern.compile("<dependency>.*?<groupId>([^<]+)</groupId>.*?<artifactId>([^<]+)</artifactId>.*?</dependency>",
                Pattern.DOTALL);
        Matcher m = dep.matcher(pomXml);
        while (m.find()) {
            String g = m.group(1).trim();
            String a = m.group(2).trim();
            if (wildcardMatch(groupIdPattern, g) && wildcardMatch(artifactPattern, a)) {
                return true;
            }
        }
        return false;
    }

    private static boolean wildcardMatch(String pattern, String value) {
        String regex = pattern.replace(".", "\\.").replace("*", ".*");
        return value.matches(regex);
    }

    private static boolean containsCreateTable(String sql, String table) {
        String pattern = "(?is)\\bcreate\\s+table\\s+(if\\s+not\\s+exists\\s+)?[`\\\"]?" +
                         Pattern.quote(table) + "[`\\\"]?\\b";
        return Pattern.compile(pattern).matcher(sql).find();
    }

    private static boolean containsAddColumn(String sql, String table, String column) {
        String pattern = "(?is)\\balter\\s+table\\s+[`\\\"]?" + Pattern.quote(table) + "[`\\\"]?\\s+" +
                         "add\\s+column\\s+(if\\s+not\\s+exists\\s+)?[`\\\"]?" + Pattern.quote(column) + "[`\\\"]?\\b";
        return Pattern.compile(pattern).matcher(sql).find();
    }

    private static boolean containsColumnDefinition(String sql, String table, String column) {
        Matcher m = Pattern.compile("(?is)\\bcreate\\s+table\\s+(if\\s+not\\s+exists\\s+)?[`\\\"]?" +
                                    Pattern.quote(table) + "[`\\\"]?\\s*\\((.*?)\\)")
                           .matcher(sql);
        while (m.find()) {
            String defs = m.group(2);
            if (Pattern.compile("(?is)(^|,|\\s)[`\\\"]?" + Pattern.quote(column) + "[`\\\"]?\\s+").matcher(defs).find()) {
                return true;
            }
        }
        return false;
    }

    private static String nextVersion(Set<String> filenames) {
        List<VersionTokens> versions = new ArrayList<>();
        for (String filename : filenames) {
            Matcher m = FLYWAY_SQL_FILE.matcher(filename);
            if (!m.matches()) {
                continue;
            }
            VersionTokens parsed = parseVersion(m.group(1));
            if (parsed != null) {
                versions.add(parsed);
            }
        }
        if (versions.isEmpty()) {
            return null;
        }
        VersionTokens max = versions.stream().max(Comparator.naturalOrder()).orElse(null);
        if (max == null) {
            return null;
        }
        return max.incrementLast();
    }

    private static @Nullable VersionTokens parseVersion(String raw) {
        String[] tokens = raw.split("_");
        int[] values = new int[tokens.length];
        int[] widths = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            if (!tokens[i].chars().allMatch(Character::isDigit)) {
                return null;
            }
            values[i] = Integer.parseInt(tokens[i]);
            widths[i] = tokens[i].length();
        }
        return new VersionTokens(values, widths);
    }

    private static String extractModuleRoot(String normalizedPath) {
        int srcIndex = normalizedPath.indexOf("/src/");
        if (srcIndex > 0) {
            return normalizedPath.substring(0, srcIndex);
        }
        if ("pom.xml".equals(normalizedPath)) {
            return "";
        }
        if (normalizedPath.endsWith("/pom.xml")) {
            return normalizedPath.substring(0, normalizedPath.length() - "/pom.xml".length());
        }
        return "";
    }

    private static String normalizePath(Path sourcePath) {
        return sourcePath.toString().replace('\\', '/');
    }

    private static List<String> parseCsv(String csv) {
        List<String> values = new ArrayList<>();
        for (String raw : csv.split(",")) {
            String t = raw.trim();
            if (!t.isEmpty()) {
                values.add(t);
            }
        }
        return values;
    }

    private static String blankToDefault(@Nullable String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    static class Accumulator {
        final Map<String, ModuleData> modules = new LinkedHashMap<>();
    }

    static class ModuleData {
        final String moduleRoot;
        boolean dependencyMatched;
        final Set<String> pomPaths = new LinkedHashSet<>();
        final Map<String, RootData> roots = new LinkedHashMap<>();

        ModuleData(String moduleRoot) {
            this.moduleRoot = moduleRoot;
        }
    }

    static class RootData {
        final String root;
        final Map<String, String> files = new HashMap<>();
        boolean tableCreated;
        boolean columnAlreadyPresent;

        RootData(String root) {
            this.root = root;
        }
    }

    static class VersionTokens implements Comparable<VersionTokens> {
        final int[] values;
        final int[] widths;

        VersionTokens(int[] values, int[] widths) {
            this.values = values;
            this.widths = widths;
        }

        @Override
        public int compareTo(VersionTokens o) {
            int max = Math.max(values.length, o.values.length);
            for (int i = 0; i < max; i++) {
                int a = i < values.length ? values[i] : 0;
                int b = i < o.values.length ? o.values[i] : 0;
                if (a != b) {
                    return Integer.compare(a, b);
                }
            }
            return Integer.compare(values.length, o.values.length);
        }

        String incrementLast() {
            int[] out = values.clone();
            out[out.length - 1] = out[out.length - 1] + 1;
            List<String> tokens = new ArrayList<>(out.length);
            for (int i = 0; i < out.length; i++) {
                String raw = Integer.toString(out[i]);
                int width = widths[i];
                if (raw.length() < width) {
                    raw = "0".repeat(width - raw.length()) + raw;
                }
                tokens.add(raw);
            }
            return String.join("_", tokens).toUpperCase(Locale.ROOT);
        }
    }
}
