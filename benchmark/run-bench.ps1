param(
    [string]$Name,
    [string]$WorkDir,
    [string]$BootClassPath = '',
    [string]$JavaAgent = '',
    [int]$DurationSeconds = 90
)

$ErrorActionPreference = 'Stop'
$stdout = Join-Path $WorkDir "$Name.stdout.log"
$stderr = Join-Path $WorkDir "$Name.stderr.log"
Remove-Item $stdout, $stderr -Force -ErrorAction SilentlyContinue

$jvmArgs = @('-Xms1G', '-Xmx2G')
if ($BootClassPath -ne '') { $jvmArgs += "-Xbootclasspath/a:$BootClassPath" }
if ($JavaAgent -ne '') { $jvmArgs += "-javaagent:$JavaAgent" }
$jvmArgs += @('-jar', 'server.jar', 'nogui')

Write-Host "[$Name] Starting: java $jvmArgs"
$proc = Start-Process java -ArgumentList $jvmArgs -WorkingDirectory $WorkDir -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
$procId = $proc.Id

# Wait for server to be ready
$ready = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 2
    if (Test-Path $stdout) {
        if (Select-String -Path $stdout -Pattern 'Done \(' -Quiet) {
            $ready = $true
            break
        }
    }
    if ($proc.HasExited) { break }
}

if (-not $ready) {
    Write-Host "[$Name] Server failed to start in time"
    if (-not $proc.HasExited) { Stop-Process -Id $procId -Force }
    return
}

Write-Host "[$Name] Server ready, collecting metrics..."

# Collect metrics
$start = Get-Date
$samples = @()
$deadline = $start.AddSeconds($DurationSeconds)

while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    $p = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if (-not $p) { break }
    $samples += [pscustomobject]@{
        seconds = [math]::Round(((Get-Date) - $start).TotalSeconds, 2)
        cpuSeconds = [math]::Round($p.CPU, 3)
        workingSetMB = [math]::Round($p.WorkingSet64 / 1MB, 1)
        threads = $p.Threads.Count
    }
}

Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
$end = Get-Date

$cpuDelta = if ($samples.Count -ge 2) { $samples[-1].cpuSeconds - $samples[0].cpuSeconds } else { 0 }
$duration = [math]::Max(1.0, ($end - $start).TotalSeconds)
$avgCpu = [math]::Round($cpuDelta / $duration, 3)
$maxMem = if ($samples.Count) { ($samples | Measure-Object workingSetMB -Maximum).Maximum } else { 0 }
$maxThreads = if ($samples.Count) { ($samples | Measure-Object threads -Maximum).Maximum } else { 0 }
$logText = if (Test-Path $stdout) { Get-Content $stdout -Raw } else { '' }
$cantKeepUp = ([regex]::Matches($logText, "Can't keep up")).Count

Write-Host "=== $Name RESULTS ==="
Write-Host "Duration: $([math]::Round(($end - $start).TotalSeconds, 2))s"
Write-Host "Avg CPU: $avgCpu cores"
Write-Host "Max Memory: $maxMem MB"
Write-Host "Max Threads: $maxThreads"
Write-Host "Can't keep up: $cantKeepUp"
Write-Host "Samples: $($samples.Count)"