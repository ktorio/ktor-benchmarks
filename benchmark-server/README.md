# Benchmark Server

Gradle plugin providing reusable test server infrastructure for Ktor benchmarks.

## Overview

This module is a Gradle plugin that provides common server configurations, routes, and utilities used by other benchmark modules. It's not meant to be run standalone but rather as a dependency for other benchmark projects.

## Contents

### TestServer

A configurable Ktor server implementation that can be started with different engines for benchmark testing.

**Features:**
- Support for multiple server engines (CIO, Netty, Jetty, Tomcat)
- Pre-configured with WebSockets and Compression plugins
- Configurable port and host settings

### BenchmarkRoutes

Standard routes for benchmark testing:

- `GET /` - Serves a file response
- `GET /hello` - Returns "Hello, World!" text
- `GET /json` - Returns JSON response
- `GET /health` - Health check endpoint
- WebSocket `/echo` - Echo WebSocket for testing

### TestServerService

Service interface for managing server lifecycle in tests.

## Usage

This plugin is applied in other benchmark modules via:

```kotlin
plugins {
    id("test-server")
}
```

## Dependencies

- Ktor Server Core
- Ktor Server CIO
- Ktor Server WebSockets
- Ktor Server Compression

## Integration

Used by:
- `allocation-benchmark` - For allocation testing
- `client-benchmarks` - As the target server for client benchmarks
- Other benchmark modules requiring a test server

## Development

The plugin is implemented using Kotlin DSL in `src/main/kotlin/test-server.gradle.kts`.
