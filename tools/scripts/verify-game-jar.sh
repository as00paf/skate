#!/usr/bin/env bash
# TLG-10 acceptance check: change game code, build it the way the editor is started, export a project, and check the
# exported skate-game.jar contains the change. Run from WSL/Git Bash (uses gradlew.bat via cmd.exe when available).
set -euo pipefail
cd "$(dirname "$0")/../.."
if command -v cmd.exe >/dev/null 2>&1 && [ -f gradlew.bat ]; then gradle() { cmd.exe /c gradlew.bat "$@"; }; else gradle() { ./gradlew "$@"; }; fi

token="probe$(date +%s%N)"
probe=src/main/kotlin/com/pafoid/skate/game/GameJarProbe.kt
trap 'rm -f "$probe"' EXIT
cat > "$probe" <<KT
package com.pafoid.skate.game

/** Temporary marker written by tools/scripts/verify-game-jar.sh; never commit. */
object GameJarProbe { const val TOKEN = "$token" }
KT
echo "1/3 game code changed (GameJarProbe.TOKEN = $token)"

# gradlew run: the editor launch must schedule gameJar (dry run, so no editor window opens)
plan=$(gradle run --dry-run)
grep -q ':gameJar' <<<"$plan" || { echo "FAIL: gradlew run does not build gameJar"; exit 1; }
# IntelliJ (PAFSK8): Make = :classes, then the before-launch Gradle task gameJar
gradle -q classes
gradle -q gameJar
echo "2/3 built like the editor launch (gradlew run schedules gameJar; IntelliJ: classes + gameJar)"

# A real ProjectExporter export must ship the jar containing the new token
gradle -q :test --tests "com.pafoid.skate.build.GameJarBuildTest" "-Pskate.gameJarToken=$token"
echo "3/3 export contains the change: OK"
