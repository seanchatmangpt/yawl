#!/bin/bash
set -e

# Wait for Cloud SQL Proxy to be ready
echo "Waiting for database connection..."
for i in {1..30}; do
    if pg_isready -h $YAWL_DB_HOST -p $YAWL_DB_PORT -U $YAWL_DB_USER; then
        echo "Database is ready!"
        break
    fi
    echo "Attempt $i: Database not ready, waiting..."
    sleep 2
done

# Set Tomcat heap size
export CATALINA_OPTS="-Xms512m -Xmx${YAWL_HEAP_SIZE:-1024m} -XX:+UseG1GC"

# Start Tomcat
/usr/local/tomcat/bin/catalina.sh run
