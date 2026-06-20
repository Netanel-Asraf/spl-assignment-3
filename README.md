# World Cup Informer System

## Overview
The World Cup Informer is a robust, distributed messaging system enabling community-led updates on ongoing soccer matches. Utilizing the STOMP (Simple Text-Oriented Messaging Protocol), the system allows clients to subscribe to specific game channels, report match events, and receive real-time updates from other participants. The implementation features a modular architecture with a C++ client and a high-performance Java-based server.

## Core Architecture
The system is built on a client-server model designed for scalability and protocol compliance.

* **STOMP Implementation:** Handles all client-server communication using the STOMP protocol, ensuring interoperability for subscription and messaging operations.
* **Server Infrastructure:** Implemented in Java, the server supports multiple concurrency models—Thread-Per-Client (TPC) for direct connection handling and a Reactor pattern for non-blocking asynchronous event handling.
* **Client Logic:** Implemented in C++, the client manages local event processing, connection handling, and message frame construction.
* **Event System:** Processes JSON-formatted game data, allowing users to report and consume dynamic match updates (e.g., goals, possession changes) in real-time.

## Build and Execution

### Prerequisites
* **Server:** Java (JDK) and Maven.
* **Client:** C++ compiler (g++), make build tool, and a Linux/Unix environment.

### Compilation
The project consists of two distinct components that must be built separately:

**Server:**
Navigate to the `server/` directory and use Maven:
```bash
mvn compile
```
## Client
Implemented in C++, the client manages local event processing, connection handling, and message frame construction.

## Execution
The server acts as the central hub for all STOMP communication, while clients connect to report and receive data.

## Running the Client
Once the server is active, launch the client to interact with the service:

```bash
./bin/StompClient <server_ip>:<port>
