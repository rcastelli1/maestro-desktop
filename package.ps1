$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
Set-Location -LiteralPath $projectRoot

if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath 'C:\Users\Public\jdk-21.0.8\bin\java.exe')) {
    $env:JAVA_HOME = 'C:\Users\Public\jdk-21.0.8'
}
if (-not $env:JAVA_HOME) { throw 'Configure JAVA_HOME com o caminho de um JDK 21.' }
$jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
if (-not (Test-Path -LiteralPath $jpackage)) { throw 'jpackage nao encontrado no JDK configurado.' }

$localMaven = Join-Path $projectRoot '.tools\apache-maven-3.9.16\bin\mvn.cmd'
if (Test-Path -LiteralPath $localMaven) {
    & $localMaven '-Dmaven.repo.local=.tools/m2' -q package
} else {
    & (Join-Path $projectRoot 'mvnw.cmd') -q package
}
if ($LASTEXITCODE -ne 0) { throw 'A compilacao Maven falhou.' }

function Remove-GeneratedDirectory([string] $target) {
    $absolute = [System.IO.Path]::GetFullPath($target)
    $expectedPrefix = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'target')) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $absolute.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Diretorio fora de target: $absolute"
    }
    if (Test-Path -LiteralPath $absolute) { Remove-Item -LiteralPath $absolute -Recurse -Force }
}

$inputDirectory = Join-Path $projectRoot 'target\package-input'
Remove-GeneratedDirectory $inputDirectory
New-Item -ItemType Directory -Path $inputDirectory -Force | Out-Null
$dependencyGoal = 'org.apache.maven.plugins:maven-dependency-plugin:3.10.0:copy-dependencies'
if (Test-Path -LiteralPath $localMaven) {
    & $localMaven '-Dmaven.repo.local=.tools/m2' -q $dependencyGoal '-DincludeScope=runtime' '-DoutputDirectory=target/package-input'
} else {
    & (Join-Path $projectRoot 'mvnw.cmd') -q $dependencyGoal '-DincludeScope=runtime' '-DoutputDirectory=target/package-input'
}
if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel copiar as dependencias do aplicativo.' }
Copy-Item -LiteralPath (Join-Path $projectRoot 'target\agentes-local-0.1.0.jar') -Destination $inputDirectory

$outputDirectory = Join-Path $projectRoot 'target\dist'
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$releaseDirectory = Join-Path $outputDirectory ('Maestro-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 6))
New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null
$appDirectory = Join-Path $releaseDirectory 'Maestro'
& $jpackage --type app-image --dest $releaseDirectory --name 'Maestro' `
    --input $inputDirectory --main-jar 'agentes-local-0.1.0.jar' `
    --main-class 'br.com.agenteslocal.DesktopLauncher' `
    --icon (Join-Path $projectRoot 'src\main\resources\brand\maestro.ico') `
    --description 'Gerenciador local de agentes de IA por projeto'
if ($LASTEXITCODE -ne 0) { throw 'O jpackage falhou.' }
$executable = Join-Path $appDirectory 'Maestro.exe'
Write-Host "Aplicativo criado em: $executable"

$desktopShortcut = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Maestro.lnk'
if (Test-Path -LiteralPath $desktopShortcut) {
    try {
        $shell = New-Object -ComObject WScript.Shell
        $shortcut = $shell.CreateShortcut($desktopShortcut)
        $distPrefix = [System.IO.Path]::GetFullPath($outputDirectory) + [System.IO.Path]::DirectorySeparatorChar
        if ($shortcut.TargetPath.StartsWith($distPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            $shortcut.TargetPath = $executable
            $shortcut.WorkingDirectory = $appDirectory
            $shortcut.IconLocation = $executable + ',0'
            $shortcut.Save()
            Write-Host "Atalho atualizado em: $desktopShortcut"
        } else {
            Write-Warning 'O atalho Maestro aponta para outro local e foi mantido.'
        }
    } catch {
        Write-Warning "Nao foi possivel atualizar o atalho: $($_.Exception.Message)"
    }
}
