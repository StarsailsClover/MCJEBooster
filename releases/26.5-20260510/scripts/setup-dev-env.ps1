# MCJEBooster Development Environment Setup
# PowerShell script for setting up development environment

param(
    [switch]$InstallPrerequisites = $false,
    [switch]$SkipGitConfig = $false
)

$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "MCJEBooster Dev Environment Setup" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

function Test-Command($command) {
    return $null -ne (Get-Command $command -ErrorAction SilentlyContinue)
}

function Get-JavaVersion {
    try {
        $output = java -version 2>&1
        $version = $output | Select-String "version" | ForEach-Object { 
            if ($_ -match '"(\d+)(?:\.\d+)?(?:\.\d+)?"') { 
                $matches[1] 
            } 
        } | Select-Object -First 1
        return [int]$version
    } catch {
        return 0
    }
}

# Phase 1: Check Prerequisites
Write-Host "`n[Phase 1] Checking Prerequisites..." -ForegroundColor Yellow

$prereqs = @{
    Java = @{ Installed = Test-Command "java"; Version = Get-JavaVersion; MinVersion = 17 }
    Maven = @{ Installed = Test-Command "mvn"; Version = 0; MinVersion = 3 }
    Git = @{ Installed = Test-Command "git"; Version = 0; MinVersion = 2 }
}

foreach ($tool in $prereqs.Keys) {
    $info = $prereqs[$tool]
    if ($info.Installed) {
        $status = "✓"
        $color = "Green"
        $msg = if ($info.Version -gt 0) { "v$($info.Version)" } else { "installed" }
        
        if ($info.Version -gt 0 -and $info.Version -lt $info.MinVersion) {
            $status = "⚠"
            $color = "Yellow"
            $msg += " (v$($info.MinVersion)+ recommended)"
        }
    } else {
        $status = "✗"
        $color = "Red"
        $msg = "not installed"
    }
    
    Write-Host "  $status $tool: $msg" -ForegroundColor $color
}

