# MCJEBooster Test Runner
# PowerShell script for running all tests

param(
    [string]$TestCategory = "all",
    [switch]$Verbose = $false,
    [switch]$GenerateReport = $false
)

$ErrorActionPreference = "Stop"

$script:TestResults = @()
$script:Passed = 0
$script:Failed = 0
$script:Skipped = 0

function Write-TestHeader($text) {
    Write-Host "`n=========================================" -ForegroundColor Cyan
    Write-Host $text -ForegroundColor Cyan
    Write-Host "=========================================" -ForegroundColor Cyan
}

function Write-TestResult($testName, $result, $message = "") {
    $status = switch ($result) {
        "PASS" { "✓ PASS"; Green }
        "FAIL" { "✗ FAIL"; Red }
        "SKIP" { "⊘ SKIP"; Yellow }
        default { "? UNKNOWN"; Gray }
    }
    
    Write-Host "[$($status[0])] $testName" -ForegroundColor $status[1]
    if ($message -and $Verbose) {
        Write-Host "  $message" -ForegroundColor Gray
    }
    
    $script:TestResults += @{
        Name = $testName
        Result = $result
        Message = $message
        Timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    }
    
    switch ($result) {
        "PASS" { $script:Passed++ }
        "FAIL" { $script:Failed++ }
        "SKIP" { $script:Skipped++ }
    }
}

function Test-Prerequisites {
    Write-TestHeader "Phase 1: Prerequisites"
    
    # Check Java
    if (Get-Command java -ErrorAction SilentlyContinue) {
        $version = java -version 2>&1 | Select-String "version" | ForEach-Object { $_.ToString().Split('"')[1] }
        Write-TestResult "Java installed" "PASS" "Version: $version"
    } else {
        Write-TestResult "Java installed" "FAIL" "Java not found"
        return $false
    }
    
    # Check Maven
    if (Get-Command mvn -ErrorAction SilentlyContinue) {
        $version = mvn -version 2>&1 | Select-String "Apache Maven" | ForEach-Object { $_.ToString().Split()[2] }
        Write-TestResult "Maven installed" "PASS" "Version: $version"
    } else {
        Write-TestResult "Maven installed" "FAIL" "Maven not found"
        return $false
    }
    
    # Check project structure
    $requiredFiles = @("pom.xml", "src\main\java\com\mcjebooster\agent\MCJEBoosterAgent.java")
    foreach ($file in $requiredFiles) {
        if (Test-Path $file) {
            Write-TestResult "File exists: $file" "PASS"
        } else {
            Write-TestResult "File exists: $file" "FAIL" "File not found"
            return $false
        }
    }
    
    return $true
}

function Test-Compilation {
    Write-TestHeader "Phase 2: Compilation"
    
    Write-Host "Running Maven compile..." -ForegroundColor Yellow
    $output = mvn compile -q 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-TestResult "Maven compile" "PASS"
        return $true
    } else {
        Write-TestResult "Maven compile" "FAIL" $output
        return $false
    }
}

function Test-UnitTests {
    Write-TestHeader "Phase 3: Unit Tests"
    
    Write-Host "Running unit tests..." -ForegroundColor Yellow
    $output = mvn test -q 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        # Parse test results
        $testsRun = $output | Select-String "Tests run:"
        Write-TestResult "Unit tests execution" "PASS" $testsRun
        return $true
    } else {
        Write-TestResult "Unit tests execution" "FAIL" $output
        return $false
    }
}

function Test-Packaging {
    Write-TestHeader "Phase 4: Packaging"
    
    Write-Host "Running Maven package..." -ForegroundColor Yellow
    $output = mvn package -DskipTests -q 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        $jar = Get-ChildItem -Path "target" -Filter "MCJEBooster-*.jar" | Select-Object -First 1
        if ($jar) {
            Write-TestResult "JAR packaging" "PASS" "Created: $($jar.Name)"
            
            # Check JAR contents
            $manifest = jar -tf $jar.FullName | Select-String "MANIFEST.MF"
            if ($manifest) {
                Write-TestResult "JAR manifest" "PASS"
            } else {
                Write-TestResult "JAR manifest" "FAIL" "Manifest not found"
            }
            
            return $true
        } else {
            Write-TestResult "JAR packaging" "FAIL" "JAR not created"
            return $false
        }
    } else {
        Write-TestResult "JAR packaging" "FAIL" $output
        return $false
    }
}

