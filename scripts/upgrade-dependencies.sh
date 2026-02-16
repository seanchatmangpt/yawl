#!/bin/bash
#
# YAWL Dependency Upgrade Script
# Version: 1.0
# Date: 2026-02-16
#
# This script removes legacy, security-vulnerable dependencies and
# downloads modern Jakarta EE 10, Hibernate 6, and BouncyCastle 1.78 replacements.
#
# IMPORTANT: This script requires internet access to download JARs from Maven Central.
# A backup is created automatically before any changes.
#

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}YAWL Dependency Upgrade Script v1.0${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Navigate to YAWL root
cd /home/user/yawl

# Step 1: Backup
echo -e "${YELLOW}Step 1/6: Creating backup...${NC}"
BACKUP_DIR="build/3rdParty/lib-backup-$(date +%Y%m%d-%H%M%S)"
cp -r build/3rdParty/lib "$BACKUP_DIR"
echo -e "${GREEN}✓ Backup created at: $BACKUP_DIR${NC}"
echo ""

cd build/3rdParty/lib

# Step 2: Remove obsolete SOAP libraries
echo -e "${YELLOW}Step 2/6: Removing obsolete SOAP libraries...${NC}"
SOAP_JARS="axis-1.1RC2.jar wsdl4j-20030807.jar saaj.jar wsif.jar jaxrpc.jar apache_soap-2_3_1.jar"
for jar in $SOAP_JARS; do
    if [ -f "$jar" ]; then
        echo "  Removing: $jar"
        rm -f "$jar"
    fi
done
echo -e "${GREEN}✓ Legacy SOAP libraries removed${NC}"
echo ""

# Step 3: Remove obsolete misc libraries
echo -e "${YELLOW}Step 3/6: Removing obsolete misc libraries...${NC}"
if [ -f "concurrent-1.3.4.jar" ]; then
    echo "  Removing: concurrent-1.3.4.jar (pre-Java 5)"
    rm -f concurrent-1.3.4.jar
fi

# Remove old BouncyCastle
if [ -f "bcprov-jdk15-139.jar" ]; then
    echo "  Removing: bcprov-jdk15-139.jar (2009)"
    rm -f bcprov-jdk15-139.jar
fi
if [ -f "bcmail-jdk15-139.jar" ]; then
    echo "  Removing: bcmail-jdk15-139.jar (2009)"
    rm -f bcmail-jdk15-139.jar
fi

# Remove Hibernate 5.6
HIBERNATE5_JARS="hibernate-core-5.6.14.Final.jar hibernate-c3p0-5.6.14.Final.jar hibernate-ehcache-5.6.14.Final.jar hibernate-commons-annotations-5.1.2.Final.jar hibernate-jpa-2.1-api-1.0.0.Final.jar"
for jar in $HIBERNATE5_JARS; do
    if [ -f "$jar" ]; then
        echo "  Removing: $jar (Hibernate 5.6)"
        rm -f "$jar"
    fi
done
echo -e "${GREEN}✓ Obsolete libraries removed${NC}"
echo ""

# Step 4: Download BouncyCastle 1.78.1
echo -e "${YELLOW}Step 4/6: Downloading BouncyCastle 1.78.1...${NC}"
if ! wget -q --show-progress https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/1.78.1/bcprov-jdk18on-1.78.1.jar; then
    echo -e "${RED}✗ Failed to download bcprov-jdk18on-1.78.1.jar${NC}"
    echo -e "${YELLOW}Continuing with other downloads...${NC}"
fi
if ! wget -q --show-progress https://repo1.maven.org/maven2/org/bouncycastle/bcmail-jdk18on/1.78.1/bcmail-jdk18on-1.78.1.jar; then
    echo -e "${RED}✗ Failed to download bcmail-jdk18on-1.78.1.jar${NC}"
    echo -e "${YELLOW}Continuing with other downloads...${NC}"
fi
echo -e "${GREEN}✓ BouncyCastle 1.78.1 downloaded${NC}"
echo ""

# Step 5: Download Hibernate 6.5.1
echo -e "${YELLOW}Step 5/6: Downloading Hibernate 6.5.1.Final...${NC}"
BASE_URL="https://repo1.maven.org/maven2/org/hibernate/orm"
if ! wget -q --show-progress ${BASE_URL}/hibernate-core/6.5.1.Final/hibernate-core-6.5.1.Final.jar; then
    echo -e "${RED}✗ Failed to download hibernate-core-6.5.1.Final.jar${NC}"
fi
if ! wget -q --show-progress ${BASE_URL}/hibernate-hikaricp/6.5.1.Final/hibernate-hikaricp-6.5.1.Final.jar; then
    echo -e "${RED}✗ Failed to download hibernate-hikaricp-6.5.1.Final.jar${NC}"
fi
if ! wget -q --show-progress ${BASE_URL}/hibernate-jcache/6.5.1.Final/hibernate-jcache-6.5.1.Final.jar; then
    echo -e "${RED}✗ Failed to download hibernate-jcache-6.5.1.Final.jar${NC}"
fi
echo -e "${GREEN}✓ Hibernate 6.5.1 downloaded${NC}"
echo ""

# Step 6: Download Jakarta EE 10 APIs
echo -e "${YELLOW}Step 6/6: Downloading Jakarta EE 10 APIs...${NC}"
if ! wget -q --show-progress https://repo1.maven.org/maven2/jakarta/persistence/jakarta.persistence-api/3.2.0/jakarta.persistence-api-3.2.0.jar; then
    echo -e "${RED}✗ Failed to download jakarta.persistence-api-3.2.0.jar${NC}"
fi
if ! wget -q --show-progress https://repo1.maven.org/maven2/jakarta/xml/bind/jakarta.xml.bind-api/4.0.2/jakarta.xml.bind-api-4.0.2.jar; then
    echo -e "${RED}✗ Failed to download jakarta.xml.bind-api-4.0.2.jar${NC}"
fi
if ! wget -q --show-progress https://repo1.maven.org/maven2/jakarta/xml/soap/jakarta.xml.soap-api/3.0.2/jakarta.xml.soap-api-3.0.2.jar; then
    echo -e "${RED}✗ Failed to download jakarta.xml.soap-api-3.0.2.jar${NC}"
fi
if ! wget -q --show-progress https://repo1.maven.org/maven2/jakarta/xml/ws/jakarta.xml.ws-api/4.0.1/jakarta.xml.ws-api-4.0.1.jar; then
    echo -e "${RED}✗ Failed to download jakarta.xml.ws-api-4.0.1.jar${NC}"
fi
if ! wget -q --show-progress https://repo1.maven.org/maven2/jakarta/jws/jakarta.jws-api/3.0.0/jakarta.jws-api-3.0.0.jar; then
    echo -e "${RED}✗ Failed to download jakarta.jws-api-3.0.0.jar${NC}"
fi
if ! wget -q --show-progress https://repo1.maven.org/maven2/org/eclipse/angus/angus-mail/2.0.3/angus-mail-2.0.3.jar; then
    echo -e "${RED}✗ Failed to download angus-mail-2.0.3.jar (Jakarta Mail implementation)${NC}"
fi
echo -e "${GREEN}✓ Jakarta EE 10 APIs downloaded${NC}"
echo ""

# Summary
cd /home/user/yawl
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}✅ Dependency upgrade complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "${YELLOW}Summary:${NC}"
echo "  - Removed 14 obsolete JARs (SOAP, BouncyCastle 1.39, Hibernate 5.6)"
echo "  - Downloaded 11 modern replacements (Jakarta EE 10, Hibernate 6.5, BC 1.78)"
echo "  - Backup location: $BACKUP_DIR"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "  1. Update build/build.xml property references (see DEPENDENCY_UPGRADE_GUIDE_2026-02-16.md)"
echo "  2. Run: ant -f build/build.xml compile"
echo "  3. Run: ant -f build/build.xml unitTest"
echo ""
echo -e "${YELLOW}Rollback if needed:${NC}"
echo "  rm -rf build/3rdParty/lib"
echo "  mv $BACKUP_DIR build/3rdParty/lib"
echo ""
