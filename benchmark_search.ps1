param(
    [switch]$Rerun = $true
)

$rerunFlag = if ($Rerun) { "--rerun-tasks" } else { "" }
./gradlew testDebugUnitTest --tests "com.seventv.stickers.benchmark.EmoteSearchBenchmarkTest" $rerunFlag | Out-Null

$xmlPath = "app/build/test-results/testDebugUnitTest/TEST-com.seventv.stickers.benchmark.EmoteSearchBenchmarkTest.xml"
if (Test-Path $xmlPath) {
    $xmlContent = Get-Content $xmlPath -Raw
    if ($xmlContent -match '<system-out><!\[CDATA\[([\s\S]*?)\]\]></system-out>') {
        $stdout = $matches[1]
        Write-Host $stdout
    } else {
        Write-Host "Benchmark XML found but no system-out extracted"
    }
} else {
    Write-Error "Benchmark test XML not found at $xmlPath"
}
