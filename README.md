# Distributed Logging System

A distributed logging system implementing Raft consensus for fault-tolerant log replication across nodes, with a client interface for log submission and monitoring.

## Team Members

<table style="min-width: 75px">
<colgroup><col style="min-width: 25px"><col style="min-width: 25px"><col style="min-width: 25px"></colgroup><tbody><tr class="border-border"><th colspan="1" rowspan="1"><p dir="ltr">Name</p></th><th colspan="1" rowspan="1"><p dir="ltr">Registration Number</p></th><th colspan="1" rowspan="1"><p dir="ltr">Email</p></th></tr><tr class="border-border"><td colspan="1" rowspan="1"><p dir="ltr">MENDIS M C R</p></td><td colspan="1" rowspan="1"><p dir="ltr">IT23173118 </p></td><td colspan="1" rowspan="1"><p dir="ltr">IT23173118@my.sliit.lk</p></td></tr><tr class="border-border"><td colspan="1" rowspan="1"><p dir="ltr">LIYANAGE N.S.D</p></td><td colspan="1" rowspan="1"><p dir="ltr">IT23285606</p></td><td colspan="1" rowspan="1"><p dir="ltr">IT23285606@my.sliit.lk</p></td></tr><tr class="border-border"><td colspan="1" rowspan="1"><p dir="ltr">RUPASINGHE P W K W</p></td><td colspan="1" rowspan="1"><p dir="ltr">IT23283312 </p></td><td colspan="1" rowspan="1"><p dir="ltr">IT23283312@my.sliit.lk</p></td></tr><tr class="border-border"><td colspan="1" rowspan="1"><p dir="ltr">RANASINGHE K W R L V</p><p></p></td><td colspan="1" rowspan="1"><p dir="ltr">IT23225510</p></td><td colspan="1" rowspan="1"><p dir="ltr">IT23225510@my.sllit.lk</p></td></tr></tbody>
</table>

## Tech Stack

- **Java 21**: Backend development
- **Spring Boot**: Framework for `log-node` and `log-client`
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
   https://github.com/ChanuRasmika/DistributedLoggingSystem.git
   ```

2. **Install Dependencies**:

   - Open `logging-system` in IntelliJ.
   - Run `mvn clean install` for both modules.

3. **Configure MongoDB**:

   - Start MongoDB locally (`mongod`) or via Docker:

     ```bash
     docker run -d -p 27017:27017 mongo
     ```
   - Ensure `logDb` database is accessible.

4. **Configure Eureka**:

   - Start Eureka server (port 8761):

     ```bash
     cd eureka-server
     mvn spring-boot:run
     ```
   - Verify at `http://localhost:8761`.

## Running the Prototype

### Configure Multiple `log-node` Instances

Run 4 `log-node` instances on ports 55051, 55052, 55053, 55054.

1. **Edit Configuration in IntelliJ**:

   - For each instance:
     - Go to `Run > Edit Configurations > Add New > Spring Boot`.
     - Set `Main class`: `com.example.lognode.LogNodeApplication`.
     - Add Environment Variables options: `SERVER_PORT=55051` (repeat for 55052, 55053, 55054).
     - Name configurations: `LogNode-55051`, `LogNode-55052`, etc.

2. **Run** `log-node` **Instances**:

   - Start each configuration (`LogNode-55051`, `LogNode-55052`, etc.).
   - Verify registration at `http://localhost:8761`.

3. **Run** `log-client`:

   - Open `log-client` in IntelliJ.
   - Run `com.example.logclient.LogClientApplication` (default port: 8080).
   - Access front-end at `http://localhost:< SERVER_PORT>`. (SERVER_PORT - any port that you start the log-node)

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

## Running Tests

1. **Unit and Integration Tests**:

   - In `log-node` and `log-client`, run:

     ```bash
     mvn test
     ```
   - Tests include:
     - `RaftServiceTest`: Leader election, log replication.
     - `LogControllerTest`: Log submission/retrieval.
     - `LogSenderTest`: High-load submission, failover.
     - `ChaosTest`: Leader failure, network partitions.

2. **Chaos Testing**:

   - Manually kill a `log-node` instance (e.g., stop `LogNode-55051`).
   - Check reelection via `/api/raft/state`.
   - Simulate network delays in `ChaosTest` by modifying mocks.

## Monitoring

- **Ingestion Rates**: Check `LogSenderTest.testHighLoadSubmission` output (e.g., duration for 100 logs).
- **Replication Lag**: Compare submission vs. MongoDB save timestamps in tests.
- **Consistency**: Query `/api/logs` across nodes to verify identical logs.
- **Dynamic Joining**: Start a new `log-node` instance and check Eureka.

## Troubleshooting

- **Eureka Issues**: Ensure all nodes register at `http://localhost:8761`.
- **MongoDB Errors**: Verify `mongod` is running and `logDb` is accessible.
- **API Failures**: Check logs for `Failed to replicate log` or `PeerDiscoveryService` issues.
- **Test Failures**: Share `PeerDiscoveryService.java` or logs for debugging.

## 
