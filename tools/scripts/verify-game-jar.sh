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
echo "1/4 game code changed (GameJarProbe.TOKEN = $token)"

# gradlew run: the editor launch must schedule gameJar (dry run, so no editor window opens)
plan=$(gradle run --dry-run)
grep -q ':gameJar' <<<"$plan" || { echo "FAIL: gradlew run does not build gameJar"; exit 1; }
# IntelliJ (PAFSK8): Make = :classes, then the before-launch Gradle task gameJar
gradle -q classes
gradle -q gameJar
echo "2/4 built like the editor launch (gradlew run schedules gameJar; IntelliJ: classes + gameJar)"

# Both physics backends' natives must ship in the game jar (PHY-17: Bullet and Jolt)
jar=build/libs/skate-game.jar
list_jar() { if command -v unzip >/dev/null 2>&1; then unzip -Z1 "$1"; else python3 -c 'import sys,zipfile; print("\n".join(zipfile.ZipFile(sys.argv[1]).namelist()))' "$1"; fi; }
entries=$(list_jar "$jar")
for native in native/windows/x86_64/bulletjme.dll windows/x86-64/com/github/stephengold/joltjni.dll; do
  grep -qx "$native" <<<"$entries" || { echo "FAIL: $jar is missing $native"; exit 1; }
done
# PHY-20: Bullet must be the release build; its debug build carries a "Debug_" build-type marker and a .pdb path
jar_entry() { if command -v unzip >/dev/null 2>&1; then unzip -p "$1" "$2"; else python3 -c 'import sys,zipfile; sys.stdout.buffer.write(zipfile.ZipFile(sys.argv[1]).read(sys.argv[2]))' "$1" "$2"; fi; }
if jar_entry "$jar" native/windows/x86_64/bulletjme.dll | grep -a -q 'Debug_'; then
  echo "FAIL: $jar ships the debug Bullet native (use the SpRelease classifier)"; exit 1
fi
echo "3/4 game jar ships the Bullet (release) and Jolt physics natives"
# AST-27: every engine shader in the repo's assets/Shaders (basic and internal) ships in the jar under /shaders/
for shader in assets/Shaders/*.glsl; do
  grep -qx "shaders/$(basename "$shader")" <<<"$entries" || { echo "FAIL: $jar is missing shaders/$(basename "$shader")"; exit 1; }
done
echo "   game jar ships all $(ls assets/Shaders/*.glsl | wc -l) engine shaders"

# A real ProjectExporter export must ship the jar containing the new token
gradle -q :test --tests "com.pafoid.skate.build.GameJarBuildTest" "-Pskate.gameJarToken=$token"
echo "4/4 export contains the change: OK"
