# Event Publisher Application

## Description

The Event Publisher is a Spring Boot microservice that showcases event processing with real-time score updates for events like soccer matches. The application connects to a Node.js server that simulates match scores, fetches scores at regular intervals, and publishes them to Kafka for further processing. The application follows the outbox pattern to ensure reliable message delivery to Kafka, even in case of failures.

### Key Features

- Event management through REST endpoints
- Real-time score tracking from external Node.js server
- Kafka integration for event score publishing
- Outbox pattern implementation for reliable message delivery
- PostgreSQL database with Flyway migrations
- Scheduled tasks for periodic score fetching
- Virtual threads for efficient I/O operations
- Comprehensive test suite including unit, integration, and end-to-end tests

## Prerequisites

The following software must be installed on your system:

- Java 21+
- Maven 3.6+
- Node.js and npm
- Docker and Docker Compose

## Running the Application

### Using the Start Script

The easiest way to run the complete application is using the provided start script:

```bash
./scripts/start.sh
```

This script will:
1. Check for required dependencies (Java, Maven, Node.js, Docker)
2. Start Kafka and PostgreSQL using Docker Compose
3. Start the Node.js soccer server
4. Start the Spring Boot event publisher application

Once the script completes, you'll have the following services running:
- PostgreSQL: on port 5432
- Kafka: on port 9092
- Zookeeper: on port 2181
- Soccer Server: on http://localhost:8081
- Event Publisher: on http://localhost:8080

### Running Manually

If you prefer to run the components individually, follow these steps:

1. **Start the Docker Infrastructure**:
   ```bash
   cd event-publisher/ci
   docker-compose up -d
   ```
   This will start PostgreSQL, Kafka, and Zookeeper in the background.

2. **Wait for Kafka to be ready**:
   ```bash
   sleep 10
   ```

3. **Start the Soccer Server**:
   ```bash
   cd ../../soccer-server
   npm start
   ```
   This will start the Node.js server that simulates match scores.

4. **Start the Event Publisher Application**:
   ```bash
   cd ../event-publisher
   mvn spring-boot:run
   ```

5. **Create a Live Event** (in a new terminal):
   Once all services are running, you can create an event that will trigger score polling:
   ```bash
   curl -X POST http://localhost:8080/events \
     -H "Content-Type: application/json" \
     -d '{"eventId": "match-1", "status": "LIVE"}'
   ```
   This creates an event with ID "match-1" and sets its status to LIVE, which starts polling the soccer server for score updates.

6. **Update Event to NOT_LIVE** (when needed):
   To stop polling for a specific event, use:
   ```bash
   curl -X PUT http://localhost:8080/events/match-1 \
     -H "Content-Type: application/json" \
     -d '{"eventId": "match-1", "status": "NOT_LIVE"}'
   ```

### To Stop the Application

To stop all services:
1. Press `Ctrl+C` to stop the start script
2. Run `docker-compose down` in the `event-publisher` directory to stop Docker services

## Running Tests

### Unit Tests

To run only unit tests (excluding integration and functional tests):

```bash
mvn test
```

Note: By default, the Maven configuration skips unit tests. To run them explicitly, you can use:

```bash
mvn test -DskipTests=false
```

### All Tests (Unit + Integration + Functional)

To run all tests including integration and functional tests:

```bash
mvn verify
```

This command will execute all test types in the project, including:
- Unit tests (marked with `*Test.java` pattern)
- Integration tests (marked with `*FunctionalTest.java` and `*KafkaIntegrationTest.java` patterns)

## Architecture

The application consists of several components:

1. **Event Controller**: REST endpoints for managing events
2. **Event Service**: Business logic for event management
3. **Score Scheduler**: Scheduled tasks for fetching scores from external server
4. **Kafka Producer**: Publishes score updates to Kafka topics
5. **Outbox Pattern**: Ensures reliable message delivery to Kafka with retry logic
6. **Data Access Layer**: JDBI repositories for database operations
7. **Node.js Soccer Server**: Simulates match scores for events

