---
name: test-run
description: Run the project's test suite with optional filtering. Use after code changes to verify functionality, when debugging failures, or when the user asks to run tests.
---

# Test Run

Run the project's test suite with optional class or method filtering.

## When to Use

- After implementing features or fixes
- When debugging test failures
- When the user asks to "run tests" or "check if tests pass"
- Before committing changes

## How to Execute

### Run All Tests

```bash
./gradlew test
```

### Run a Specific Test Class

```bash
./gradlew test --tests "com.pafoid.skate.engine.physics.SkateboardPhysicsTest"
```

### Run a Specific Test Method

```bash
./gradlew test --tests "com.pafoid.skate.engine.physics.SkateboardPhysicsTest.applyTailImpulse_StationaryOnGround_NoseMovesUpward"
```

### Run All Tests in a Package

```bash
./gradlew test --tests "com.pafoid.skate.engine.physics.*"
```

## Expected Output

**All Pass:**

```
> Task :test
BUILD SUCCESSFUL in Xs
X actionable tasks executed, X completed
```

**Failures:**

```
> Task :test FAILED

X tests completed, Y failed, Z skipped
```

Individual test failures show stack traces and assertion messages.

## Interpreting Results

1. **0 failed, 0 skipped** → All green, safe to proceed
2. **Failures** → Read the failure output, identify the cause:
    - Assertion failures → logic bug
    - Exceptions → runtime error in test setup
    - Timeouts → performance issue or infinite loop
3. **Skipped** → Tests marked `@Disabled`, usually intentional

## After Fixing Failures

Always re-run the specific test class to verify:

```bash
./gradlew test --tests "com.pafoid.skate.path.to.FailingTest"
```

## Performance Note

The test suite uses 2GB max heap. If you see OutOfMemoryError:

```bash
export GRADLE_OPTS="-Xmx4g"
./gradlew test
```
