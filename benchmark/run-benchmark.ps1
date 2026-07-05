param(
    [Parameter(Mandatory=$true)]
    [string]$Name,

    [Parameter(Mandatory=$true)]
    [string]$WorkDir,

    [string[]]$ExtraJvmArgs = @(),

    [string]$JavaAgent = '',

    [int]$WarmupSeconds = 90,

    [int]$SampleIntervalSeconds = 2
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path $WorkDir
$stdout = Join-Path $root "$Name.stdout.log"
$stderr = Join-Path $root "$Name.stderr.log"
$result = Join-Path $root "$Name.result.json"
$pidFile = Join-Path $root "$Name.pid"
Remove-Item $stdout, $stderr, $result, $pidFile -Force -ErrorAction SilentlyContinue

$agentArgs = @()
if ($JavaAgent -ne '') {
    $agentArgs += "-javaagent:$JavaAgent"
}
$args = @('-Xms1G', '-Xmx2G') + $agentArgs + $ExtraJvmArgs + @('-jar', 'server.jar', 'nogui')
$p = Start-Process java -ArgumentList $args -WorkingDirectory $root.Path -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
$p.Id | Set-Content $pidFile -Encoding ASCII

$start = Get-Date
$samples = @()
$doneAt = $null
$deadline = $start.AddSeconds($WarmupSeconds)

while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds $SampleIntervalSeconds
    if ($p.HasExited) { break }

    $proc = Get-Process -Id $p.Id -ErrorAction SilentlyContinue
    if ($proc) {
        $samples += [pscustomobject]@{
            seconds = [math]::Round(((Get-Date) - $start).TotalSeconds, 2)
            cpuSeconds = [math]::Round($proc.CPU, 3)
            workingSetMB = [math]::Round($proc.WorkingSet64 / 1MB, 1)
            privateMB = [math]::Round($proc.PrivateMemorySize64 / 1MB, 1)
            threads = $proc.Threads.Count
        }
    }

    if ($null -eq $doneAt -and (Test-Path $stdout)) {
        if (Select-String -Path $stdout -Pattern 'Done \(' -Quiet) {
            $doneAt = Get-Date
        }
    }
}

if (-not $p.HasExited) {
    Stop-Process -Id $p.Id -Force
    Start-Sleep -Seconds 2
}
$p.Refresh()
$end = Get-Date

$logText = if (Test-Path $stdout) { Get-Content $stdout -Raw } else { '' }
$errText = if (Test-Path $stderr) { Get-Content $stderr -Raw } else { '' }
$cantKeepUpCount = ([regex]::Matches($logText, "Can't keep up")).Count
$boosterLines = @()
if ($logText) { $boosterLines += [regex]::Matches($logText, '.*MCJEBooster.*') | ForEach-Object { $_.Value } }
if ($errText) { $boosterLines += [regex]::Matches($errText, '.*MCJEBooster.*') | ForEach-Object { $_.Value } }

$cpuDelta = 0.0
if ($samples.Count -ge 2) {
    $cpuDelta = $samples[-1].cpuSeconds - $samples[0].cpuSeconds
}
$duration = [math]::Max(1.0, (($end - $start).TotalSeconds))
$avgCpuCores = [math]::Round($cpuDelta / $duration, 3)

$summary = [pscustomobject]@{
    name = $Name
    args = $args
    exitCode = $p.ExitCode
    durationSeconds = [math]::Round(($end - $start).TotalSeconds, 2)
    startupSeconds = if ($doneAt) { [math]::Round(($doneAt - $start).TotalSeconds, 2) } else { $null }
    sampleCount = $samples.Count
    avgCpuCores = $avgCpuCores
    maxWorkingSetMB = if ($samples.Count) { ($samples | Measure-Object workingSetMB -Maximum).Maximum } else { $null }
    maxPrivateMB = if ($samples.Count) { ($samples | Measure-Object privateMB -Maximum).Maximum } else { $null }
    maxThreads = if ($samples.Count) { ($samples | Measure-Object threads -Maximum).Maximum } else { $null }
    cantKeepUpCount = $cantKeepUpCount
    boosterLineCount = $boosterLines.Count
    boosterLines = $boosterLines | Select-Object -First 30
    samples = $samples
}

$summary | ConvertTo-Json -Depth 6 | Set-Content $result -Encoding UTF8
$summary | ConvertTo-Json -Depth 4
