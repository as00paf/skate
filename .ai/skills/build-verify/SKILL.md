---
name: build-verify
description: Run a fast compile check to verify the project builds without errors. Use after code changes to catch compilation errors before running tests or the application.
---

# Build Verify

Run a fast Kotlin compilation check to verify the project builds without errors.

## When to Use

- After implementing code changes
- Before running the full test suite
- Before committing changes
- When the user asks to "check if it builds" or "verify compile"

## How to Execute

Run the Gradle compile task:

```bash
./gradlew compileKotlin
```

This is faster than a full `build` because it skips tests and packaging.

## Expected Output

**Success:**

```
BUILD SUCCESSFUL in Xs
X actionable tasks executed, X completed
```

**Failure:**

```
> Task :compileKotlin FAILED
e: file://path/to/File.kt:line:col error description
BUILD FAILED in Xs
```

## If Build Fails

1. Read the error output carefully
2. Identify the file and line with the error
3. Fix the compilation error
4. Run `./gradlew compileKotlin` again to verify
5. Repeat until build succeeds

## Full Build (Optional)

If compile passes and you want a full verification:

```bash
./gradlew build
```

This runs tests, linting, and packaging but takes longer.
