# MCJEBooster Simple Build Script
# Compile directly with javac, no Maven required

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "MCJEBooster Build Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check Java version
Write-Host "[1/6] Checking Java environment..." -ForegroundColor Yellow
$javaVersion = & javac -version 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: javac not found, please install JDK 17+" -ForegroundColor Red
    exit 1
}
Write-Host "  OK: $javaVersion" -ForegroundColor Green
Write-Host ""

# Create output directory
Write-Host "[2/6] Creating output directory..." -ForegroundColor Yellow
Remove-Item -Recurse -Force "target/classes" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "target/classes" | Out-Null
Write-Host "  OK: target/classes created" -ForegroundColor Green
Write-Host ""

# Download dependencies
Write-Host "[3/6] Checking dependencies..." -ForegroundColor Yellow
$libDir = "lib"
New-Item -ItemType Directory -Force -Path $libDir | Out-Null

$asmVersion = "9.6"
$jsonVersion = "20231013"

$dependencies = @(
    @{
        Name = "asm-$asmVersion.jar"
        Url = "https://repo1.maven.org/maven2/org/ow2/asm/asm/$asmVersion/asm-$asmVersion.jar"
    },
    @{
        Name = "asm-tree-$asmVersion.jar"
        Url = "https://repo1.maven.org/maven2/org/ow2/asm/asm-tree/$asmVersion/asm-tree-$asmVersion.jar"
    },
    @{
        Name = "asm-commons-$asmVersion.jar"
        Url = "https://repo1.maven.org/maven2/org/ow2/asm/asm-commons/$asmVersion/asm-commons-$asmVersion.jar"
    },
    @{
        Name = "json-$jsonVersion.jar"
        Url = "https://repo1.maven.org/maven2/org/json/json/$jsonVersion/json-$jsonVersion.jar"
    }
)

foreach ($dep in $dependencies) {
    $filePath = Join-Path $libDir $dep.Name
    if (!(Test-Path $filePath)) {
        Write-Host "  Downloading: $($dep.Name)..." -ForegroundColor Cyan
        try {
            Invoke-WebRequest -Uri $dep.Url -OutFile $filePath -ErrorAction Stop
            Write-Host "    OK: Downloaded" -ForegroundColor Green
        } catch {
            Write-Host "    FAIL: $_" -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host "  OK: $($dep.Name) exists" -ForegroundColor Green
    }
}
Write-Host ""

# Build classpath
$dependencyClasspath = ($dependencies | ForEach-Object { Join-Path $libDir $_.Name }) -join ";"
$classpath = "target/classes;$dependencyClasspath"
Write-Host "  Classpath: $classpath" -ForegroundColor DarkGray

# Compile source files
Write-Host "[4/6] Compiling sources..." -ForegroundColor Yellow

$compileOrder = @(
    @{
        Name = "Utilities"
        Files = @(
            "src/main/java/com/mcjebooster/util/Logger.java"
        )
    },
    @{
        Name = "Adapter interface and impl"
        Files = @(
            "src/main/java/com/mcjebooster/adapter/VersionAdapter.java",
            "src/main/java/com/mcjebooster/adapter/JsonVersionAdapter.java",
            "src/main/java/com/mcjebooster/adapter/AdapterLoader.java"
        )
    },
    @{
        Name = "Reflection and version detection"
        Files = @(
            "src/main/java/com/mcjebooster/util/ReflectionHelper.java",
            "src/main/java/com/mcjebooster/util/VersionDetector.java"
        )
    },
    @{
        Name = "Scheduler and sync"
        Files = @(
            "src/main/java/com/mcjebooster/scheduler/RegionScheduler.java",
            "src/main/java/com/mcjebooster/scheduler/HotspotTaskRegistry.java",
            "src/main/java/com/mcjebooster/scheduler/EntityDensityAnalyzer.java",
            "src/main/java/com/mcjebooster/scheduler/ChunkActivityPredictor.java",
            "src/main/java/com/mcjebooster/sync/SyncPointManager.java",
            "src/main/java/com/mcjebooster/core/TaskScheduler.java"
        )
    },
    @{
        Name = "Bytecode transformer"
        Files = @(
            "src/main/java/com/mcjebooster/transformer/MinecraftServerTransformer.java"
        )
    },
    @{
        Name = "Agent and injector"
        Files = @(
            "src/main/java/com/mcjebooster/agent/MCJEBoosterAgent.java",
            "src/main/java/com/mcjebooster/agent/InjectionBridge.java",
            "src/main/java/com/mcjebooster/injector/InjectorMain.java",
            "src/main/java/com/mcjebooster/update/UpdateManager.java"
        )
    },
    @{
        Name = "Benchmark"
        Files = @(
            "src/main/java/com/mcjebooster/benchmark/SchedulerMicroBenchmark.java"
        )
    }
)

$totalFiles = 0
$compiledFiles = 0
$failedFiles = 0

foreach ($group in $compileOrder) {
    Write-Host "  Compiling: $($group.Name)..." -ForegroundColor Cyan
    
    $existingFiles = $group.Files | Where-Object { Test-Path $_ }
    $totalFiles += $existingFiles.Count
    
    if ($existingFiles.Count -eq 0) {
        Write-Host "    WARN: No files found, skipping" -ForegroundColor Yellow
        continue
    }
    
    $result = & javac -d target/classes -cp "$classpath" -encoding UTF-8 $existingFiles 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "    OK: Compiled ($($existingFiles.Count) files)" -ForegroundColor Green
        $compiledFiles += $existingFiles.Count
    } else {
        Write-Host "    FAIL: Compilation error" -ForegroundColor Red
        Write-Host $result -ForegroundColor Red
        $failedFiles += $existingFiles.Count
    }
}

