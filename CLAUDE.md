# Project Instructions

- All written content — source code comments, documentation (README.md, etc.), and any other text artifacts — must be in English.
- After completing any implementation task, run the following checks in order and confirm all pass before reporting the task as done:
  1. `./gradlew :service:spotlessCheck` — formatting
  2. `./gradlew :service:compileJava` — static analysis (Error Prone)
  3. `./gradlew :service:test` — full test suite

## Technology Notes

- This project uses Spring Boot 4.x, which auto-configures Jackson 3.x (`tools.jackson`).
  Always use `tools.jackson.*` imports, never `com.fasterxml.jackson.*` (Jackson 2.x).
  Mixing the two causes `NoSuchBeanDefinitionException` at runtime.
