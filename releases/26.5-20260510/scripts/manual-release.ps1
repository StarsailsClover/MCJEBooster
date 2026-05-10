# MCJEBooster Manual Release Script
# Creates release structure without Maven build

param(
    [string]$Version = "26.5-20260510"
)

$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "MCJEBooster Manual Release Builder" -ForegroundColor Cyan
Write-Host "Version: $Version" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# Create release directory
$releaseDir = "releases\$Version"
Write-Host "`nCreating release directory: $releaseDir" -ForegroundColor Yellow
New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null

# Copy source files (for reference)
Write-Host "`nCopying source files..." -ForegroundColor Yellow
$srcDir = "$releaseDir\src"
New-Item -ItemType Directory -Path $srcDir -Force | Out-Null

# Copy main source
if (Test-Path "src\main\java\com\mcjebooster") {
    Copy-Item -Path "src\main\java\com\mcjebooster" -Destination "$srcDir\main\java\com\" -Recurse -Force
    Write-Host "  Copied main source" -ForegroundColor Green
}

# Copy test source
if (Test-Path "src\test\java\com\mcjebooster") {
    Copy-Item -Path "src\test\java\com\mcjebooster" -Destination "$srcDir\test\java\com\" -Recurse -Force
    Write-Host "  Copied test source" -ForegroundColor Green
}

# Copy adapters
Write-Host "`nCopying adapters..." -ForegroundColor Yellow
if (Test-Path "adapters") {
    $adapters = Get-ChildItem -Path "adapters" -Filter "*.mcjeb"
    foreach ($adapter in $adapters) {
        Copy-Item -Path $adapter.FullName -Destination "$releaseDir\$($adapter.Name)" -Force
    }
    Write-Host "  Copied $($adapters.Count) adapters" -ForegroundColor Green
}

# Copy documentation
Write-Host "`nCopying documentation..." -ForegroundColor Yellow
$docs = @(
    "README.md",
    "LICENSE",
    "CHANGELOG.md",
    "TEST_PLAN.md",
    "RELEASE_TEMPLATE.md",
    "Releases-v$Version.md",
    "pom.xml",
    ".gitignore"
)

foreach ($doc in $docs) {
    if (Test-Path $doc) {
        Copy-Item -Path $doc -Destination "$releaseDir\$doc" -Force
        Write-Host "  Copied $doc" -ForegroundColor Gray
    }
}

# Copy docs directory
if (Test-Path "docs") {
    Copy-Item -Path "docs" -Destination "$releaseDir\docs" -Recurse -Force
    Write-Host "  Copied docs/ directory" -ForegroundColor Gray
}

# Copy scripts
Write-Host "`nCopying scripts..." -ForegroundColor Yellow
if (Test-Path "scripts") {
    Copy-Item -Path "scripts" -Destination "$releaseDir\scripts" -Recurse -Force
    Write-Host "  Copied scripts/ directory" -ForegroundColor Gray
}

# Create README for release
$releaseReadme = @"
# MCJEBooster v$Version

## Quick Start

### For Users
1. Download the appropriate adapter for your Minecraft version
2. Run the injector: java -jar MCJEBooster-$Version.jar
3. Select your Minecraft process

### For Developers
1. Import as Maven project
2. Run: mvn clean package
3. Find built JAR in target/

## Files in this Release

