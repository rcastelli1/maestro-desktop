@echo off
setlocal
cd /d "%~dp0"

if not defined JAVA_HOME if exist "C:\Users\Public\jdk-21.0.8\bin\java.exe" set "JAVA_HOME=C:\Users\Public\jdk-21.0.8"

if exist ".tools\apache-maven-3.9.16\bin\mvn.cmd" (
    call ".tools\apache-maven-3.9.16\bin\mvn.cmd" "-Dmaven.repo.local=.tools/m2" javafx:run
) else (
    call "mvnw.cmd" javafx:run
)

if errorlevel 1 (
    echo.
    echo Nao foi possivel iniciar. Confira o JDK 21 e a conexao de internet.
    pause
)
