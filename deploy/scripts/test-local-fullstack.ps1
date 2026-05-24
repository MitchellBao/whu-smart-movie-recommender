param(
    [int]$UserId = 1,
    [int]$MovieId = 1,
    [double]$Score = 4.5,
    [int]$TopN = 5,
    [int[]]$RecommendUserIds = @(1, 2, 10),
    [string]$AuthUsername = "",
    [string]$AuthPassword = "test123456",
    [string]$MovieKeyword = "Matrix",
    [string]$LlmQuery = "推荐一部烧脑科幻片",
    [switch]$AutoStart,
    [switch]$SkipMysqlCheck,
    [switch]$SkipLlmCheck
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$mysqlCheckScript = Join-Path $repoRoot "deploy\scripts\check-mysql-local.ps1"
$startScript = Join-Path $repoRoot "deploy\scripts\start-local-fullstack.ps1"

$results = New-Object System.Collections.Generic.List[object]

function Add-Result {
    param(
        [string]$Name,
        [bool]$Passed,
        [string]$Detail
    )
    $results.Add([PSCustomObject]@{
            Name   = $Name
            Passed = $Passed
            Detail = $Detail
        }) | Out-Null
}

function Invoke-Test {
    param(
        [string]$Name,
        [scriptblock]$Action
    )
    try {
        & $Action
        Add-Result -Name $Name -Passed $true -Detail "OK"
        Write-Host "[PASS] $Name"
    } catch {
        Add-Result -Name $Name -Passed $false -Detail $_.Exception.Message
        Write-Host "[FAIL] $Name -> $($_.Exception.Message)"
    }
}

function Test-Endpoint {
    param([string]$Url)
    try {
        $null = Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec 3
        return $true
    } catch {
        return $false
    }
}

function Assert-RecommendResponse {
    param(
        [object]$Response,
        [int]$ExpectedTopN
    )
    if ($Response.code -ne 0) { throw "code != 0" }
    if ($null -eq $Response.data) { throw "data is null" }

    $items = @($Response.data)
    if ($items.Count -lt $ExpectedTopN) {
        throw "expected at least $ExpectedTopN recommendations, got $($items.Count)"
    }

    foreach ($item in $items | Select-Object -First $ExpectedTopN) {
        if ($null -eq $item.movieId) { throw "movieId missing" }
        if ([string]::IsNullOrWhiteSpace($item.title)) {
            throw "movie title missing for movieId=$($item.movieId)"
        }
        if ([string]::IsNullOrWhiteSpace($item.genres)) {
            throw "movie genres missing for movieId=$($item.movieId)"
        }
        if ($null -eq $item.score) { throw "score missing for movieId=$($item.movieId)" }
        if ([string]::IsNullOrWhiteSpace($item.reason)) {
            throw "reason missing for movieId=$($item.movieId)"
        }
    }
}

Write-Host "== Local Fullstack Test =="
Write-Host "target userId=$UserId movieId=$MovieId score=$Score topN=$TopN"

if ([string]::IsNullOrWhiteSpace($AuthUsername)) {
    $AuthUsername = "test_user_$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
}

if ($AutoStart) {
    Write-Host "[INFO] AutoStart enabled. Ensuring services are running..."
    $algorithmUp = Test-Endpoint -Url "http://127.0.0.1:8000/api/python/health"
    $backendUp = Test-Endpoint -Url "http://127.0.0.1:8080/api/recommend/movie?userId=$UserId&topN=1"
    if (-not ($algorithmUp -and $backendUp)) {
        if (!(Test-Path $startScript)) {
            throw "Missing startup script: $startScript"
        }
        & powershell -NoProfile -ExecutionPolicy Bypass -File $startScript
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to auto start local fullstack, exit code: $LASTEXITCODE"
        }
    } else {
        Write-Host "[INFO] Services already healthy, skip autostart."
    }
}

if (-not $SkipMysqlCheck) {
    Invoke-Test -Name "MySQL connectivity script" -Action {
        if (!(Test-Path $mysqlCheckScript)) {
            throw "Missing script: $mysqlCheckScript"
        }
        & powershell -NoProfile -ExecutionPolicy Bypass -File $mysqlCheckScript
        if ($LASTEXITCODE -ne 0) {
            throw "check-mysql-local.ps1 exit code: $LASTEXITCODE"
        }
    }
}

