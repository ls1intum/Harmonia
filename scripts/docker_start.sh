#!/bin/bash

# Harmonia Startup Script
# This script starts all services using Docker containers

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}    Starting Harmonia Application${NC}"
echo -e "${BLUE}    (Containerized Version)${NC}"
echo -e "${BLUE}========================================${NC}\n"

# Step 1: Check Docker is running
echo -e "${GREEN}[1/4] Checking Docker...${NC}"
if ! docker info >/dev/null 2>&1; then
    echo -e "${RED}✗ Docker is not running. Please start Docker Desktop.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker is running${NC}\n"

# Step 2: Clean up any existing containers
echo -e "${GREEN}[2/4] Cleaning up existing containers...${NC}"
docker compose -f docker/docker-compose.yml down --remove-orphans 2>/dev/null || true
echo -e "${GREEN}✓ Cleanup completed${NC}\n"

# Step 3: Build and start all services
echo -e "${GREEN}[3/4] Building and starting all services...${NC}"
echo -e "${YELLOW}This may take several minutes for the first run...${NC}"
echo -e "${YELLOW}Building Docker images and starting containers...${NC}\n"

# Start services in background and capture output
docker compose -f docker/docker-compose.yml up --build -d

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ All services started successfully${NC}\n"
else
    echo -e "${RED}✗ Failed to start services${NC}"
    echo -e "${YELLOW}Check logs with: docker compose -f docker/docker-compose.yml logs${NC}"
    exit 1
fi

# Step 4: Wait for services to be ready
echo -e "${GREEN}[4/4] Waiting for services to be ready...${NC}"
echo -e "${YELLOW}Checking service health...${NC}"

# Wait for postgres to be healthy
echo -e "${YELLOW}• Waiting for PostgreSQL...${NC}"
timeout=60
while [ $timeout -gt 0 ]; do
    if docker compose -f docker/docker-compose.yml ps postgres | grep -q "healthy"; then
        echo -e "${GREEN}  ✓ PostgreSQL is ready${NC}"
        break
    fi
    sleep 2
    ((timeout-=2))
done

if [ $timeout -le 0 ]; then
    echo -e "${RED}  ✗ PostgreSQL failed to start within 60 seconds${NC}"
    exit 1
fi

# Wait for server to be responding
echo -e "${YELLOW}• Waiting for Spring Boot server...${NC}"
timeout=120
while [ $timeout -gt 0 ]; do
    if curl -f -s http://localhost:8080/actuator/health >/dev/null 2>&1; then
        echo -e "${GREEN}  ✓ Server is ready${NC}"
        break
    fi
    sleep 3
    ((timeout-=3))
done

if [ $timeout -le 0 ]; then
    echo -e "${YELLOW}  ⚠ Server may still be starting (timeout reached)${NC}"
fi

# Check if client is responding
echo -e "${YELLOW}• Waiting for React client...${NC}"
timeout=30
while [ $timeout -gt 0 ]; do
    if curl -f -s http://localhost:5173 >/dev/null 2>&1; then
        echo -e "${GREEN}  ✓ Client is ready${NC}"
        break
    fi
    sleep 2
    ((timeout-=2))
done

if [ $timeout -le 0 ]; then
    echo -e "${YELLOW}  ⚠ Client may still be starting (timeout reached)${NC}"
fi

# Summary
echo -e "\n${BLUE}========================================${NC}"
echo -e "${GREEN}✓ Harmonia is now running!${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "${YELLOW}Server:${NC} http://localhost:8080"
echo -e "${YELLOW}Client:${NC} http://localhost:5173"
echo -e "${YELLOW}Database:${NC} PostgreSQL on localhost:5432"
echo -e "\n${YELLOW}Useful commands:${NC}"
echo -e "${YELLOW}• View logs:${NC} docker compose -f docker/docker-compose.yml logs -f"
echo -e "${YELLOW}• Stop services:${NC} docker compose -f docker/docker-compose.yml down"
echo -e "${YELLOW}• Restart services:${NC} docker compose -f docker/docker-compose.yml restart"

