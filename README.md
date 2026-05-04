# feature-flag-expt

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
