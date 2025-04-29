# Distributed Logging System

A distributed logging system implementing Raft consensus for fault-tolerant log replication across nodes, with a client interface for log submission and monitoring.

## Team Members

| Name                      | Registration Number | Email                      |
|---------------------------|--------------------|----------------------------|
| MENDIS M C R              | IT23173118         | IT23173118@my.sliit.lk     |
| LIYANAGE N.S.D            | IT23285606         | IT23285606@my.sliit.lk     |
| RUPASINGHE P W K W        | IT23283312         | IT23283312@my.sliit.lk     |
| RANASINGHE K W R L V      | IT23225510         | IT23225510@my.sliit.lk     |

## Tech Stack

- **Java 21**: Backend development
- **Spring Boot**: Framework for `eureka-server`, `log-node`, and `log-client`
- **MongoDB**: Log storage
- **Eureka**: Service discovery
- **HTML/CSS/JavaScript**: Front-end (Tailwind CSS, Axios)
- **IntelliJ IDEA**: IDE
- **Postman**: API testing
- **Maven**: Build tool
- **JUnit 5**: Testing framework
- **Embedded MongoDB**: Test database

## Prerequisites

- IntelliJ IDEA
- Java 21
- MongoDB
- Postman
- Maven
- Git

## Setup Instructions

1. **Clone Repository**:

   ```bash
   git clone https://github.com/ChanuRasmika/DistributedLoggingSystem.git
   ```

2. **Install Dependencies**:

   - Open `DistributedLoggingSystem` in IntelliJ.
   - Run `mvn clean install` in the root directory (covers `eureka-server`, `log-node`, `log-client`).

3. **Configure MongoDB**:

   - Start MongoDB locally (`mongod`) or via Docker:

     ```bash
     docker run -d -p 27017:27017 mongo
     ```

   - Ensure `logDb` database is accessible.

## Running the Prototype

### 1. Run Eureka Server

- **Module**: `eureka-server`
- **Port**: 8761
- **Steps**:
  - Open `eureka-server` in IntelliJ.
  - Run `com.example.eurekaserver.EurekaServerApplication`.
  - Verify at `http://localhost:8761`.

### 2. Configure Multiple `log-node` Instances

Run 4 `log-node` instances on ports 55051, 55052, 55053, 55054.

- **Edit Configuration in IntelliJ**:
  - For each instance:
    - Go to `Run > Edit Configurations > Add New > Spring Boot`.
    - Set `Main class`: `com.example.lognode.LogNodeApplication`.
    - Add VM Options: `-Dserver.port=55051` (repeat for 55052, 55053, 55054).
    - Name configurations: `LogNode-55051`, `LogNode-55052`, etc.
- **Run Instances**:
  - Start each configuration (`LogNode-55051`, `LogNode-55052`, etc.).
  - Verify registration at `http://localhost:8761`.

### 3. Run `log-client`

- **Module**: `log-client`
- **Port**: 8080
- **Steps**:
  - Open `log-client` in IntelliJ.
  - Run `com.example.logclient.LogClientApplication`.
  - Access front-end at `http://localhost:8080`.

## Using API Endpoints

Test APIs with Postman or `curl`.

- **Submit Log** (`log-client`):

  ```bash
  curl -X POST http://localhost:8080/send -H "Content-Type: application/json" -d '{"message":"Test log","level":"INFO"}'
  ```

- **Get Logs** (`log-node`):

  ```bash
  curl http://localhost:55051/api/logs
  ```

- **Check Raft State** (`log-node`):

  ```bash
  curl http://localhost:55051/api/raft/state
  ```

- **Check Node Status** (`log-node`):

  ```bash
  curl http://localhost:55051/api/nodes/status
  ```

## Monitoring

- **Ingestion Rates**: Check `LogSenderTest.testHighLoadSubmission` output (duration for 100 logs).
- **Replication Lag**: Compare submission vs. MongoDB save timestamps in tests.
- **Consistency**: Query `/api/logs` across nodes to verify identical logs.
- **Dynamic Joining**: Start a new `log-node` instance and check Eureka.

## Troubleshooting

- **Eureka Issues**: Ensure all nodes register at `http://localhost:8761`.
- **MongoDB Errors**: Verify `mongod` is running and `logDb` is accessible.
- **API Failures**: Check logs for `Failed to replicate log` or `PeerDiscoveryService` issues.
- **Test Failures**: Share `PeerDiscoveryService.java` or logs for debugging.
