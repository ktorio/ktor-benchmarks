#!/bin/bash
# ABOUTME: Script to build all projects in the repository and verify they compile successfully.
# ABOUTME: This script builds each subproject independently using their own Gradle wrappers.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "================================================"
echo "Building all ktor-benchmarks projects"
echo "================================================"
echo ""

# Array to track results
declare -a RESULTS

# List of all project directories
PROJECTS=(
    "allocation-benchmark"
    "benchmark-server"
    "client-benchmarks"
    "io-benchmarks"
    "server-benchmarks"
    "utils-benchmarks"
)

# Function to build a project
build_project() {
    local project=$1
    echo -e "${YELLOW}Building $project...${NC}"

    cd "$SCRIPT_DIR/$project"

    if ./gradlew clean classes testClasses --no-daemon; then
        echo -e "${GREEN}✓ $project: BUILD SUCCESSFUL${NC}"
        RESULTS+=("$project:SUCCESS")
        return 0
    else
        echo -e "${RED}✗ $project: BUILD FAILED${NC}"
        RESULTS+=("$project:FAILED")
        return 1
    fi
}

# Build each project
FAILED_COUNT=0
for project in "${PROJECTS[@]}"; do
    echo ""
    echo "------------------------------------------------"

    if build_project "$project"; then
        :
    else
        FAILED_COUNT=$((FAILED_COUNT + 1))
    fi

    cd "$SCRIPT_DIR"
done

# Print summary
echo ""
echo "================================================"
echo "BUILD SUMMARY"
echo "================================================"

for result in "${RESULTS[@]}"; do
    project="${result%%:*}"
    status="${result##*:}"

    if [ "$status" = "SUCCESS" ]; then
        echo -e "${GREEN}✓ $project${NC}"
    else
        echo -e "${RED}✗ $project${NC}"
    fi
done

echo ""
if [ $FAILED_COUNT -eq 0 ]; then
    echo -e "${GREEN}All projects built successfully!${NC}"
    exit 0
else
    echo -e "${RED}$FAILED_COUNT project(s) failed to build.${NC}"
    exit 1
fi
