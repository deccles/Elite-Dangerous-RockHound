@echo off
cd /d "%~dp0\.."
call mvn -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.mainClass=org.dce.ed.tools.pacing.EdsmPacingExperimentFrame"