function Test-Adapters {
    Write-TestHeader "Phase 5: Adapter Validation"
    
    if (-not (Test-Path "adapters")) {
        Write-TestResult "Adapters directory" "FAIL" "Directory not found"
        return $false
    }
    
    $adapters = Get-ChildItem -Path "adapters" -Filter "*.mcjeb"
    Write-TestResult "Adapter count" "PASS" "Found $($adapters.Count) adapters"
    
    $validCount = 0
    foreach ($adapter in $adapters) {
        try {
            $content = Get-Content $adapter.FullName -Raw | ConvertFrom-Json
            if ($content.adapterId -and $content.minecraftVersion) {
                $validCount++
                if ($Verbose) {
                    Write-Host "  ✓ $($adapter.Name)" -ForegroundColor Gray
                }
            } else {
                Write-TestResult "Adapter: $($adapter.Name)" "FAIL" "Missing required fields"
            }
        } catch {
            Write-TestResult "Adapter: $($adapter.Name)" "FAIL" "Invalid JSON"
        }
    }
    
    Write-TestResult "Valid adapters" "PASS" "$validCount / $($adapters.Count)"
    return $true
}

function Test-Security {
    Write-TestHeader "Phase 6: Security Checks"
    
    # Check for hardcoded credentials
    $files = Get-ChildItem -Path "src" -Recurse -Filter "*.java"
    $issues = @()
    
    foreach ($file in $files) {
        $content = Get-Content $file.FullName -Raw
        
        # Check for hardcoded passwords
        if ($content -match "password\s*=\s*[""'][^""']+[""']") {
            $issues += "Hardcoded password in $($file.Name)"
        }
        
        # Check for System.exit
        if ($content -match "System\.exit") {
            $issues += "System.exit found in $($file.Name)"
        }
        
        # Check for printStackTrace
        if ($content -match "printStackTrace\(\)") {
            $issues += "printStackTrace in $($file.Name)"
        }
    }
    
    if ($issues.Count -eq 0) {
        Write-TestResult "Security scan" "PASS"
        return $true
    } else {
        foreach ($issue in $issues) {
            Write-TestResult "Security: $issue" "FAIL"
        }
        return $false
    }
}

function Test-Documentation {
    Write-TestHeader "Phase 7: Documentation"
    
    $docs = @("README.md", "LICENSE", "TEST_PLAN.md")
    foreach ($doc in $docs) {
        if (Test-Path $doc) {
            $size = (Get-Item $doc).Length
            Write-TestResult "Documentation: $doc" "PASS" "$size bytes"
        } else {
            Write-TestResult "Documentation: $doc" "FAIL" "File not found"
        }
    }
    
    return $true
}

function Generate-Report {
    Write-TestHeader "Test Report"
    
    $total = $script:Passed + $script:Failed + $script:Skipped
    $passRate = if ($total -gt 0) { [math]::Round(($script:Passed / $total) * 100, 2) } else { 0 }
    
    Write-Host "Total Tests: $total" -ForegroundColor White
    Write-Host "Passed: $script:Passed" -ForegroundColor Green
    Write-Host "Failed: $script:Failed" -ForegroundColor Red
    Write-Host "Skipped: $script:Skipped" -ForegroundColor Yellow
    Write-Host "Pass Rate: $passRate%" -ForegroundColor $(if ($passRate -ge 80) { "Green" } elseif ($passRate -ge 60) { "Yellow" } else { "Red" })
    
    if ($GenerateReport) {
        $reportPath = "test-report-$((Get-Date -Format 'yyyyMMdd-HHmmss')).json"
        $report = @{
            Timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
            Summary = @{
                Total = $total
                Passed = $script:Passed
                Failed = $script:Failed
                Skipped = $script:Skipped
                PassRate = $passRate
            }
            Results = $script:TestResults
        }
        
        $report | ConvertTo-Json -Depth 3 | Out-File -FilePath $reportPath
        Write-Host "`nReport saved to: $reportPath" -ForegroundColor Green
    }
    
    return $script:Failed -eq 0
}

# Main execution
Write-Host "MCJEBooster Test Runner" -ForegroundColor Cyan
Write-Host "Category: $TestCategory`n" -ForegroundColor Gray

$success = $true

switch ($TestCategory) {
    "all" {
        $success = Test-Prerequisites
        if ($success) { $success = Test-Compilation }
        if ($success) { $success = Test-UnitTests }
        if ($success) { $success = Test-Packaging }
        if ($success) { Test-Adapters }
        if ($success) { Test-Security }
        if ($success) { Test-Documentation }
    }
    "unit" {
        Test-Prerequisites
        Test-Compilation
        Test-UnitTests
    }
    "integration" {
        Test-Prerequisites
        Test-Compilation
        Test-Packaging
    }
    "security" {
        Test-Security
    }
    "adapters" {
        Test-Adapters
    }
    default {
        Write-Error "Unknown test category: $TestCategory"
        exit 1
    }
}

$finalResult = Generate-Report

Write-Host "`n=========================================" -ForegroundColor Cyan
if ($finalResult) {
    Write-Host "ALL TESTS PASSED" -ForegroundColor Green
    exit 0
} else {
    Write-Host "SOME TESTS FAILED" -ForegroundColor Red
    exit 1
}
Write-Host "=========================================" -ForegroundColor Cyan
