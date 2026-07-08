#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

# Override on the host, e.g. HEAP=240g ./scripts/run.sh
HEAP="${HEAP:-240g}"
JVM_ARGS=(-Xms"${HEAP}" -Xmx"${HEAP}" -XX:+UseG1GC -XX:MaxGCPauseMillis=500)

ARGS=(
  --puzzles /tmp/sample.ts
  --puzzle-id weyl_e8
  --compress-longs 2
  --no-keyframes
  --dump-depths weyl_e8_depths.txt
)

mvn -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt

exec java "${JVM_ARGS[@]}" \
  -cp "target/classes:$(<target/classpath.txt)" \
  io.chandler.gap.SamplePuzzleDepthDistribution \
  "${ARGS[@]}"
