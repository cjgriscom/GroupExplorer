mvn -q exec:java \
  -Dexec.jvmArgs="-Xms250g -Xmx250g -XX:+UseG1GC" \
  -Dexec.mainClass=io.chandler.gap.SamplePuzzleDepthDistribution \
  -Dexec.args="--puzzles /tmp/sample.ts --puzzle-id weyl_e8 --compress-longs 2 --no-keyframes --dump-depths weyl_e8_depths.txt"
