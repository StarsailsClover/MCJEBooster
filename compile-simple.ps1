# MCJEBooster 简单编译脚本
# 不依赖 Maven，直接使用 javac 编译

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "MCJEBooster 编译脚本" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查 Java 版本
Write-Host "[1/6] 检查 Java 环境..." -ForegroundColor Yellow
$javaVersion = & javac -version 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "错误: 未找到 javac，请安装 JDK 17+" -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ $javaVersion" -ForegroundColor Green
Write-Host ""

# 创建输出目录
Write-Host "[2/6] 创建输出目录..." -ForegroundColor Yellow
Remove-Item -Recurse -Force "target/classes" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "target/classes" | Out-Null
Write-Host "  ✓ target/classes 已重建" -ForegroundColor Green
Write-Host ""

# 下载依赖
Write-Host "[3/6] 检查依赖库..." -ForegroundColor Yellow
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
        Write-Host "  下载: $($dep.Name)..." -ForegroundColor Cyan
        try {
            Invoke-WebRequest -Uri $dep.Url -OutFile $filePath -ErrorAction Stop
            Write-Host "    ✓ 下载完成" -ForegroundColor Green
        } catch {
            Write-Host "    ✗ 下载失败: $_" -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host "  ✓ $($dep.Name) 已存在" -ForegroundColor Green
    }
}
Write-Host ""

# 构建 classpath
$dependencyClasspath = ($dependencies | ForEach-Object { Join-Path $libDir $_.Name }) -join ";"
$classpath = "target/classes;$dependencyClasspath"
Write-Host "  Classpath: $classpath" -ForegroundColor DarkGray

# 编译顺序
Write-Host "[4/6] 编译源代码..." -ForegroundColor Yellow

$compileOrder = @(
    @{
        Name = "工具类"
        Files = @(
            "src/main/java/com/mcjebooster/util/Logger.java"
        )
    },
    @{
        Name = "适配器接口和类"
        Files = @(
            "src/main/java/com/mcjebooster/adapter/VersionAdapter.java",
            "src/main/java/com/mcjebooster/adapter/JsonVersionAdapter.java",
            "src/main/java/com/mcjebooster/adapter/AdapterLoader.java"
        )
    },
    @{
        Name = "反射和版本检测"
        Files = @(
            "src/main/java/com/mcjebooster/util/ReflectionHelper.java",
            "src/main/java/com/mcjebooster/util/VersionDetector.java"
        )
    },
    @{
        Name = "调度器和同步"
        Files = @(
            "src/main/java/com/mcjebooster/scheduler/RegionScheduler.java",
            "src/main/java/com/mcjebooster/sync/SyncPointManager.java",
            "src/main/java/com/mcjebooster/core/TaskScheduler.java"
        )
    },
    @{
        Name = "字节码转换器"
        Files = @(
            "src/main/java/com/mcjebooster/transformer/MinecraftServerTransformer.java"
        )
    },
    @{
        Name = "Agent 和注入器"
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
    Write-Host "  编译: $($group.Name)..." -ForegroundColor Cyan
    
    $existingFiles = $group.Files | Where-Object { Test-Path $_ }
    $totalFiles += $existingFiles.Count
    
    if ($existingFiles.Count -eq 0) {
        Write-Host "    ⚠ 没有找到文件，跳过" -ForegroundColor Yellow
        continue
    }
    
    $result = & javac -d target/classes -cp "$classpath" -encoding UTF-8 $existingFiles 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "    ✓ 编译成功 ($($existingFiles.Count) 个文件)" -ForegroundColor Green
        $compiledFiles += $existingFiles.Count
    } else {
        Write-Host "    ✗ 编译失败" -ForegroundColor Red
        Write-Host $result -ForegroundColor Red
        $failedFiles += $existingFiles.Count
    }
}

Write-Host ""
Write-Host "[5/6] 编译统计..." -ForegroundColor Yellow
Write-Host "  总文件数: $totalFiles" -ForegroundColor White
Write-Host "  成功: $compiledFiles" -ForegroundColor Green
Write-Host "  失败: $failedFiles" -ForegroundColor $(if ($failedFiles -gt 0) { "Red" } else { "Green" })
Write-Host ""

if ($failedFiles -gt 0) {
    Write-Host "编译失败，存在错误！" -ForegroundColor Red
    exit 1
}

# 创建 JAR
Write-Host "[6/6] 打包 JAR..." -ForegroundColor Yellow
$jarName = "MCJEBooster-26.6-20260706.jar"
$manifestContent = @"
Manifest-Version: 1.0
Agent-Class: com.mcjebooster.agent.MCJEBoosterAgent
Premain-Class: com.mcjebooster.agent.MCJEBoosterAgent
Can-Redefine-Classes: true
Can-Retransform-Classes: true
Can-Set-Native-Method-Prefix: true
Main-Class: com.mcjebooster.injector.InjectorMain
Implementation-Title: MCJEBooster
Implementation-Version: 26.6-20260706
Implementation-Vendor: StarsailsClover
"@

$manifestContent | Out-File -FilePath "target/MANIFEST.MF" -Encoding ASCII

# 复制依赖到 target/classes
foreach ($dep in $dependencies) {
    $srcPath = Join-Path $libDir $dep.Name
    Write-Host "  添加依赖: $($dep.Name)" -ForegroundColor Cyan
    
    # 解压 jar 到 target/classes
    $absoluteSrcPath = (Resolve-Path $srcPath).Path
    Push-Location target/classes
    & jar xf $absoluteSrcPath 2>&1 | Out-Null
    Pop-Location
}

# 打包
& jar cfm "target/$jarName" target/MANIFEST.MF -C target/classes . 2>&1

if ($LASTEXITCODE -eq 0) {
    Write-Host "  ✓ JAR 已创建: target/$jarName" -ForegroundColor Green
    
    # 复制适配器到 target/adapters
    if (Test-Path "adapters") {
        $targetAdapters = "target/adapters"
        Remove-Item -Recurse -Force $targetAdapters -ErrorAction SilentlyContinue
        Copy-Item -Recurse "adapters" $targetAdapters -Force
        Write-Host "  ✓ 适配器已复制到 target/adapters" -ForegroundColor Green
    }
} else {
    Write-Host "  ✗ JAR 创建失败" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "编译完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "输出文件: target/$jarName" -ForegroundColor White
Write-Host "文件大小: $((Get-Item "target/$jarName").Length / 1KB | ForEach-Object {"{0:N2}" -f $_}) KB" -ForegroundColor White
Write-Host ""
Write-Host "使用方法:" -ForegroundColor Yellow
Write-Host "  1. 作为 Java Agent (需要 -Xbootclasspath/a):" -ForegroundColor White
Write-Host "     java -Xbootclasspath/a:target/$jarName -javaagent:target/$jarName -jar minecraft_server.jar nogui" -ForegroundColor White
Write-Host "  2. 作为独立注入器: java -jar target/$jarName" -ForegroundColor White
Write-Host ""
