$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$localJdk = 'C:\Users\Public\jdk-21.0.8'
if (-not $env:JAVA_HOME -and (Test-Path (Join-Path $localJdk 'bin\java.exe'))) {
    $env:JAVA_HOME = $localJdk
}

$localMaven = Join-Path $projectRoot '.tools\apache-maven-3.9.16\bin\mvn.cmd'
if (Test-Path $localMaven) {
    & $localMaven '-Dmaven.repo.local=.tools/m2' javafx:run
} elseif (Test-Path (Join-Path $projectRoot 'mvnw.cmd')) {
    & (Join-Path $projectRoot 'mvnw.cmd') javafx:run
} else {
    $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (-not $maven) {
        Write-Error 'Maven não encontrado. Instale Maven ou use o Maven Wrapper do projeto.'
        exit 1
    }
    & $maven.Source javafx:run
}
