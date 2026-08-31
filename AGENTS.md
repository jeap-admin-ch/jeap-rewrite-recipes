# AGENTS.md

Guidance for AI coding agents working **in this repository**. For how to use the recipes (via
jeap-cli or directly with the OpenRewrite Maven plugin), read [README.md](README.md) and the
[docs/](docs/) folder instead.

## Project

`jeap-rewrite-recipes` packages custom [OpenRewrite](https://docs.openrewrite.org/) recipes for
migrating jEAP Blueprint Microservices — Spring Boot/Framework version upgrades and other automated
code refactorings. It is consumed by [jeap-cli](https://github.com/jeap-admin-ch/jeap-cli).

## Repository layout

```
src/main/java/ch/admin/bit/jeap/openrewrite/recipe/<area>/   # custom Java recipes, grouped by area
  (boot, jackson, maven, flyway, hibernate, kafka, springboot, security, testing, ...)
src/main/resources/META-INF/rewrite/*.yml                    # declarative recipe lists / compositions
src/test/java/.../<area>/<Recipe>Test.java                   # one test class per recipe
Jenkinsfile, publiccode.yml, LICENSE
```

There are **no CHANGELOG.md** and **no child modules** in this repository.

## Build & test

```bash
./mvnw test
```

- Parent: `ch.admin.bit.jeap:jeap-internal-spring-boot-parent`.
- Every recipe (Java or declarative) must have a corresponding test using OpenRewrite's `RewriteTest`
  harness that asserts on before/after source.

## Conventions (load-bearing)

- **Attribution & licensing**: recipes derived from
  [rewrite-spring](https://github.com/openrewrite/rewrite-spring) are licensed under the
  [Moderne Source Available License](https://docs.moderne.io/licensing/moderne-source-available-license/)
  and must keep that license header — they may not be commercialized or offered as a managed service,
  and their license/copyright notices must not be removed or obscured. All other (jEAP-original)
  recipes are Apache License 2.0. Preserve the correct header when adding or modifying a recipe.
- Recipes are grouped by concern **consistently** across both the Java package structure and the
  declarative `.yml` file names — keep new recipes in the matching area, or add a new area directory +
  yml file if none fits.
- The composite recipe `ch.admin.bit.jeap.openrewrite.recipe.UpgradeSpringBoot_4_0_NoOtherMigrations`
  in `spring-boot-40-minimal.yml` is the entry point jeap-cli's Spring Boot 4.0 migration uses — when
  adding a new migration step for that upgrade path, add it to this recipe's `recipeList`, not as a
  standalone unreferenced recipe.
- Never modify a recipe's behavior for an already-released version without considering downstream
  reproducibility — prefer adding a new recipe over silently changing an existing one's semantics.

## Docs

When adding a new recipe (or a new declarative recipe file), add it to the recipe catalog in
[docs/getting-started.md](docs/getting-started.md).

## Versioning

- Semantic Versioning. The project `<version>` lives directly in `pom.xml` (single module).
- On a feature branch keep the `-SNAPSHOT` suffix; CI removes it when releasing.
- This repo has **no CHANGELOG.md**. When bumping the version, also update `softwareVersion` and
  `releaseDate` in `publiccode.yml`.
- Use the JIRA ID from the branch name as the commit-message prefix (e.g. `JEAP-1234 Add ...`); do not
  use conventional commits.
