---
status: "accepted"
date: 2026-05-09
decision-makers: ["Akihiro TAKASE"]
consulted: []
informed: []
---

# Use zero-configuration formatters for Java and Kotlin

## Context and Problem Statement

The project contains both Java and Kotlin source files. Kotlin source files (`.kt`) had no formatter configured — only `.gradle.kts` build scripts were covered by ktlint. With AI agents writing most of the code, style inconsistencies are common and a zero-configuration, maximally-opinionated formatter is preferable to one with configurable rules.

## Decision Drivers

* Minimal configuration: no project-specific ruleset files to maintain
* Enforced uniformity: formatter behavior is intentionally non-configurable apart from style presets and limited integration settings
* Consistency across languages: Java and Kotlin should follow the same formatting philosophy
* Compatibility with the project's Kotlin version (2.3.20+, currently 2.3.21)

## Considered Options

* ktlint
* ktfmt

## Decision Outcome

Chosen option: **ktfmt 0.62 for Kotlin, googleJavaFormat for Java**, configured via Spotless.

Both formatters share a similar philosophy: deterministic, highly opinionated formatting with little to no project-specific configuration. ktfmt is based on google-java-format, making the two a natural pairing for a Java/Kotlin codebase.

### Consequences

* Good: Kotlin source files (`.kt`) are now formatted on every `spotlessApply` run, closing the previous coverage gap.
* Good: `.gradle.kts` build scripts are also reformatted to ktfmt style, unifying all Kotlin files under one formatter.
* Good: `trimTrailingWhitespace()` and `endWithNewline()` helpers are no longer needed — both ktfmt and googleJavaFormat handle trailing whitespace removal and final-newline enforcement automatically.
* Neutral: ktfmt version must be pinned explicitly. ktfmt 0.62 is required for Kotlin 2.3.20+ compatibility because Kotlin's compiler PSI renamed `CONTEXT_RECEIVER_LIST` to `CONTEXT_PARAMETER_LIST` in 2.3.20; older ktfmt versions can fail when run against Kotlin 2.3.20 or later (this project uses 2.3.21).

### Confirmation

`./gradlew :service:spotlessCheck` passes with no violations.

## Pros and Cons of the Options

### ktlint

* Good: well-established, widely used in the Kotlin ecosystem
* Good: compatible with a broad range of Kotlin versions out of the box
* Neutral: works for both `.kt` and `.gradle.kts` via Spotless `kotlin {}` and `kotlinGradle {}` blocks
* Bad: rule-based and configurable via `.editorconfig` — leaves room for per-project drift

### ktfmt

* Good: single enforced style, no configuration surface
* Good: developed by Facebook, explicitly modeled after googleJavaFormat — consistent philosophy with the Java formatter
* Good: handles trailing whitespace and final newlines automatically
* Bad: tightly coupled to the Kotlin compiler's internal PSI API, causing version incompatibilities with newer Kotlin releases (required pinning to 0.62 for Kotlin 2.3.20+ support)

## More Information

* Spotless version: 8.4.0
* ktfmt version: 0.62 (minimum required for Kotlin 2.3.20+ compatibility)
* Configuration: [`build-logic/src/main/kotlin/spring-boot-conventions.gradle.kts`](../../build-logic/src/main/kotlin/spring-boot-conventions.gradle.kts)