## Configuration

The application is configured using Spring Boot's configuration mechanism. Key properties can be found in `application.properties` or can be overridden via environment variables.

The Kafka topic name is configurable, defaulting to `event-scores`.

## Docker Infrastructure

The application relies on the following Docker services:
- **PostgreSQL**: Persistence for events and outbox messages
- **Apache Kafka**: Message broker for publishing event scores
- **Zookeeper**: Coordination service for Kafka

The Docker configuration is located in `ci/docker-compose.yml`.

## Development log and AI usage

This project was built in an iterative process using qwen-code (https://github.com/QwenLM/qwen-code), a free CLI AI agent.

I started the project by using the spring initializer to create a new spring boot app. Then i cloned an existing
repository of mine, which was also a spring boot app with basic CRUD functionalities and a dockerized postgres database.
This repo had some design decisions that i wanted to use in this project as well. I started the qwen code cli
session in the parent folder and instructed it to scan both projects and then instructed it to start implementing
features in the event-publisher project, using the similar patterns and design decisions from the original repo.

My design decisions were to use spring boot with a dockerized postgres database for ease of deployment, as
well as to use JDBI for queries. Flyway for database migrations.

Some of the initial prompts i used can be found in the prompts.md file in the project root directory, while the
DEVELOPMENT_LOG.md is generated by qwen itself, which i instructed at the end of each session to document the changes
we made together.

After creating the initial database tables, services and models for events and messages, the next step was to create
an external service for simulating the score polling external api. I chose node and express since i think they are
great for such simple one file APIs, and node is omnipresent in most development configurations. Qwen was able to
generate the entire node application in one prompt, and it is indeed very useful for creating such mock APIs quickly.

Next step was to handle the scheduling of the calls to the score server. In my prompt, i instructed qwen to use spring
task scheduler as well as a concurrent hash map with scheduled futures since this was the approach i used most for such
tasks. I find it that the AI agent works best if you make implementation decisions and give a quick overview of the design
you have in mind. Later on i also added the virtual thread executor, so that the application can handle larger volume
of concurrent events in LIVE state.

After i confirmed that scores were being successfully fetched from the soccer server, the next step was to add kafka.
Since AI agents and humans as well handle the smaller incremental tasks the best, i first implemented just the
direct sending of scores to kafka as messages. Then i implemented the outbox pattern with saving of the messages to the
database for retry if needed.

Thorough the development process i noticed that as the project scope grew, the AI agent performance started decreasing.
As the context grows, the AI agent started getting slower, hallucinating more, as well as sliding into infinite loops of
changing the code back and forth between two non-valid solutions. So as the project grew i needed to implement fixes and
code manually more and more.

Once the basic functionality was implemented, the next step was writing tests. I started of with basic unit tests for
all the controllers and services. In this particular project there isn't a lot of application logic, so the more useful
tests are the integration and functional tests. Here i used testcontainers to create DAO tests, i consider these very
important when writing query annotations, since it is so easy to break these queries.
For kafka i didn't use testcontainers, and instead the use the actual kafka instance in the docker container.
Finally, the end-to-end test covers everything from the initial creation of the LIVE event to consuming the kafka messages
to confirm they have been produced, as well as querying the database to see that all the messages are in the correct
status.

In general, i prefer writing e2e tests using a separate application using guice and cucumber, but in this particular case
there was no need since there is no complex table data needed to initialize the process.

Since this project contains multiple components, the next step was to write a shell script that sets everything up
for ease of use.

I used this project as an opportunity to evaluate usage of AI agents in quick prototyping of multi component systems.
I was pleasantly surprised with how quick and seamless the process can be, however a lot of effort is needed to verify
and fix issues, and this effort starts growing exponentially as the project size grows. Therefore, it is my opinion that
AI agents are best used in experimenting and prototyping, with strict oversight and with certain design decisions forced
by the developer himself.