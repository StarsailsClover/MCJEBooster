param(
    [Parameter(Mandatory=$true)]
    [string]$Name,

    [Parameter(Mandatory=$true)]
    [string]$WorkDir,

    [string]$BootClassPath = '',
    [string]$JavaAgent = '',
    [int]$DurationSeconds = 120,
    [int]$SampleIntervalSeconds = 2,
    [int]$ForceloadRadius = 6,
    [int]$EntityCount = 800
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path $WorkDir
$stdout = Join-Path $root "$Name.stdout.log"
$stderr = Join-Path $root "$Name.stderr.log"
$result = Join-Path $root "$Name.result.json"
Remove-Item $stdout, $stderr, $result -Force -ErrorAction SilentlyContinue

$jvmArgs = @('-Xms1G', '-Xmx2G')
if ($BootClassPath -ne '') { $jvmArgs += "-Xbootclasspath/a:$BootClassPath" }
if ($JavaAgent -ne '') { $jvmArgs += "-javaagent:$JavaAgent" }
$jvmArgs += @('-jar', 'server.jar', 'nogui')

$psi = [System.Diagnostics.ProcessStartInfo]::new()
$psi.FileName = 'java'
$psi.Arguments = ($jvmArgs | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }) -join ' '
$psi.WorkingDirectory = $root.Path
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true

$p = [System.Diagnostics.Process]::new()
$p.StartInfo = $psi

$lines = [System.Collections.Concurrent.ConcurrentQueue[string]]::new()
$outWriter = [System.IO.StreamWriter]::new($stdout, $false, [System.Text.Encoding]::UTF8)
$errWriter = [System.IO.StreamWriter]::new($stderr, $false, [System.Text.Encoding]::UTF8)
$outWriter.AutoFlush = $true
$errWriter.AutoFlush = $true

$p.add_OutputDataReceived({
    param($sender, $eventArgs)
    if ($null -ne $eventArgs.Data) {
        $outWriter.WriteLine($eventArgs.Data)
        $lines.Enqueue($eventArgs.Data)
    }
})
$p.add_ErrorDataReceived({
    param($sender, $eventArgs)
    if ($null -ne $eventArgs.Data) {
        $errWriter.WriteLine($eventArgs.Data)
    }
})

Write-Host "[$Name] Starting: java $($psi.Arguments)"
[void]$p.Start()
$p.BeginOutputReadLine()
$p.BeginErrorReadLine()

$ready = $false
$readyDeadline = (Get-Date).AddSeconds(120)
while ((Get-Date) -lt $readyDeadline) {
    Start-Sleep -Milliseconds 500
    if ($p.HasExited) { break }
    foreach ($line in $lines.ToArray()) {
        if ($line -match 'Done \(') {
            $ready = $true
            break
        }
    }
    if ($ready) { break }
}

if (-not $ready) {
    Write-Host "[$Name] Server failed to become ready"
    if (-not $p.HasExited) { $p.Kill() }
    $outWriter.Dispose()
    $errWriter.Dispose()
    exit 1
}

function Send-ServerCommand([string]$Command) {
    Write-Host "[$Name] > $Command"
    $p.StandardInput.WriteLine($Command)
    $p.StandardInput.Flush()
    Start-Sleep -Milliseconds 80
}

Write-Host "[$Name] Preparing stress workload..."
Send-ServerCommand 'gamerule doMobSpawning false'
Send-ServerCommand 'gamerule doDaylightCycle false'
Send-ServerCommand 'gamerule randomTickSpeed 400'
Send-ServerCommand 'time set noon'
Send-ServerCommand 'difficulty easy'
Send-ServerCommand "forceload add -$ForceloadRadius -$ForceloadRadius $ForceloadRadius $ForceloadRadius"

# Create block update/random tick workload in the loaded area.
Send-ServerCommand 'fill -32 64 -32 32 64 32 minecraft:grass_block replace'
Send-ServerCommand 'fill -32 65 -32 32 65 32 minecraft:wheat[age=0] replace'

# Create entity tick workload distributed across several loaded chunks.
for ($i = 0; $i -lt $EntityCount; $i++) {
    $x = ($i % 64) - 32
    $z = ([math]::Floor($i / 64) % 64) - 32
    Send-ServerCommand "summon minecraft:cow $x 66 $z {NoAI:0b,Silent:1b}"
}

Send-ServerCommand 'say MCJEBooster stress workload ready'
Write-Host "[$Name] Collecting metrics for $DurationSeconds seconds..."

$start = Get-Date
$samples = @()
$deadline = $start.AddSeconds($DurationSeconds)
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
}

try {
    Send-ServerCommand 'stop'
    if (-not $p.WaitForExit(10000)) { $p.Kill() }
} finally {
    $outWriter.Dispose()
    $errWriter.Dispose()
}

$end = Get-Date
$logText = if (Test-Path $stdout) { Get-Content $stdout -Raw } else { '' }
$errText = if (Test-Path $stderr) { Get-Content $stderr -Raw } else { '' }
$cpuDelta = if ($samples.Count -ge 2) { $samples[-1].cpuSeconds - $samples[0].cpuSeconds } else { 0.0 }
$duration = [math]::Max(1.0, (($end - $start).TotalSeconds))
$avgCpuCores = [math]::Round($cpuDelta / $duration, 3)
$cantKeepUpCount = ([regex]::Matches($logText, "Can't keep up")).Count
$boosterLines = @()
if ($logText) { $boosterLines += [regex]::Matches($logText, '.*MCJEBooster.*') | ForEach-Object { $_.Value } }
if ($errText) { $boosterLines += [regex]::Matches($errText, '.*MCJEBooster.*') | ForEach-Object { $_.Value } }

$summary = [pscustomobject]@{
    name = $Name
    args = $jvmArgs
    durationSeconds = [math]::Round(($end - $start).TotalSeconds, 2)
    sampleCount = $samples.Count
    avgCpuCores = $avgCpuCores
    maxWorkingSetMB = if ($samples.Count) { ($samples | Measure-Object workingSetMB -Maximum).Maximum } else { $null }
    maxPrivateMB = if ($samples.Count) { ($samples | Measure-Object privateMB -Maximum).Maximum } else { $null }
    maxThreads = if ($samples.Count) { ($samples | Measure-Object threads -Maximum).Maximum } else { $null }
    cantKeepUpCount = $cantKeepUpCount
    boosterLineCount = $boosterLines.Count
    boosterLines = $boosterLines | Select-Object -First 30
    workload = [pscustomobject]@{
        forceloadRadius = $ForceloadRadius
        entityCount = $EntityCount
        randomTickSpeed = 400
    }
    samples = $samples
}

$summary | ConvertTo-Json -Depth 6 | Set-Content $result -Encoding UTF8
$summary | ConvertTo-Json -Depth 4