# Install prerequisites if requested
if ($InstallPrerequisites) {
    Write-Host "`n[Phase 1.5] Installing Prerequisites..." -ForegroundColor Yellow
    
    # Install Chocolatey if not present
    if (-not (Test-Command "choco")) {
        Write-Host "Installing Chocolatey..." -ForegroundColor Cyan
        Set-ExecutionPolicy Bypass -Scope Process -Force
        [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
        Invoke-Expression ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
    }
    
    # Install missing tools
    if (-not $prereqs.Java.Installed) {
        Write-Host "Installing OpenJDK 17..." -ForegroundColor Cyan
        choco install openjdk17 -y
    }
    
    if (-not $prereqs.Maven.Installed) {
        Write-Host "Installing Maven..." -ForegroundColor Cyan
        choco install maven -y
    }
    
    if (-not $prereqs.Git.Installed) {
        Write-Host "Installing Git..." -ForegroundColor Cyan
        choco install git -y
    }
    
    Write-Host "Prerequisites installed. Please restart PowerShell." -ForegroundColor Green
    exit 0
}

# Check if all required tools are installed
$missingTools = $prereqs.Keys | Where-Object { -not $prereqs[$_].Installed }
if ($missingTools) {
    Write-Host "`nMissing prerequisites: $($missingTools -join ', ')" -ForegroundColor Red
    Write-Host "Run with -InstallPrerequisites to install automatically" -ForegroundColor Yellow
    exit 1
}

# Phase 2: Git Configuration
if (-not $SkipGitConfig) {
    Write-Host "`n[Phase 2] Git Configuration..." -ForegroundColor Yellow
    
    $gitName = git config --global user.name 2>$null
    $gitEmail = git config --global user.email 2>$null
    
    if (-not $gitName) {
        $gitName = Read-Host "Enter your Git name"
        git config --global user.name "$gitName"
    }
    
    if (-not $gitEmail) {
        $gitEmail = Read-Host "Enter your Git email"
        git config --global user.email "$gitEmail"
    }
    
    Write-Host "  Git user: $gitName <$gitEmail>" -ForegroundColor Green
    
    # Configure Git hooks
    Write-Host "  Setting up Git hooks..." -ForegroundColor Gray
    
    $preCommitHook = @'
#!/bin/sh
# Pre-commit hook for MCJEBooster

echo "Running pre-commit checks..."

# Check for hardcoded credentials
if grep -r "password.*=" src/ --include="*.java" | grep -v "// " | grep -v "getPassword"; then
    echo "ERROR: Potential hardcoded password found"
    exit 1
fi

# Run tests
mvn test -q
if [ $? -ne 0 ]; then
    echo "ERROR: Tests failed"
    exit 1
fi

echo "Pre-commit checks passed"
'@
    
    $hooksDir = ".git\hooks"
    if (Test-Path $hooksDir) {
        $preCommitHook | Out-File -FilePath "$hooksDir\pre-commit" -Encoding UTF8 -NoNewline
        # Convert to Unix line endings
        (Get-Content "$hooksDir\pre-commit" -Raw).Replace("`r`n", "`n") | Set-Content "$hooksDir\pre-commit" -NoNewline
        Write-Host "  Pre-commit hook installed" -ForegroundColor Green
    }
}

# Phase 3: Maven Configuration
Write-Host "`n[Phase 3] Maven Configuration..." -ForegroundColor Yellow

$mavenSettingsDir = "$env:USERPROFILE\.m2"
if (-not (Test-Path $mavenSettingsDir)) {
    New-Item -ItemType Directory -Path $mavenSettingsDir | Out-Null
}

# Check if settings.xml exists
if (-not (Test-Path "$mavenSettingsDir\settings.xml")) {
    $mavenSettings = @'
<settings>
  <localRepository>$'{user.home}/.m2/repository</localRepository>
  <interactiveMode>true</interactiveMode>
  <offline>false</offline>
</settings>
'@
    $mavenSettings | Out-File -FilePath "$mavenSettingsDir\settings.xml" -Encoding UTF8
    Write-Host "  Created Maven settings.xml" -ForegroundColor Green
}

# Phase 4: IDE Setup
Write-Host "`n[Phase 4] IDE Setup..." -ForegroundColor Yellow

$ides = @{
    "IntelliJ IDEA" = "idea64.exe"
    "Eclipse" = "eclipse.exe"
    "VS Code" = "code.exe"
}

$detectedIdes = @()
foreach ($ide in $ides.Keys) {
    if (Get-Command $ides[$ide] -ErrorAction SilentlyContinue) {
        $detectedIdes += $ide
    }
}

if ($detectedIdes.Count -gt 0) {
    Write-Host "  Detected IDEs: $($detectedIdes -join ', ')" -ForegroundColor Green
} else {
    Write-Host "  No IDEs detected" -ForegroundColor Yellow
    Write-Host "  Recommended: IntelliJ IDEA Community Edition" -ForegroundColor Gray
}

# Generate IDE configurations
if (Test-Command "idea64.exe") {
    Write-Host "  Generating IntelliJ IDEA project files..." -ForegroundColor Gray
    mvn idea:idea -q 2>$null
}

# Phase 5: Build Verification
Write-Host "`n[Phase 5] Build Verification..." -ForegroundColor Yellow

Write-Host "  Running initial build..." -ForegroundColor Gray
$mvnOutput = mvn clean compile -q 2>&1

if ($LASTEXITCODE -eq 0) {
    Write-Host "  ✓ Build successful" -ForegroundColor Green
} else {
    Write-Host "  ✗ Build failed" -ForegroundColor Red
    Write-Host "  $mvnOutput" -ForegroundColor Gray
    exit 1
}

# Phase 6: Test Execution
Write-Host "`n[Phase 6] Running Tests..." -ForegroundColor Yellow

$mvnOutput = mvn test -q 2>&1

if ($LASTEXITCODE -eq 0) {
    Write-Host "  ✓ Tests passed" -ForegroundColor Green
} else {
    Write-Host "  ⚠ Some tests failed (this may be normal for initial setup)" -ForegroundColor Yellow
}

# Phase 7: Documentation
Write-Host "`n[Phase 7] Documentation..." -ForegroundColor Yellow

$docs = @("README.md", "TEST_PLAN.md", "docs\VERSION_MATRIX.md")
foreach ($doc in $docs) {
    if (Test-Path $doc) {
        Write-Host "  ✓ $doc" -ForegroundColor Green
    } else {
        Write-Host "  ⚠ $doc not found" -ForegroundColor Yellow
    }
}

# Phase 8: Summary
Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host "Setup Complete!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan

Write-Host "`nNext steps:" -ForegroundColor White
Write-Host "  1. Import project into your IDE" -ForegroundColor Gray
Write-Host "  2. Review TEST_PLAN.md for testing procedures" -ForegroundColor Gray
Write-Host "  3. Run scripts\run-tests.ps1 to execute tests" -ForegroundColor Gray
Write-Host "  4. Run scripts\build-release.ps1 to create releases" -ForegroundColor Gray

Write-Host "`nUseful commands:" -ForegroundColor White
Write-Host "  mvn clean package    - Build project" -ForegroundColor Gray
Write-Host "  mvn test             - Run tests" -ForegroundColor Gray
Write-Host "  mvn clean            - Clean build" -ForegroundColor Gray

Write-Host "`nHappy coding!" -ForegroundColor Cyan
