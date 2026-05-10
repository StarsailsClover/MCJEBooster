# MCJEBooster Release Build Script
# PowerShell script for building and packaging releases

param(
    [string]$Version = "26.4-20260510",
    [switch]$SkipTests = $false,
    [switch]$CreateRelease = $false
)

$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "MCJEBooster Release Builder" -ForegroundColor Cyan
Write-Host "Version: $Version" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# Check prerequisites
Write-Host "`nChecking prerequisites..." -ForegroundColor Yellow

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Error "Maven not found. Please install Maven 3.8+"
    exit 1
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "Java not found. Please install Java 17+"
    exit 1
}

$javaVersion = java -version 2>&1 | Select-String "version" | ForEach-Object { $_ -match '"(\d+)\.'; $matches[1] }
if ($javaVersion -lt 17) {
    Write-Warning "Java $javaVersion detected. Java 17+ recommended for building."
}

Write-Host "Prerequisites OK" -ForegroundColor Green

# Clean previous builds
Write-Host "`nCleaning previous builds..." -ForegroundColor Yellow
if (Test-Path "target") {
    Remove-Item -Path "target" -Recurse -Force
}
Write-Host "Clean complete" -ForegroundColor Green

# Build with Maven
Write-Host "`nBuilding with Maven..." -ForegroundColor Yellow
$mvnArgs = @("clean", "package")
if ($SkipTests) {
    $mvnArgs += "-DskipTests"
}

& mvn $mvnArgs
if ($LASTEXITCODE -ne 0) {
    Write-Error "Maven build failed"
    exit 1
}
Write-Host "Build successful" -ForegroundColor Green

# Find built JAR
$jarFile = Get-ChildItem -Path "target" -Filter "MCJEBooster-*.jar" | Select-Object -First 1
if (-not $jarFile) {
    Write-Error "Built JAR not found in target/"
    exit 1
}

Write-Host "Built JAR: $($jarFile.Name)" -ForegroundColor Green

# Create release directory
$releaseDir = "releases\$Version"
Write-Host "`nCreating release directory: $releaseDir" -ForegroundColor Yellow
New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null

# Copy main JAR
Copy-Item -Path $jarFile.FullName -Destination "$releaseDir\MCJEBooster-$Version.jar" -Force
Write-Host "Copied main JAR" -ForegroundColor Green

# Copy adapters
Write-Host "`nCopying adapters..." -ForegroundColor Yellow
if (Test-Path "adapters") {
    Copy-Item -Path "adapters\*.mcjeb" -Destination $releaseDir -Force
    $adapterCount = (Get-ChildItem -Path "$releaseDir\*.mcjeb").Count
    Write-Host "Copied $adapterCount adapters" -ForegroundColor Green
} else {
    Write-Warning "Adapters directory not found"
}

# Copy documentation
Write-Host "`nCopying documentation..." -ForegroundColor Yellow
$docs = @("README.md", "LICENSE", "TEST_PLAN.md", "docs\VERSION_MATRIX.md")
foreach ($doc in $docs) {
    if (Test-Path $doc) {
        Copy-Item -Path $doc -Destination $releaseDir -Force
        Write-Host "  Copied $doc" -ForegroundColor Gray
    }
}

# Create checksums
Write-Host "`nGenerating checksums..." -ForegroundColor Yellow
$files = Get-ChildItem -Path $releaseDir -File
foreach ($file in $files) {
    $hash = Get-FileHash -Path $file.FullName -Algorithm SHA256
    "$($hash.Hash)  $($file.Name)" | Out-File -FilePath "$releaseDir\checksums.sha256" -Append
}
Write-Host "Checksums generated" -ForegroundColor Green

# Create ZIP archive
Write-Host "`nCreating ZIP archive..." -ForegroundColor Yellow
$zipName = "MCJEBooster-$Version.zip"
Compress-Archive -Path "$releaseDir\*" -DestinationPath "releases\$zipName" -Force
Write-Host "Created $zipName" -ForegroundColor Green

# Calculate total size
$totalSize = (Get-ChildItem -Path $releaseDir -File | Measure-Object -Property Length -Sum).Sum
$sizeMB = [math]::Round($totalSize / 1MB, 2)

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host "Release Summary" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Version: $Version"
Write-Host "Location: $releaseDir"
Write-Host "Size: $sizeMB MB"
Write-Host "Files:"
Get-ChildItem -Path $releaseDir | ForEach-Object {
    $size = if ($_.Length -gt 1MB) { 
        "{0:N2} MB" -f ($_.Length / 1MB) 
    } else { 
        "{0:N2} KB" -f ($_.Length / 1KB) 
    }
    Write-Host "  $($_.Name) ($size)"
}

if ($CreateRelease) {
    Write-Host "`nCreating GitHub Release..." -ForegroundColor Yellow
    
    # Check if gh CLI is installed
    if (Get-Command gh -ErrorAction SilentlyContinue) {
        $releaseNotes = @"
# MCJEBooster $Version

## Changes
- Security audit fixes
- Null pointer protection
- Resource cleanup improvements
- Atomic operations for thread safety

## Included
- Core JAR
- 30 adapter packages
- Documentation

## Checksums
See checksums.sha256 in release archive.
"@
        
        gh release create $Version `
            --title "MCJEBooster $Version" `
            --notes $releaseNotes `
            "releases\$zipName" `
            "$releaseDir\checksums.sha256"
        
        Write-Host "GitHub Release created" -ForegroundColor Green
    } else {
        Write-Warning "GitHub CLI not found. Manual release required."
        Write-Host "Upload these files to GitHub Releases:" -ForegroundColor Yellow
        Write-Host "  - releases\$zipName"
        Write-Host "  - $releaseDir\checksums.sha256"
    }
}

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host "Build Complete!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan
