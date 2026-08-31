# Architecture

`jeap-rewrite-recipes` is an [OpenRewrite](https://docs.openrewrite.org/) recipe module: a Maven module
that packages custom [OpenRewrite](https://docs.openrewrite.org/) recipes for migrating applications
based on the jEAP Blueprint Microservices. It is consumed by the
[jeap-cli](https://github.com/jeap-admin-ch/jeap-cli) tool, which drives automated Spring Boot and
Spring Framework version migrations and other code refactorings against a jEAP microservice.

## Module structure

```text
src/main/java/...                        # custom Java recipes (org.openrewrite.Recipe implementations)
src/main/resources/META-INF/rewrite/*.yml # declarative recipe definitions (recipe lists, preconditions)
src/test/java/...                        # recipe tests, one test class per recipe
```

Recipes are grouped by concern in both the Java packages and the YAML declarative-recipe files, for
example:

- `boot/` — Spring Boot version-upgrade recipes (e.g. package moves, managed-dependency additions).
- `jackson/` — Jackson `@JsonCreator` hardening and API-normalization recipes.
- `maven/` — Maven POM cleanups (e.g. removing now-redundant BOM imports).
- `flyway/` — Flyway migration helpers (e.g. ensuring an outbox column migration exists).
- `hibernate/`, `kafka/`, `springboot/`, `security/`, `testing/` — further migration recipes for their
  respective concerns.

A top-level composite recipe (`ch.admin.bit.jeap.openrewrite.recipe.UpgradeSpringBoot_4_0_NoOtherMigrations`
in `spring-boot-40-minimal.yml`) chains the individual recipes needed to migrate a jEAP app that is
already on Spring Boot 3.5.x to Spring Boot 4.0, minimizing the changeset for apps already on the
expected baseline.

## Attribution and licensing

Recipes derived from [rewrite-spring](https://github.com/openrewrite/rewrite-spring) are licensed under
the [Moderne Source Available License](https://docs.moderne.io/licensing/moderne-source-available-license/)
and carry that license header individually; they may not be commercialized or provided to others as a
managed service, and their license/copyright notices must not be removed. The rest of the repository
(custom jEAP-specific recipes) is licensed under the Apache License 2.0.

## Related

- [Getting started](getting-started.md)
- [jeap-cli](https://github.com/jeap-admin-ch/jeap-cli) — the tool that applies these recipes
- [OpenRewrite documentation](https://docs.openrewrite.org/)
