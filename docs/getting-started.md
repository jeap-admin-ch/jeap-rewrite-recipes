# Getting started

## Applying recipes via jeap-cli

Most users apply these recipes indirectly through the
[jeap-cli](https://github.com/jeap-admin-ch/jeap-cli) tool, which selects and runs the appropriate
migration recipe(s) for a jEAP microservice. See the jeap-cli documentation for the available
migration commands.

## Applying recipes directly with Maven

The recipes can also be run directly with the
[OpenRewrite Maven plugin](https://docs.openrewrite.org/running-recipes/getting-started) against a
target project:

```xml
<plugin>
    <groupId>org.openrewrite.maven</groupId>
    <artifactId>rewrite-maven-plugin</artifactId>
    <configuration>
        <activeRecipes>
            <recipe>ch.admin.bit.jeap.openrewrite.recipe.UpgradeSpringBoot_4_0_NoOtherMigrations</recipe>
        </activeRecipes>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>ch.admin.bit.jeap</groupId>
            <artifactId>jeap-rewrite-recipes</artifactId>
            <version>${jeap-rewrite-recipes.version}</version>
        </dependency>
    </dependencies>
</plugin>
```

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:run
```

## Available recipes

Declarative recipe lists live under `src/main/resources/META-INF/rewrite/`:

- `spring-boot-40-minimal.yml` — `UpgradeSpringBoot_4_0_NoOtherMigrations`, the composite recipe that
  migrates a jEAP app already on Spring Boot 3.5.x to Spring Boot 4.0.
- `jackson-json-creator.yml` — hardens JPA-entity `@JsonCreator` usage
  (`HardenJpaJsonCreators`, `HardenJpaJsonCreatorsProofOfPossessionOnly`).
- `flyway-outbox.yml` — ensures the messaging outbox table migration is present
  (`EnsureOutboxColumnMigration`, `AddMissingOutboxColumnMigrationExample`).

Custom Java recipes (one class per recipe, grouped by package under
`ch.admin.bit.jeap.openrewrite.recipe`) cover further migrations for Spring Boot, Jackson, Maven,
Hibernate, Kafka, Spring Security and testing concerns; see the corresponding test class under
`src/test/java` for a runnable before/after example of each recipe's effect.

## Developing a new recipe

1. Add the recipe as a Java class (or a new entry in a declarative `.yml` file under
   `src/main/resources/META-INF/rewrite/`).
2. Add a test class under `src/test/java` using OpenRewrite's `RewriteTest` harness, asserting on the
   before/after source.
3. Run `./mvnw test` to validate the recipe against its test fixtures.

## Related

- [Architecture](architecture.md)
- [jeap-cli](https://github.com/jeap-admin-ch/jeap-cli)
- [OpenRewrite recipe development guide](https://docs.openrewrite.org/authoring-recipes)
- [jeap-rewrite-recipes README](../README.md)