Write-Host ""
Write-Host "[5/6] Build stats..." -ForegroundColor Yellow
Write-Host "  Total files: $totalFiles" -ForegroundColor White
Write-Host "  Succeeded: $compiledFiles" -ForegroundColor Green
Write-Host "  Failed: $failedFiles" -ForegroundColor $(if ($failedFiles -gt 0) { "Red" } else { "Green" })
Write-Host ""

if ($failedFiles -gt 0) {
    Write-Host "Build failed with errors!" -ForegroundColor Red
    exit 1
}

# Create JAR
Write-Host "[6/6] Packaging JAR..." -ForegroundColor Yellow
$jarName = "MCJEBooster-26.6-20260714.jar"
$manifestContent = @"
Manifest-Version: 1.0
Agent-Class: com.mcjebooster.agent.MCJEBoosterAgent
Premain-Class: com.mcjebooster.agent.MCJEBoosterAgent
Can-Redefine-Classes: true
Can-Retransform-Classes: true
Can-Set-Native-Method-Prefix: true
Main-Class: com.mcjebooster.injector.InjectorMain
Implementation-Title: MCJEBooster
Implementation-Version: 26.6-20260714
Implementation-Vendor: StarsailsClover
"@

$manifestContent | Out-File -FilePath "target/MANIFEST.MF" -Encoding ASCII

# Copy dependencies into target/classes
foreach ($dep in $dependencies) {
    $srcPath = Join-Path $libDir $dep.Name
    Write-Host "  Adding dependency: $($dep.Name)" -ForegroundColor Cyan
    
    $absoluteSrcPath = (Resolve-Path $srcPath).Path
    Push-Location target/classes
    & jar xf $absoluteSrcPath 2>&1 | Out-Null
    Pop-Location
}

# Package
& jar cfm "target/$jarName" target/MANIFEST.MF -C target/classes . 2>&1

if ($LASTEXITCODE -eq 0) {
    Write-Host "  OK: JAR created: target/$jarName" -ForegroundColor Green
    
    # Copy adapters to target/adapters
    if (Test-Path "adapters") {
        $targetAdapters = "target/adapters"
        Remove-Item -Recurse -Force $targetAdapters -ErrorAction SilentlyContinue
        Copy-Item -Recurse "adapters" $targetAdapters -Force
        Write-Host "  OK: Adapters copied to target/adapters" -ForegroundColor Green
    }
} else {
    Write-Host "  FAIL: JAR creation failed" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Build complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Output: target/$jarName" -ForegroundColor White
Write-Host "Size: $((Get-Item "target/$jarName").Length / 1KB | ForEach-Object {"{0:N2}" -f $_}) KB" -ForegroundColor White
Write-Host ""
Write-Host "Usage:" -ForegroundColor Yellow
Write-Host "  1. As Java Agent (requires -Xbootclasspath/a):" -ForegroundColor White
Write-Host "     java -Xbootclasspath/a:target/$jarName -javaagent:target/$jarName -jar minecraft_server.jar nogui" -ForegroundColor White
Write-Host "  2. As standalone injector: java -jar target/$jarName" -ForegroundColor White
Write-Host ""
