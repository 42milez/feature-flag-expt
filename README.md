# feature-flag-expt

## Running the Service

### Prerequisites

A PostgreSQL instance must be running and accessible. The defaults match the credentials below:

| Variable | Default |
|---|---|
| `FEATURE_FLAGS_DB_URL` | `jdbc:postgresql://localhost:5432/featureflags` |
| `FEATURE_FLAGS_DB_USERNAME` | `featureflags` |
| `FEATURE_FLAGS_DB_PASSWORD` | `featureflags` |

### Start PostgreSQL with Docker

For local Swagger UI checks, start a PostgreSQL container with the default
database settings:

```bash
docker run --name feature-flags-postgres \
  -e POSTGRES_DB=featureflags \
  -e POSTGRES_USER=featureflags \
  -e POSTGRES_PASSWORD=featureflags \
  -p 5432:5432 \
  -d postgres:16-alpine
```

If the container already exists, start it again:

```bash
docker start feature-flags-postgres
```

The PostgreSQL container only starts the database process. Schema migrations are
applied by Flyway when the Spring Boot application starts, using the migrations
under `service/src/main/resources/db/migration`.

It is also possible to run migrations from a PostgreSQL container startup flow
with tools such as Flyway CLI or `docker-entrypoint-initdb.d`, but this project
uses Spring Boot startup as the standard migration path.

### Start the service

```bash
./gradlew :service:bootRun
```

Override the database connection if needed:

```bash
FEATURE_FLAGS_DB_URL=jdbc:postgresql://localhost:5432/featureflags \
FEATURE_FLAGS_DB_USERNAME=featureflags \
FEATURE_FLAGS_DB_PASSWORD=featureflags \
./gradlew :service:bootRun
```

### Swagger UI

Once the service is running, open the Swagger UI in a browser:

```
http://localhost:8080/swagger-ui.html
```

The raw OpenAPI spec is also available at:

| Format | URL |
|---|---|
| JSON | `http://localhost:8080/v3/api-docs` |
| YAML | `http://localhost:8080/v3/api-docs.yaml` |

A static snapshot of the spec is committed at [docs/openapi.yaml](docs/openapi.yaml).

## Static Analysis

### Check formatting (Spotless)

```bash
./gradlew :service:spotlessCheck
```

### Fix formatting automatically (Spotless)

```bash
./gradlew :service:spotlessApply
```

### Run static analysis (Error Prone)

Error Prone runs automatically during compilation.

```bash
./gradlew :service:compileJava
```

## Running Tests

### Run all tests

```bash
./gradlew :service:test
```

### Run a specific test class

```bash
./gradlew :service:test --tests "com.github.milez42.featureflags.flags.FeatureFlagEvaluatorTest"
```

### Run a specific test method

```bash
./gradlew :service:test --tests "com.github.milez42.featureflags.flags.FeatureFlagEvaluatorTest.fullRolloutEnablesFlag"
```
