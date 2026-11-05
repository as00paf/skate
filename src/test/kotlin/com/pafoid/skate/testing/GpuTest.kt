package com.pafoid.skate.testing

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable

/**
 * A test that needs a real OpenGL context (a GLFW window on a GPU). GitHub-hosted runners have no GPU, so GLFW cannot
 * create a window there (QA-15). These tests run on developer machines and are disabled where `CI=true`, which
 * GitHub Actions sets. Reproduce CI locally with `set CI=true` before `gradlew.bat test`.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Tag("gpu")
@DisabledIfEnvironmentVariable(
    named = "CI", matches = "true",
    disabledReason = "needs an OpenGL context; CI runners have no GPU (run locally, see GpuTest)",
)
annotation class GpuTest