- **Adapters/**: 30 .mcjeb adapter files for different Minecraft versions
- **src/**: Complete source code
- **docs/**: Documentation
- **scripts/**: Build and test scripts
- **pom.xml**: Maven configuration

## Support

See README.md for full documentation.
"@

$releaseReadme | Out-File -FilePath "$releaseDir\RELEASE_README.txt" -Encoding UTF8
Write-Host "  Created RELEASE_README.txt" -ForegroundColor Gray

# Generate checksums
Write-Host "`nGenerating checksums..." -ForegroundColor Yellow
$files = Get-ChildItem -Path $releaseDir -File
$checksums = @()
foreach ($file in $files) {
    $hash = Get-FileHash -Path $file.FullName -Algorithm SHA256
    $checksums += "$($hash.Hash)  $($file.Name)"
    "$($hash.Hash)  $($file.Name)" | Out-File -FilePath "$releaseDir\checksums.sha256" -Append
}
Write-Host "  Generated checksums.sha256" -ForegroundColor Green

# Create adapter list
Write-Host "`nCreating adapter list..." -ForegroundColor Yellow
$adapterList = @"
MCJEBooster Adapter List
Version: $Version
Generated: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
=========================================

"@

$adapters = Get-ChildItem -Path $releaseDir -Filter "*.mcjeb" | Sort-Object Name
foreach ($adapter in $adapters) {
    $hash = Get-FileHash -Path $adapter.FullName -Algorithm SHA256
    $adapterList += "$($adapter.Name)`n  SHA256: $($hash.Hash)`n`n"
}

$adapterList | Out-File -FilePath "$releaseDir\ADAPTER_LIST.txt" -Encoding UTF8
Write-Host "  Created ADAPTER_LIST.txt" -ForegroundColor Green

# Calculate sizes
$totalSize = (Get-ChildItem -Path $releaseDir -File | Measure-Object -Property Length -Sum).Sum
$sizeMB = [math]::Round($totalSize / 1MB, 2)

# Summary
Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host "Release Summary" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Version: $Version"
Write-Host "Location: $releaseDir"
Write-Host "Total Size: $sizeMB MB"
Write-Host "Files:"

Get-ChildItem -Path $releaseDir | ForEach-Object {
    if ($_.PSIsContainer) {
        $itemCount = (Get-ChildItem $_.FullName -Recurse | Measure-Object).Count
        Write-Host "  [DIR] $($_.Name) ($itemCount items)"
    } else {
        $size = if ($_.Length -gt 1MB) { 
            "{0:N2} MB" -f ($_.Length / 1MB) 
        } else { 
            "{0:N2} KB" -f ($_.Length / 1KB) 
        }
        Write-Host "  [FILE] $($_.Name) ($size)"
    }
}

Write-Host "`nAdapter Count: $($adapters.Count)" -ForegroundColor Green

# Create ZIP archives
Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host "Creating Archives" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# Full release ZIP
$fullZip = "releases\MCJEBooster-v$Version-Full.zip"
Write-Host "Creating full release archive..." -ForegroundColor Yellow
Compress-Archive -Path "$releaseDir\*" -DestinationPath $fullZip -Force
$fullSize = [math]::Round((Get-Item $fullZip).Length / 1MB, 2)
Write-Host "  Created: $fullZip ($fullSize MB)" -ForegroundColor Green

# Adapters-only ZIP
$adaptersZip = "releases\MCJEBooster-v$Version-Adapters.zip"
Write-Host "Creating adapters archive..." -ForegroundColor Yellow
$adapterFiles = Get-ChildItem -Path $releaseDir -Filter "*.mcjeb"
Compress-Archive -Path $adapterFiles -DestinationPath $adaptersZip -Force
$adaptersSize = [math]::Round((Get-Item $adaptersZip).Length / 1MB, 2)
Write-Host "  Created: $adaptersZip ($adaptersSize MB)" -ForegroundColor Green

# Source-only ZIP
$srcZip = "releases\MCJEBooster-v$Version-Source.zip"
Write-Host "Creating source archive..." -ForegroundColor Yellow
Compress-Archive -Path "$releaseDir\src" -DestinationPath $srcZip -Force
Compress-Archive -Path "$releaseDir\pom.xml" -Update -DestinationPath $srcZip
Compress-Archive -Path "$releaseDir\README.md" -Update -DestinationPath $srcZip
Compress-Archive -Path "$releaseDir\LICENSE" -Update -DestinationPath $srcZip
$srcSize = [math]::Round((Get-Item $srcZip).Length / 1MB, 2)
Write-Host "  Created: $srcZip ($srcSize MB)" -ForegroundColor Green

# Final summary
Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host "Release Complete!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Archives created:"
Write-Host "  1. $fullZip ($fullSize MB) - Full release with all files"
Write-Host "  2. $adaptersZip ($adaptersSize MB) - Adapters only"
Write-Host "  3. $srcZip ($srcSize MB) - Source code only"
Write-Host ""
Write-Host "Release directory: $releaseDir"
Write-Host ""
Write-Host "Next steps:"
Write-Host "  1. Build the JAR file with Maven: mvn clean package"
Write-Host "  2. Copy the built JAR to the release directory"
Write-Host "  3. Upload individual adapter files to GitHub Releases"
Write-Host "  4. Upload ZIP archives to GitHub Releases"
Write-Host "  5. Use Releases-v$Version.md as the release description"
Write-Host ""
