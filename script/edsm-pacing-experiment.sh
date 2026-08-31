#!/bin/bash
cd "$(dirname "$0")/.."
exec mvn -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=org.dce.ed.tools.pacing.EdsmPacingExperimentFrame
