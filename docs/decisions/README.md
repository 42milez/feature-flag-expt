# Architecture Decision Records

This directory contains Architecture Decision Records (ADRs) for this project, following the [MADR v4](https://adr.github.io/madr/) format.

## Index

| ID                                                   | Title                                                         | Status   |
|------------------------------------------------------|---------------------------------------------------------------|----------|
| [0001](0001-use-zero-configuration-formatters.md)    | Use zero-configuration formatters for Java and Kotlin         | Accepted |
| [0002](0002-use-spring-data-jdbc-instead-of-jpa.md)  | Use Spring Data JDBC instead of JPA/Hibernate                 | Accepted |
| [0003](0003-represent-audit-event-details-as-sealed-interface-with-records.md) | Represent audit event details as a sealed interface with records | Accepted |
| [0004](0004-enforce-audit-atomicity-with-propagation-mandatory.md) | Enforce audit atomicity with `Propagation.MANDATORY`          | Accepted |
| [0005](0005-separate-domain-records-from-persistence-entities.md) | Separate domain records from persistence entities             | Accepted |
| [0006](0006-use-code-first-openapi-with-springdoc-openapi.md) | Use code-first OpenAPI with springdoc-openapi                 | Accepted |
| [0007](0007-generate-committed-openapi-snapshot-with-springdoc-gradle-plugin.md) | Generate the committed OpenAPI snapshot with the springdoc Gradle plugin | Accepted |
