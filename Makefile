.DEFAULT_GOAL := help

APP_NAME   := kios-mock-pos-service
GRADLE     := ./gradlew

.PHONY: help build run test clean docker-build

help:
	@echo "Usage: make <target>"
	@echo ""
	@echo "  build        Compile and package the application"
	@echo "  run          Run the Spring Boot application locally"
	@echo "  test         Run unit tests"
	@echo "  clean        Clean build artefacts"
	@echo "  docker-build Build Docker image"

build:
	$(GRADLE) bootJar

run:
	$(GRADLE) bootRun

test:
	$(GRADLE) test

clean:
	$(GRADLE) clean

docker-build: build
	docker build -t $(APP_NAME):latest .
