$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $projectRoot

$tracked = @(& git ls-files --cached)
if ($LASTEXITCODE -ne 0) { throw 'Inicialize o repositório Git antes de verificar os arquivos.' }

$forbidden = @(
    '(^|/)(target|\.tools|\.local-data|node_modules|\.expo|dist|remote-inflight)/',
    '(^|/)(google-services\.json|firebaseConfig\.local\.ts|firestore\.rules|workspace\.json|firebase-remote\.json|push-devices\.json|mobile-access-token)$',
    '(^|/)[^/]*(service-account|adminsdk)[^/]*\.json$',
    '(^|/)\.env($|\.)',
    '\.(apk|aab|zip|maestrobackup|jks|keystore|pem|p8|p12|key)$'
)
$problems = @()
foreach ($item in $tracked) {
    $path = $item.Replace('\', '/')
    foreach ($pattern in $forbidden) {
        if ($path -match $pattern) { $problems += $path; break }
    }
    if (Test-Path -LiteralPath $item -PathType Leaf) {
        if (Select-String -LiteralPath $item -Pattern '-----BEGIN (RSA |EC )?PRIVATE KEY-----' -Quiet) {
            $problems += $path
        }
    }
}
if ($problems.Count -gt 0) {
    $problems | Sort-Object -Unique | ForEach-Object { Write-Error "Arquivo sensível no índice Git: $_" }
    throw 'Remova os arquivos acima do índice antes do commit.'
}
Write-Host ("Arquivos no índice Git verificados: " + $tracked.Count)
