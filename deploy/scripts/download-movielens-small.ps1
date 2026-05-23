param(
    [string]$OutputDir = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $repoRoot "data\movielens"
}

$zipPath = Join-Path $OutputDir "ml-latest-small.zip"
$url = "https://files.grouplens.org/datasets/movielens/ml-latest-small.zip"

New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

Write-Host "Downloading MovieLens small dataset..."
Write-Host "target: $zipPath"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
try {
    Invoke-WebRequest -Uri $url -OutFile $zipPath
} catch {
    Write-Host "[WARN] Invoke-WebRequest failed, trying Python urllib fallback..."
    $venvPython = Join-Path $repoRoot "algorithm-service\.venv\Scripts\python.exe"
    $pythonCmd = Get-Command python -ErrorAction SilentlyContinue
    if (Test-Path $venvPython) {
        & $venvPython -c "import urllib.request; urllib.request.urlretrieve('$url', r'$zipPath')"
    } elseif ($pythonCmd) {
        & $pythonCmd.Source -c "import urllib.request; urllib.request.urlretrieve('$url', r'$zipPath')"
    } else {
        throw
    }
}

Write-Host "Extracting dataset..."
Expand-Archive -LiteralPath $zipPath -DestinationPath $OutputDir -Force

$movies = Get-ChildItem -Path $OutputDir -Recurse -Filter "movies.csv" | Select-Object -First 1
$ratings = Get-ChildItem -Path $OutputDir -Recurse -Filter "ratings.csv" | Select-Object -First 1

if ($null -eq $movies -or $null -eq $ratings) {
    throw "MovieLens files not found after extraction."
}

Write-Host "[OK] movies:  $($movies.FullName)"
Write-Host "[OK] ratings: $($ratings.FullName)"
