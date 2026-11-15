$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $projectRoot

$maven = Join-Path $projectRoot 'mvnw.cmd'
& $maven -q package
if ($LASTEXITCODE -ne 0) { throw 'A compilação Maven falhou.' }

& $maven -q 'org.apache.maven.plugins:maven-dependency-plugin:3.10.0:build-classpath' `
    '-Dmdep.includeScope=test' '-Dmdep.outputFile=target/test-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível montar o classpath das verificações.' }

$dependencies = (Get-Content -LiteralPath 'target/test-classpath.txt' -Raw).Trim()
$classpath = 'target/classes;target/test-classes;' + $dependencies
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java.exe' }
$checks = @(
    'br.com.agenteslocal.mobile.QueuedMessagesCheck',
    'br.com.agenteslocal.mobile.ScheduledMessageCheck',
    'br.com.agenteslocal.mobile.FirebaseRemoteSettingsCheck',
    'br.com.agenteslocal.data.BackupSecurityCheck'
)

foreach ($check in $checks) {
    $env:AGENTES_LOCAL_HOME = Join-Path $projectRoot ('target/verification/' + [Guid]::NewGuid().ToString('N'))
    & $java -cp $classpath $check
    if ($LASTEXITCODE -ne 0) { throw "Verificação falhou: $check" }
}
Remove-Item Env:AGENTES_LOCAL_HOME -ErrorAction SilentlyContinue
Write-Host 'Compilação e verificações de fila, agendamento e backup: OK'
