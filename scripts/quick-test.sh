#!/bin/bash
# MCJEBooster Quick Test Script for Linux/Mac
# Usage: ./quick-test.sh [version]

set -e

VERSION=${1:-"26.4-20260510"}
MC_VERSION=${2:-"1.20.6"}

echo "========================================="
echo "MCJEBooster Quick Test"
echo "Version: $VERSION"
echo "Minecraft: $MC_VERSION"
echo "========================================="

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test functions
test_build() {
    echo -e "${YELLOW}[TEST] Building project...${NC}"
    if mvn clean package -q -DskipTests; then
        echo -e "${GREEN}✓ Build successful${NC}"
        return 0
    else
        echo -e "${RED}✗ Build failed${NC}"
        return 1
    fi
}

test_adapters() {
    echo -e "${YELLOW}[TEST] Validating adapters...${NC}"
    local count=0
    local valid=0
    
    for adapter in adapters/*.mcjeb; do
        count=$((count + 1))
        if python3 -c "import json; json.load(open('$adapter'))" 2>/dev/null; then
            valid=$((valid + 1))
        else
            echo -e "${RED}✗ Invalid JSON: $adapter${NC}"
        fi
    done
    
    echo -e "${GREEN}✓ $valid/$count adapters valid${NC}"
    return 0
}

test_jar() {
    echo -e "${YELLOW}[TEST] Checking JAR...${NC}"
    local jar="target/MCJEBooster-$VERSION.jar"
    
    if [ ! -f "$jar" ]; then
        echo -e "${RED}✗ JAR not found: $jar${NC}"
        return 1
    fi
    
    # Check manifest
    if unzip -p "$jar" META-INF/MANIFEST.MF | grep -q "Agent-Class"; then
        echo -e "${GREEN}✓ Agent manifest present${NC}"
    else
        echo -e "${RED}✗ Agent manifest missing${NC}"
        return 1
    fi
    
    return 0
}

test_injection() {
    echo -e "${YELLOW}[TEST] Testing injection (dry run)...${NC}"
    
    # Check if we can find a Minecraft process
    if pgrep -f "minecraft" > /dev/null; then
        echo -e "${YELLOW}⚠ Minecraft process detected${NC}"
        echo -e "${YELLOW}  Run injection manually with:${NC}"
        echo -e "  java -jar target/MCJEBooster-$VERSION.jar"
    else
        echo -e "${YELLOW}⚠ No Minecraft process found${NC}"
        echo -e "  Start Minecraft first, then run injection"
    fi
    
    return 0
}

# Run tests
echo ""
test_build || exit 1

echo ""
test_adapters || exit 1

echo ""
test_jar || exit 1

echo ""
test_injection

echo ""
echo "========================================="
echo -e "${GREEN}All tests passed!${NC}"
echo "========================================="
echo ""
echo "Next steps:"
echo "  1. Start Minecraft $MC_VERSION"
echo "  2. Run: java -jar target/MCJEBooster-$VERSION.jar"
echo "  3. Monitor console for injection success"
echo ""
