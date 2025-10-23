#!/bin/bash

# start.sh - Script to start the complete event-publisher system

set -e  # Exit immediately if a command exits with a non-zero status

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

echo "Checking dependencies..."

# Check for required dependencies
MISSING_DEPS=()

if ! command_exists docker; then
    MISSING_DEPS+=("docker")
fi

if ! command_exists npm; then
    MISSING_DEPS+=("npm")
fi

if ! command_exists mvn; then
    MISSING_DEPS+=("mvn (Maven)")
fi

if ! command_exists java; then
    MISSING_DEPS+=("java")
fi

# If there are missing dependencies, print error and exit
if [ ${#MISSING_DEPS[@]} -ne 0 ]; then
    echo "Error: Missing required dependencies:"
    for dep in "${MISSING_DEPS[@]}"; do
        echo "  - $dep"
    done
    echo ""
    echo "Please install the missing dependencies and try again."
    exit 1
fi

echo "All dependencies are installed."

# Navigate to the event-publisher directory (assuming that's where docker-compose.yml is)
EVENT_PUBLISHER_DIR="/Users/jelena/Desktop/projects/event-publisher/event-publisher"

if [ ! -d "$EVENT_PUBLISHER_DIR" ]; then
    echo "Error: Directory $EVENT_PUBLISHER_DIR does not exist."
    exit 1
fi

cd "$EVENT_PUBLISHER_DIR"/ci
echo "Navigated to: $(pwd)"

# Start Docker Compose services (Kafka and Zookeeper)
echo "Starting Docker Compose services..."
docker-compose up -d

echo "Waiting for Kafka to start up..."
sleep 10

# Navigate to the soccer server directory and start it
SOCCER_SERVER_DIR="/Users/jelena/Desktop/projects/soccer-server"

if [ ! -d "$SOCCER_SERVER_DIR" ]; then
    echo "Error: Directory $SOCCER_SERVER_DIR does not exist."
    exit 1
fi

cd "$SOCCER_SERVER_DIR"
echo "Navigated to: $(pwd)"

echo "Starting soccer server in detached mode..."                                 │
nohup npm start > soccer-server.log 2>&1 &

# Wait a bit for the soccer server to start
sleep 3

# Navigate back to the event-publisher directory and start the Spring Boot app
cd "$EVENT_PUBLISHER_DIR"
echo "Navigated to: $(pwd)"

echo "Starting Spring Boot application..."
mvn spring-boot:run &

echo "All services started successfully!"
echo ""
echo "Services running:"
echo "- Kafka and Zookeeper: via Docker Compose"
echo "- Soccer Server: on http://localhost:8081"
echo "- Event Publisher: on http://localhost:8080"
echo ""
echo "To stop all services, use Ctrl+C and then run: docker-compose down in the event-publisher directory"