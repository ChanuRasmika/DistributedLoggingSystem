# DistributedLoggingSystem

Distributed Logging System
A distributed logging system implementing Raft consensus for fault-tolerant log replication across nodes, with a client interface for log submission and monitoring.
Team Members

NameRegistration NumberEmailMENDIS M C RIT23173118 IT23173118@my.sliit.lkLIYANAGE N.S.DIT23285606IT23285606@my.sliit.lkRUPASINGHE P W K WIT23283312 IT23283312@my.sliit.lkRANASINGHE K W R L VIT23225510IT23225510@my.sllit.lk


Tech Stack

Java 21: Backend development
Spring Boot: Framework for log-node and log-client
MongoDB: Log storage
Eureka: Service discovery
HTML/CSS/JavaScript: Front-end (Tailwind CSS, Axios)
IntelliJ IDEA: IDE
Postman: API testing
Maven: Build tool
JUnit 5: Testing framework
Embedded MongoDB: Test database

Prerequisites

IntelliJ IDEA
Java 21
MongoDB 
Postman
Maven
Git

Setup Instructions

Clone Repository:
https://github.com/ChanuRasmika/DistributedLoggingSystem.git


Install Dependencies:

Open logging-system in IntelliJ.
Run mvn clean install for both modules.


Configure MongoDB:

Start MongoDB locally (mongod) or via Docker:
docker run -d -p 27017:27017 mongo


Ensure logDb database is accessible.



Configure Eureka:

Start Eureka server (port 8761):
cd eureka-server
mvn spring-boot:run


Verify at http://localhost:8761.




Running the Prototype
Configure Multiple log-node Instances
Run 4 log-node instances on ports 55051, 55052, 55053, 55054.

Edit Configuration in IntelliJ:

For each instance:
Go to Run > Edit Configurations > Add New > Spring Boot.
Set Main class: com.example.lognode.LogNodeApplication.
Add Environment Variables options: SERVER_PORT=55051 (repeat for 55052, 55053, 55054).
Name configurations: LogNode-55051, LogNode-55052, etc.




Run log-node Instances:

Start each configuration (LogNode-55051, LogNode-55052, etc.).
Verify registration at http://localhost:8761.


Run log-client:

Open log-client in IntelliJ.
Run com.example.logclient.LogClientApplication (default port: 8080).
Access front-end at http://localhost:< SERVER_PORT>. (SERVER_PORT - any port that you start the log-node)



Using API Endpoints
Test APIs with Postman or curl.

Submit Log (log-client):
curl -X POST http://localhost:8080/send -H "Content-Type: application/json" -d '{"message":"Test log","level":"INFO"}'


Get Logs (log-node):
curl http://localhost:55051/api/logs


Check Raft State (log-node):
curl http://localhost:55051/api/raft/state


Check Node Status (log-node):
curl http://localhost:55051/api/nodes/status



Running Tests

Unit and Integration Tests:

In log-node and log-client, run:
mvn test


Tests include:

RaftServiceTest: Leader election, log replication.
LogControllerTest: Log submission/retrieval.
LogSenderTest: High-load submission, failover.
ChaosTest: Leader failure, network partitions.




Chaos Testing:

Manually kill a log-node instance (e.g., stop LogNode-55051).
Check reelection via /api/raft/state.
Simulate network delays in ChaosTest by modifying mocks.



Monitoring

Ingestion Rates: Check LogSenderTest.testHighLoadSubmission output (e.g., duration for 100 logs).
Replication Lag: Compare submission vs. MongoDB save timestamps in tests.
Consistency: Query /api/logs across nodes to verify identical logs.
Dynamic Joining: Start a new log-node instance and check Eureka.

Troubleshooting

Eureka Issues: Ensure all nodes register at http://localhost:8761.
MongoDB Errors: Verify mongod is running and logDb is accessible.
API Failures: Check logs for Failed to replicate log or PeerDiscoveryService issues.
Test Failures: Share PeerDiscoveryService.java or logs for debugging.




