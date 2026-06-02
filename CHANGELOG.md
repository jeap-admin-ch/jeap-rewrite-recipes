# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.5.3] - 2026-06-02

- Added import rewrites for Prometheus imports
- Added import rewrite for UserDetailsServiceAutoConfiguration
- Added optional dependency for Spring Web MVC Test Starter

### Added

## [1.5.2] - 2026-06-01

### Added

- Add recipe `ch.admin.bit.jeap.openrewrite.recipe.springboot.AddSpringBootStarterCache`.
- Add recipe `ch.admin.bit.jeap.openrewrite.recipe.maven.MigrateHypersistenceUtils`.
- Register Spring Boot cache starter and Hypersistence migration recipes in `spring-boot-40-minimal.yml`.

### Fixed

- Fix `AddSpringBootStarterCache` so `@DataJpaTest` classes get `@AutoConfigureCache` when `@EnableCaching` is used in the module.
- Ensure `AddSpringBootStarterCache` generates `import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;` and uses short annotation form `@AutoConfigureCache`.
- Improve `AddSpringBootStarterCache` detection fallback for unresolved type attribution by matching annotation simple names.
- Fix `RemoveSpringCloudDependenciesBomImport` to also remove `org.springframework.cloud:spring-cloud-dependencies` from profile-scoped `dependencyManagement` sections.

## [1.5.1] - 2026-05-29

### Added

- Add more jEAP spring boot 4 migration recipes
- Add custom recipes for spring retry
- Add custom recipes for jackson 3
- Add custom recipe for Spring  Security PathMatcher
- Add custom Kafka recipes
- Add custom Flyway migration recipe
- Add custom testcontainer recipes
- Add custom Hibernate recipe

## [1.5.0] - 2026-03-31

### Added

- Add jEAP spring boot 4 migration recipe
- Remove deprecated recipes

## [1.4.0] - 2026-03-30

### Changed

- Prepare repository for Open Source distribution

## [1.3.3] - 2023-09-08

### Changed

- Upgrade jEAP parent to 20.2.2

## [1.3.2] - 2023-08-29

### Changed

- Upgrade jEAP parent to 20.0.3

## [1.3.1] - 2023-08-25

### Changed

- Set hibernate timezone storage to same behaviour as Hibernate 5

## [1.3.0] - 2023-08-22

### Changed

- Upgrade jEAP parent to 20.0.1
- Auto-migrate PostgreSQL dialect property

## [1.2.1] - 2023-08-16

### Changed

- Upgrade rewrite-spring to 5.0.7
- Upgrade jEAP parent to 20.0.0

## [1.2.0] - 2023-07-11

### Changed

- Add recipe: ch.admin.bit.jeap.openrewrite.recipe.maven.UpgradeMavenWrapper

## [1.1.0] - 2023-07-11

### Changed

- Add recipe: JeapUpgradeSpringBoot_3_1_Minimal - Spring Boot 3.1 Upgrade without Java 17 migration

## [1.0.1] - 2023-07-10

### Changed

- migrate org.apache.http.HttpHeaders to spring constants

## [1.0.0] - 2023-07-12

### Added

- migration recipes to upgrade jEAP apps to spring boot 3.1