Invoke-Test -Name "Algorithm health endpoint reads MovieLens ratings" -Action {
    $resp = Invoke-RestMethod -Uri "http://127.0.0.1:8000/api/python/health" -Method Get -TimeoutSec 5
    if ($resp.status -ne "ok") {
        throw "Unexpected status: $($resp.status)"
    }
    if ($resp.ratingCount -lt 1000) {
        throw "ratingCount too small: $($resp.ratingCount)"
    }
}

Invoke-Test -Name "Recommend API returns complete MovieLens data" -Action {
    $url = "http://127.0.0.1:8080/api/recommend/movie?userId=$UserId&topN=$TopN"
    $resp = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 20
    Assert-RecommendResponse -Response $resp -ExpectedTopN $TopN
}

Invoke-Test -Name "Recommend API stable for multiple users" -Action {
    foreach ($targetUserId in $RecommendUserIds) {
        $url = "http://127.0.0.1:8080/api/recommend/movie?userId=$targetUserId&topN=$TopN"
        $resp = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 20
        Assert-RecommendResponse -Response $resp -ExpectedTopN $TopN
    }
}

Invoke-Test -Name "User register and login APIs" -Action {
    $body = @{
        username = $AuthUsername
        password = $AuthPassword
        age      = 20
        gender   = "male"
    } | ConvertTo-Json
    $registerResp = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/user/register" -Method Post -ContentType "application/json" -Body $body -TimeoutSec 10
    if ($registerResp.code -ne 0) { throw "register code != 0" }
    if ($null -eq $registerResp.data.userId) { throw "registered userId missing" }

    $loginResp = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/user/login" -Method Post -ContentType "application/json" -Body $body -TimeoutSec 10
    if ($loginResp.code -ne 0) { throw "login code != 0" }
    if ($loginResp.data.userId -ne $registerResp.data.userId) { throw "login userId mismatch" }
}

Invoke-Test -Name "Movie search API returns browsable movies" -Action {
    $url = "http://127.0.0.1:8080/api/movie/search?keyword=$([uri]::EscapeDataString($MovieKeyword))&limit=5"
    $resp = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 10
    if ($resp.code -ne 0) { throw "code != 0" }
    $items = @($resp.data)
    if ($items.Count -lt 1) { throw "movie search returned empty" }
    foreach ($item in $items) {
        if ($null -eq $item.movieId) { throw "movieId missing" }
        if ([string]::IsNullOrWhiteSpace($item.title)) { throw "title missing" }
        if ([string]::IsNullOrWhiteSpace($item.genres)) { throw "genres missing" }
    }
}

Invoke-Test -Name "Rating submit API" -Action {
    $body = @{
        userId  = $UserId
        movieId = $MovieId
        score   = $Score
    } | ConvertTo-Json
    $resp = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/rating/submit" -Method Post -ContentType "application/json" -Body $body -TimeoutSec 10
    if ($resp.code -ne 0) { throw "code != 0" }
}

Invoke-Test -Name "Recommend API after rating submit" -Action {
    $url = "http://127.0.0.1:8080/api/recommend/movie?userId=$UserId&topN=$TopN"
    $resp = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 20
    Assert-RecommendResponse -Response $resp -ExpectedTopN $TopN
}

if (-not $SkipLlmCheck) {
    Invoke-Test -Name "LLM query API" -Action {
        $body = @{
            userId    = $UserId
            queryText = $LlmQuery
        } | ConvertTo-Json
        $resp = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/llm/query" -Method Post -ContentType "application/json" -Body $body -TimeoutSec 30
        if ($resp.code -ne 0) { throw "code != 0" }
        if ([string]::IsNullOrWhiteSpace($resp.responseText)) { throw "responseText empty" }
    }
}

$passCount = @($results | Where-Object { $_.Passed }).Count
$failCount = @($results | Where-Object { -not $_.Passed }).Count

Write-Host ""
Write-Host "== Test Summary =="
$results | ForEach-Object {
    $flag = if ($_.Passed) { "PASS" } else { "FAIL" }
    Write-Host "[$flag] $($_.Name) - $($_.Detail)"
}
Write-Host "Total: $($results.Count), Passed: $passCount, Failed: $failCount"

if ($failCount -gt 0) {
    exit 1
}
exit 0
