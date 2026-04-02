@echo off
if "%~1"=="" (
  echo Usage: %~nx0 C:\path\to\jace.jar [version]
  exit /b 1
)

set JACE_JAR=%~1
set VERSION=%~2
if "%VERSION%"=="" set VERSION=5.5.12.0

mvn install:install-file ^
  -Dfile=%JACE_JAR% ^
  -DgroupId=com.ibm.filenet ^
  -DartifactId=jace ^
  -Dversion=%VERSION% ^
  -Dpackaging=jar
