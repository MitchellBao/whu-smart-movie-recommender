param(
    [string]$DataDir = "",
    [string]$EnvFile = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$algorithmDir = Join-Path $repoRoot "algorithm-service"
$venvDir = Join-Path $algorithmDir ".venv"
$venvPython = Join-Path $venvDir "Scripts\python.exe"
$importScript = Join-Path $PSScriptRoot "import_movielens_mysql.py"

if ([string]::IsNullOrWhiteSpace($DataDir)) {
    $DataDir = Join-Path $repoRoot "data\movielens"
}
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $repoRoot "backend\.env"
}

if (!(Test-Path $venvPython)) {
    $pythonCmd = Get-Command python -ErrorAction Stop
    & $pythonCmd.Source -m venv $venvDir
}

& $venvPython -m pip install -r (Join-Path $algorithmDir "requirements.txt")
if ($LASTEXITCODE -ne 0) {
    throw "Failed to install Python dependencies."
}

& $venvPython $importScript --data-dir $DataDir --env-file $EnvFile
if ($LASTEXITCODE -ne 0) {
    throw "MovieLens import failed."
}
