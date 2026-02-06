# React + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Babel](https://babeljs.io/) (or [oxc](https://oxc.rs) when used in [rolldown-vite](https://vite.dev/guide/rolldown)) for Fast Refresh
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/) for Fast Refresh

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and [`typescript-eslint`](https://typescript-eslint.io) in your project.




Project Phoenix Walkthrough
Setup & Infrastructure
We have successfully set up the "Bridge" infrastructure using Docker Compose:

Legacy Source: PostgreSQL 15 (Port 5432)
The Bridge: Debezium Connect + Kafka + Zookeeper
Modern Destination: Weaviate Vector DB (Port 8090)
Local AI (Optional): Ollama (Port 11434)
Gemini (Optional): Google Vertex AI
Observability: Jaeger (Port 16686)
Full Stack Deployment (Docker)
The entire application is now containerized and can be started with a single command:

docker-compose up -d --build
Services
1. Spring Boot Service (Backend)
Container: phoenix-phoenix-backend-1
Port: 8080 (Mapped from 8080)
Features: REST API, Kafka CDC Consumer, Multi-Model AI (Dynamic Switching).
Swagger UI: http://localhost:8080/swagger-ui.html
Note: Requires OPENAI_API_KEY for OpenAI mode.
2. React UI (Frontend)
Container: phoenix-phoenix-frontend-1
Port: 5173 (Mapped from 80 via Nginx)
URL: http://localhost:5173
Features: Dashboard, AI Toggles, Simulated Input, Embedded Jaeger Traces.
Multi-Model AI Support
You can switch between OpenAI, Ollama, and Gemini using the toggle in the UI:

OpenAI (Default): Uses GPT-4o.
Ollama: Uses local models (e.g., Llama 3).
Gemini: Uses Google Vertex AI (requires GEMINI_PROJECT_ID, GEMINI_LOCATION, and authenticated gcloud or credentials).
Verification Steps
1. Infrastructure Check
Ensure all containers are up: docker-compose ps

2. Run the App
Open the UI at http://localhost:5173.

Select AI Model: Toggle between OpenAI, Ollama, and Gemini in the top right.
Simulate Claim: Enter a raw claim description in the "Simulate Legacy Input" box and click submit.
Example: "Customer reported water leak in kitchen under the sink. Plumber called to fix pipe."
View Result: The claim will appear in the "Processed Claims" list.
It will start as PENDING.
Once processed by the backend (listening to CDC events), it will update to ENRICHED with an AI Insight.
4. Trace the Request
Scroll to the bottom of the UI to the End-to-End Traces section.

You will see the End-to-End Traces dashboard (embedded Jaeger UI).
Look for traces from phoenix-service.
Click on a trace to see the full timeline:
Kafka Receive -> Virtual Thread Start -> AI Model Call -> Vector Store -> DB Update.
5. Artifacts Created.
docker-compose.yml
: Infrastructure.
phoenix-service/: Java 25 Spring Boot Service.
phoenix-ui/: React + Tailwind Dashboard.
init-db.sh
 & 
setup-debezium.sh
: Setup scripts.
4. Jaeger Observability (Verified!) ✅
Infrastructure: Jaeger collector is receiving traces via OTLP (Port 4318).
Instrumentation: Both the REST API (claim.creation) and the background enrichment (claim.enrichment) are traced.
Trace Context: Traces successfully propagate into Virtual Threads, providing a complete view from HTTP request to database update.
UI Integration: The Jaeger dashboard is embedded directly into the React app for real-time monitoring.
Final Verification Results (Verified!) ✅
I have successfully verified the complete end-to-end CDC pipeline using local AI (Ollama).

Test Case Summary:

Insert: A new claim was added to the legacy-db.
CDC: Debezium captured the insertion and produced a message to the Kafka topic.
AI Processing: The phoenix-backend consumed the message, sent it to Ollama for enrichment (llama3), and generated an embedding (mxbai-embed-large).
Conclusion: The summary column in the legacy database was updated automatically, and the vector was stored in Weaviate.
Proof of Life (DB result for Claim #12):

id |                         description                         |                                                                                                  summary                                                                                                   
----+-------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 12 | Fire damage in the kitchen caused by a toaster malfunction. | The insured is claiming reimbursement for damages...






Project Phoenix Task List
 Phase 1: Infrastructure (Docker Ecosystem)
 Create 
docker-compose.yml
 with PostgreSQL, Zookeeper, Kafka, Debezium, and Weaviate
 Update 
docker-compose.yml
 to include Ollama
 Add Schema Registry to 
docker-compose.yml
 Verify Docker environment startup
 Phase 2: Modern Sync Service (Java 25 Spring Boot)
 Initialize Spring Boot 3.4+ project (Java 25, Spring AI, Kafka, Web)
 Add Ollama dependency to 
pom.xml
 Configure Profiles (
application-openai.properties
, 
application-ollama.properties
)
 Add Gemini Support (Dependency & Config)
 Refactor for Dynamic AI Switching (Controller & Service)
 Create Debezium connector configuration script
 Implement 
ClaimModernizationService
 using Virtual Threads and Spring AI
 Verify application logic with both profiles
 Phase 3: React UI
 Initialize React App (Vite + Tailwind)
 Implement Backend API usage (Claims & Config)
 Update Dashboard UI to include Gemini option
 Phase 4: Observability (Jaeger)
 Core Tracing Dependencies (Micrometer + Otel)
 Configure OTel Exporter for Jaeger
 Instrument Asynchronous Processing (Virtual Threads)
 Add Trace ID to Response Headers
 UI integration (Links/Dashboard)
 Enable System Architecture (Dependency Graph)
 Enable Service Performance Monitoring (Monitor Tab)
 Upgrade Kafka to support Telemetry
 Phase 5: Documentation & API Docs
 Add Swagger UI for API Documentation
 Refactor: Default Ollama, Port 8080, Robust Startup
 Containerize Frontend and Backend
 Phase 6: Logging Refinement
 Implement SLF4J logging in Service layer
 Implement SLF4J logging in Controller layer
 Component logging for AI and Vector Store
 Verification
 Test end-to-end flow with UI and switching (OpenAI, Ollama, Gemini)
 Verify CDC pipeline (Debezium + Kafka)
 Document System Connectivity

--------

Kafka Upgrade & Advanced Jaeger Monitoring Complete! 🕵️‍♂️📈 ✅
I have successfully upgraded the infrastructure and finalized the advanced observability features you requested.

Key Achievements:

Kafka Upgrade (7.8.0): Upgraded the entire Confluent Platform stack (Kafka, Zookeeper, Schema Registry) to version 7.8.0.
Telemetry Re-enabled: Resolved the UnsupportedVersionException by using a broker version that supports the latest Kafka Java client telemetry features. Telemetry is now fully active.
Advanced Jaeger Insights:
Monitor Tab (SPM): Integrated an OpenTelemetry Collector as a bridge. It converts spans into metrics and feeds them to Prometheus. Jaeger now queries Prometheus to populate the "Monitor" tab with Rate, Error, and Duration metrics.
System Architecture (DAG): Enabled the dependency graph visualization by processing span telemetry through the OTel Collector.
Robust Pipeline: Traces now flow from the Backend -> OTel Collector -> Jaeger, while Metrics flow from the Backend/Collector -> Prometheus -> Jaeger.
How to Verify:

Dashboard: Visit the React UI (http://localhost:5173).
Jaeger Monitor: In the Jaeger UI section, you should now see the "Monitor" tab active for the phoenix-service.
System Architecture: Check the "System Architecture" -> "DAG" tab to see the service relationship graph.
Kafka Logs: No more "The node does not support GET_TELEMETRY_SUBSCRIPTIONS" errors!


--------

 Project Phoenix Implementation Plan
Goal Description
Build "Project Phoenix", a system that ingests changes from a legacy PostgreSQL database via CDC (Debezium + Kafka) and processes them using a modern Java 25 Spring Boot service. This service uses Spring AI to summarize data with an LLM (dynamically switchable between OpenAI, Ollama, and Gemini) and stores vectors in Weaviate. A React UI allows users to view claims and control the AI provider.

Proposed Changes
Infrastructure
[NEW] 

docker-compose.yml
PostgreSQL (Legacy Source) with wal_level=logical.
Zookeeper & Kafka (The Bridge).
Debezium Connect (CDC Connector).
Weaviate (Vector DB).
Ollama (Local AI Model) - Optional/Switchable.
Modern Sync Service (Spring Boot)
[NEW] 

pom.xml
Spring Boot 3.4+
Java 25
Dependencies: spring-boot-starter-web, spring-ai-openai-spring-boot-starter (or similar for ChatClient), spring-ai-weaviate-store-spring-boot-starter, spring-kafka.
[MODIFY] 

pom.xml
Add spring-ai-ollama-spring-boot-starter.
[NEW] 

application.properties
Define default profile (e.g., 

openai
).
[NEW] 

application-openai.properties
OpenAI API Key and Model config.
[NEW] 

application-ollama.properties
Ollama Base URL and Model config.
[MODIFY] 

pom.xml
Add spring-ai-vertex-ai-gemini-spring-boot-starter (or spring-ai-google-gemini based on availability).
[NEW] 

application-gemini.properties
Gemini API Key and Model config.
[NEW] 

ClaimModernizationService.java
Inject VertexAiGeminiChatModel (or equivalent).
Update dynamic switching logic to include "gemini".
[NEW] 

PhoenixController.java
GET /api/claims: endpoints to fetch claims.
POST /api/claims: endpoint to create a test claim (simulating legacy app).
POST /api/config/ai-provider: endpoint to switch between "openai", "ollama", and "gemini".
Phase 3: React UI
[NEW] 

frontend/
Technology: React, Vite, TailwindCSS.
Features:
Dashboard to view Claims (Raw vs Summarized).
Toggle Switch for AI Provider (OpenAI / Ollama / Gemini).
"Simulate Claim" button.
Components:

App.jsx
: Main layout.
ClaimList.jsx: Table/Grid of claims.
ControlPanel.jsx: AI Switcher and Actions.
Phase 4: Observability
[MODIFY] 

docker-compose.yml
Add jaeger service (all-in-one).
Expose ports 16686 (UI) and 4317/4318 (OTLP).
[MODIFY] 

pom.xml
Add spring-boot-starter-actuator.
Add micrometer-tracing-bridge-otel.
Add opentelemetry-exporter-otlp.
[MODIFY] 

application.properties
Configure OTel exporter to point to Jaeger (http://localhost:4318/v1/traces).
Enable tracing sampling (probability 1.0).
[MODIFY] 

PhoenixController.java
Ensure custom spans (if needed) to simulate "Legacy" start.
[MODIFY] 

frontend/src/App.jsx
Add a new section at the bottom embedding the Jaeger UI (iframe) or linking to it.
Section title: "End-to-End Traces".
Phase 5: Documentation
[MODIFY] 

pom.xml
Add springdoc-openapi-starter-webmvc-ui.
[MODIFY] 

application.properties
Fix Port: Set server.port=8081 to avoid Weaviate conflict.
Enable Swagger UI at /swagger-ui.html.
Phase 6: Logging Refinement
[MODIFY] 

ClaimModernizationService.java
Replace System.out.println with SLF4J Logger.
Add debug logging for payload extraction and AI responses.
[MODIFY] 

PhoenixController.java
Add request logging for claim creation and provider switching.
[MODIFY] 

VectorStoreManager.java
Log strategy shifts and store initialization.
[MODIFY] 

application.properties
Configure logging levels for com.example.phoenix.
Verification Plan
Automated Tests
Backend integration tests for API endpoints.
Manual Verification
Start infrastructure and backend.
Start Frontend (npm run dev).
Open UI (localhost).
Toggle AI to "Ollama".
Create a claim via UI.
Verify it gets processed by Ollama (check logs/UI).
Toggle AI to "OpenAI".
Create another claim.
Verify it gets processed by OpenAI.

---------
Jaeger Observability: Full Implementation Complete! 🕵️‍♂️📈 ✅
I have implemented full distributed tracing across the Project Phoenix stack.

What's now available:

End-to-End Traces: You can now trace a request from the initial API call (claim.creation) through Kafka and into the background asynchronous enrichment process (claim.enrichment).
Virtual Thread Support: Micrometer Observations are correctly propagated into the virtual threads used for AI processing, which is notoriously tricky to instrument!
Rich Context: Every trace now includes low-cardinality tags like ai.provider and claim.id, allowing you to filter and search for specific enrichments in Jaeger.
Embedded Dashboard: The Jaeger UI is integrated directly at the bottom of the React Dashboard (http://localhost:5173).
Verification Proof:

Service Availability: curl check confirms phoenix-service is actively exporting spans to Jaeger.
Log Correlation: Service logs now show correlated Trace IDs: e.g., [afe36dc1...-4af98c5...] mirroring the spans in Jaeger.

Log Correlation: Service logs now show correlated Trace IDs: e.g., [afe36dc1...-4af98c5...] mirroring the spans in Jaeger.

-------

System Connectivity Guide 🔌
This guide provides details on how to connect to every component of Project Phoenix using various tools and credentials.

Summary Table
Service	Public URL / Host	Port	Tool Suggestion	Credentials
Frontend UI	http://localhost:5173	5173	Web Browser	N/A
Backend API	http://localhost:8080	8080	Postman / Curl	N/A
Swagger UI	http://localhost:8080/swagger-ui.html	8080	Web Browser	N/A
PostgreSQL	localhost	5432	DBeaver / PGAdmin	postgres / postgres
Weaviate	http://localhost:8090	8090	Weaviate Console	N/A (Anonymous)
Ollama	http://localhost:11434	11434	Ollama CLI / Curl	N/A
Jaeger UI	http://localhost:16686	16686	Web Browser	N/A
Kafka	localhost	29092	Offset Explorer / Kafdrop	N/A
Schema Registry	http://localhost:8081	8081	Postman / Curl	N/A
Debezium API	http://localhost:8083	8083	Postman / Curl	N/A
Detailed Connection Info
🐘 PostgreSQL (Legacy Source)
Database: insurance_corp
Schema: public
Table: claims
Connection String: jdbc:postgresql://localhost:5432/insurance_corp
Recommended Tool: DBeaver (Universal Database Tool).
🚀 Kafka & Debezium
Debezium Connectors: GET http://localhost:8083/connectors to list active connectors.
Kafka Bootstrap: localhost:29092
Topic for Claims: legacy.public.claims
Recommended Tool: Offset Explorer (formerly Kafka Tool) to browse messages.
🧠 Weaviate (Vector DB)
Object Class: 

Claim
GraphQL Endpoint: http://localhost:8090/v1/graphql
REST Endpoint: http://localhost:8090/v1/objects
Recommended Tool: Weaviate Cloud Console (Point it to your local URL).
🔍 Observability (Jaeger)
UI Address: http://localhost:16686
Tracing Pipeline: Backend sends traces to http://localhost:4318/v1/traces (OTLP/HTTP).
🤖 AI Providers (Spring AI)
Ollama: Running locally on port 11434.
OpenAI: Requires OPENAI_API_KEY environment variable.
Gemini: Requires GEMINI_PROJECT_ID and GEMINI_LOCATION.
Internal Docker Network
If you are running a new container and want to join the network, use the service names as hostnames:

legacy-db:5432
kafka:9092
weaviate:8080 (Note: internal port is 8080)
phoenix-backend:8080
jaeger:4318
schema-registry:8081


-----

Verification Plan: Runtime Switching Logic
The goal is to verify that the 
VectorStoreManager
 correctly handles switching between AI providers at runtime via the 
PhoenixController
 endpoints.

Proposed Changes
No code changes are proposed. This is a verification-only plan.

Verification Plan
1. Start Support Services
Ensure Docker services (Postgres, Weaviate, Ollama) are running. (Already verified).

2. Start Backend Locally
Run the phoenix-service using Maven, overriding properties to connect to local Docker ports.

Command:

SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/insurance_corp \
SPRING_DATASOURCE_USERNAME=postgres \
SPRING_DATASOURCE_PASSWORD=postgres \
SPRING_AI_VECTORSTORE_WEAVIATE_HOST=localhost:8090 \
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
mvn spring-boot:run
NOTE

If Kafka connection fails (since it's not exposed), I may need to temporarily disable Kafka listeners or expose the port in 
docker-compose.yml
.

3. Test Provider Switching
Use curl to interact with the API.

Check Default Provider
curl http://localhost:8080/api/config/ai-provider
Expected: {"provider":"ollama"}

Switch to OpenAI
curl -X POST -H "Content-Type: application/json" \
     -d '{"provider":"OPENAI"}' \
     http://localhost:8080/api/config/ai-provider
Expected: {"status":"success","provider":"OPENAI"}

Verify Switch
curl http://localhost:8080/api/config/ai-provider
Expected: {"provider":"openai"}

Switch to Gemini
curl -X POST -H "Content-Type: application/json" \
     -d '{"provider":"GEMINI"}' \
     http://localhost:8080/api/config/ai-provider
Expected: {"status":"success","provider":"GEMINI"}

------
Resolution: WeaviateVectorStoreOptions Compilation Error
The compilation error WeaviateVectorStoreOptions cannot be resolved was addressed by refactoring the vector store configuration and introducing a more flexible provider management system.

Changes Made
1. Vector Store Configuration
Updated 
VectorStoreConfig.java
 to:

Remove the problematic WeaviateVectorStoreOptions import.
Define three distinct 
VectorStore
 beans for Ollama, Gemini, and OpenAI.
Use the standard WeaviateVectorStore.builder() without the explicit options class that was causing issues in Spring AI 1.0.0-M5.
2. Introduction of VectorStoreManager
Created 
VectorStoreManager.java
 to:

Manage the active vector store atomically.
Provide a 
switchToProvider(String providerName)
 method to change the active store at runtime.
Use a 
lookupStore
 strategy to return the correct bean based on the 
AiProvider
 enum.
3. Service Refactoring
Modified 
ClaimModernizationService.java
 to:

Use 
VectorStoreManager
 instead of individual store beans.
Dynamically retrieve the active store and chat model based on the selected provider.
Verification Results
Build Success
The project now compiles successfully using Maven:

mvn clean compile -DskipTests
Status: BUILD SUCCESS

Next Steps
Verify the runtime switching logic by sending requests to the PhoenixController.
Ensure that the WeaviateClient is correctly connected to the Weaviate instance in the docker environment.
-----


SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/insurance_corp \
SPRING_DATASOURCE_USERNAME=postgres \
SPRING_DATASOURCE_PASSWORD=postgres \
SPRING_AI_VECTORSTORE_WEAVIATE_HOST=localhost:8090 \
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
mvn spring-boot:run


docker-compose up -d --build

docker ps && docker logs phoenix-phoenix-backend-1 | tail -n 50

docker-compose up -d --build && sleep 10 && curl -X POST http://localhost:8080/api/claims -H "Content-Type: application/json" -d '{"description": "Logging test claim: Car fire on highway."}' && sleep 5 && docker logs phoenix-phoenix-backend-1 | tail -n 50

docker logs phoenix-phoenix-backend-1 | grep -E "Enriched|Processing enrichment|Successfully switched" | tail -n 20


docker logs phoenix-phoenix-backend-1 | tail -n 100


docker logs phoenix-phoenix-backend-1 | grep -i "partitions assigned"


docker exec phoenix-kafka-1 kafka-consumer-groups --bootstrap-server localhost:9092 --group phoenix-group --describe

docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "INSERT INTO claims (description, status) VALUES ('Water damage from a burst pipe in the basement.', 'OPEN');" && sleep 5 && docker logs phoenix-phoenix-backend-1 | tail -n 50


curl -s http://localhost:8083/connectors/inventory-connector/status && docker logs phoenix-phoenix-backend-1 | grep "Water damage"

curl -s http://localhost:8083/connectors

curl -s http://localhost:8083/connectors/insurance-connector/status && docker logs phoenix-phoenix-backend-1 | grep "Water damage"


docker exec phoenix-kafka-1 kafka-topics --bootstrap-server localhost:9092 --list



docker exec phoenix-kafka-1 kafka-console-consumer --bootstrap-server localhost:9092 --topic legacy.public.claims --from-beginning --max-messages 10


docker logs phoenix-debezium-1 | tail -n 100


curl -s http://localhost:8083/connectors/insurance-connector/status | jq . && docker logs phoenix-debezium-1 --tail 500 | grep -E "ERROR|WARN|Postgres|insurance-connector" | head -n 50

curl -s http://localhost:8083/connectors/insurance-connector/status | jq . && docker logs phoenix-debezium-1 --tail 500 | grep -E "ERROR|WARN|Postgres|insurance-connector" | head -n 50


docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "\d claims"

docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "DROP TABLE IF EXISTS claims;"

docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "CREATE TABLE IF NOT EXISTS claims (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, description TEXT, status varchar(50), summary TEXT);"

docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "ALTER TABLE claims ADD COLUMN IF NOT EXISTS summary TEXT;"

docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "SELECT id, description, summary FROM claims WHERE summary IS NOT NULL ORDER BY id DESC LIMIT 5;"

docker logs phoenix-phoenix-backend-1 --tail 100 | grep -E "Error|Processing| enrichment"

docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "SELECT * FROM pg_replication_slots;" && docker logs phoenix-debezium-1 | grep -i "snapshot" | tail -n 20

docker exec phoenix-kafka-1 kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic legacy.public.claims --time -1 && docker exec phoenix-kafka-1 kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic legacy.public.claims --time -2


docker logs phoenix-phoenix-backend-1 && docker exec phoenix-kafka-1 kafka-consumer-groups --bootstrap-server localhost:9092 --group phoenix-group --describe

docker exec phoenix-kafka-1 kafka-console-consumer --bootstrap-server localhost:9092 --topic legacy.public.claims --from-beginning --max-messages 1 --property print.key=true

docker logs phoenix-phoenix-backend-1 -n 500


docker-compose up -d --build && sleep 10 && docker logs phoenix-phoenix-backend-1 | grep "Kafka message received"

docker logs phoenix-phoenix-backend-1 | grep "Kafka message received" && docker exec phoenix-kafka-1 kafka-consumer-groups --bootstrap-server localhost:9092 --group phoenix-group --describe


docker logs phoenix-phoenix-backend-1 | grep -i "kafka" | tail -n 50


docker exec phoenix-kafka-1 kafka-console-consumer --bootstrap-server localhost:9092 --topic legacy.public.claims --from-beginning --max-messages 5 --timeout-ms 5000


docker logs phoenix-phoenix-backend-1 | grep -i "warn"

docker exec phoenix-phoenix-backend-1 env | grep KAFKA


docker exec phoenix-kafka-1 kafka-console-consumer --bootstrap-server localhost:9092 --topic legacy.public.claims --from-beginning --max-messages 1 > /tmp/kafka_msg.json && ls -l /tmp/kafka_msg.json


docker-compose up -d --build && sleep 15 && docker logs phoenix-phoenix-backend-1 | grep -E "Kafka listener triggered|Processing enrichment" && docker exec phoenix-kafka-1 kafka-consumer-groups --bootstrap-server localhost:9092 --group phoenix-group --describe


docker-compose up -d --build && sleep 15 && docker logs phoenix-phoenix-backend-1 | grep -i "Kafka listener triggered" && docker exec phoenix-kafka-1 kafka-consumer-groups --bootstrap-server localhost:9092 --group phoenix-group --describe

docker-compose up -d --build && sleep 15 && docker logs phoenix-phoenix-backend-1 | grep -i "Kafka listener triggered" && docker exec phoenix-kafka-1 kafka-consumer-groups --bootstrap-server localhost:9092 --group phoenix-group --describe


docker logs phoenix-phoenix-backend-1 | grep "Kafka listener triggered" && docker exec phoenix-kafka-1 kafka-consumer-groups --bootstrap-server localhost:9092 --group phoenix-group --describe && docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "SELECT id, summary FROM claims WHERE summary IS NOT NULL LIMIT 5;"

docker logs phoenix-phoenix-backend-1 | tail -n 50

docker exec phoenix-kafka-1 kafka-consumer-groups --bootstrap-server localhost:9092 --group phoenix-group --describe

docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "SELECT id, summary FROM claims WHERE summary IS NOT NULL;"

docker logs phoenix-phoenix-backend-1 | grep -i "ERROR"

docker logs phoenix-phoenix-backend-1 | tail -n 100

docker ps -a

docker-compose up -d --build && sleep 20 && docker logs phoenix-phoenix-backend-1 | grep -i "poll" | tail -n 50


docker logs phoenix-phoenix-backend-1 | grep -iE "poll|received|deserializer|kafka" | tail -n 100

docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "INSERT INTO claims (description, status) VALUES ('Stolen vehicle report from a residential driveway.', 'OPEN');" && sleep 5 && docker logs phoenix-phoenix-backend-1 | grep -iE "Kafka listener triggered|Processing enrichment|Error" | tail -n 20

docker exec phoenix-kafka-1 kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic legacy.public.claims --time -1 && docker logs phoenix-debezium-1 | tail -n 20



docker exec phoenix-kafka-1 kafka-consumer-groups --bootstrap-server localhost:9092 --group phoenix-group --describe

docker logs phoenix-phoenix-backend-1 | grep -i "listener triggered" && docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "SELECT id, summary FROM claims WHERE id = 8;"

docker logs phoenix-ollama-1 && docker logs phoenix-phoenix-backend-1 | grep -iE "enriched|summary|Error" | tail -n 20

docker exec phoenix-ollama-1 ollama list && docker logs phoenix-phoenix-backend-1 | grep -i "Processing enrichment" | tail -n 50


docker exec phoenix-ollama-1 ollama pull llama3 && sleep 10 && docker logs phoenix-phoenix-backend-1 | grep -iE "enriched|Error" | tail -n 20



docker exec phoenix-ollama-1 ollama list


docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "INSERT INTO claims (description, status) VALUES ('Tree fell on the roof during a heavy storm, causing structural damage.', 'OPEN');" && sleep 30 && docker logs phoenix-phoenix-backend-1 | grep -iE "listener triggered|Processing enrichment|enriched|Error" | tail -n 20 && docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "SELECT id, description, summary FROM claims ORDER BY id DESC LIMIT 1;"



docker logs phoenix-phoenix-backend-1 | grep -iE "claim 10" -A 10 -B 5


docker-compose up -d --build phoenix-backend && sleep 15 && docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "INSERT INTO claims (description, status) VALUES ('Basement flooding due to heavy rain and sump pump failure.', 'OPEN');" && sleep 20 && docker logs phoenix-phoenix-backend-1 | grep -iE "listener triggered|Processing enrichment|enriched|Error" | tail -n 20 && docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "SELECT id, description, summary FROM claims ORDER BY id DESC LIMIT 1;"


docker logs phoenix-phoenix-backend-1 --tail 100 && docker logs phoenix-phoenix-backend-1 | grep -iE "enrichment|Claim|Error" | tail -n 20


docker logs phoenix-phoenix-backend-1 | grep "ClaimModernizationService" | tail -n 50


docker logs phoenix-phoenix-backend-1 | grep "ClaimModernizationService" | tail -n 50


docker logs phoenix-phoenix-backend-1 | grep -iE "enriched|Error" | tail -n 20 && docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "SELECT id, description, summary FROM claims ORDER BY id DESC LIMIT 1;"

docker logs phoenix-phoenix-backend-1 | grep -iE "enriched|Error" | tail -n 20 && docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "SELECT id, description, summary FROM claims ORDER BY id DESC LIMIT 1;"


docker logs phoenix-phoenix-backend-1 --tail 500 | grep -iE "enriched|Error|claim ID"


docker logs phoenix-phoenix-backend-1 --tail 1000 | grep -v "DEBUG" | grep -v "HEARTBEAT" | grep -v "FETCH"



docker exec phoenix-ollama-1 ollama pull mxbai-embed-large && sleep 5 && docker exec phoenix-ollama-1 ollama list


docker-compose up -d --build phoenix-backend && sleep 15 && docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "INSERT INTO claims (description, status) VALUES ('Fire damage in the kitchen caused by a toaster malfunction.', 'OPEN');" && sleep 45 && docker logs phoenix-phoenix-backend-1 | grep -iE "listener triggered|Processing enrichment|enriched|Error" | tail -n 20 && docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "SELECT id, description, summary FROM claims ORDER BY id DESC LIMIT 1;"



docker logs phoenix-phoenix-backend-1 | grep "Saved vector for claim ID: 12"


find phoenix-ui/src -name "App.jsx"

docker-compose up -d --build phoenix-backend && sleep 15 && docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "INSERT INTO claims (description, status) VALUES ('Water damage from a leaking roof after a thunderstorm.', 'OPEN');" && sleep 30 && docker logs phoenix-phoenix-backend-1 | grep -iE "observation|span|trace|enriched" | tail -n 20

docker logs phoenix-phoenix-backend-1 | grep -iE "observation|span|trace|enriched|virtual-76" | tail -n 50

docker-compose up -d --build phoenix-backend && sleep 15 && docker logs phoenix-phoenix-backend-1 | tail -n 50

docker logs phoenix-phoenix-backend-1 | tail -n 50 && docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "INSERT INTO claims (description, status) VALUES ('Storm damage to the garden shed.', 'OPEN');" && sleep 30 && docker logs phoenix-phoenix-backend-1 | grep -iE "observation|span|trace|enriched" | tail -n 20


docker logs phoenix-phoenix-backend-1 | grep -iE "Claim 22" -B 2 -A 5

docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "SELECT id FROM claims ORDER BY id DESC LIMIT 1;"


docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "SELECT id, summary FROM claims WHERE id = 18;"


docker logs phoenix-phoenix-backend-1 | grep -i "18" && docker exec phoenix-kafka-1 kafka-consumer-groups --bootstrap-server localhost:9092 --group phoenix-group --describe

docker logs phoenix-phoenix-backend-1 | grep "afe36dc1566ff6dbe4a6517577c1f66a"


curl -s http://localhost:16686/api/services


docker-compose up -d --build phoenix-backend && sleep 15 && docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "INSERT INTO claims (description, status) VALUES ('Hail damage to the car parked outside.', 'OPEN');" && sleep 45 && docker logs phoenix-phoenix-backend-1 | grep -iE "observation|span|trace|enriched" | tail -n 20 && curl -s http://localhost:16686/api/services

docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "SELECT id, description, summary FROM claims ORDER BY id DESC LIMIT 1;"

docker logs phoenix-phoenix-backend-1 | grep "19" && docker exec phoenix-kafka-1 kafka-consumer-groups --bootstrap-server localhost:9092 --group phoenix-group --describe

docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "SELECT id, description, summary FROM claims ORDER BY id DESC LIMIT 5;"


docker-compose up -d --build prometheus jaeger phoenix-backend && sleep 15 && docker-compose ps && curl -s http://localhost:9090/-/healthy && curl -s http://localhost:8080/actuator/prometheus | tail -n 10


docker-compose up -d --build phoenix-backend && sleep 15 && curl -s http://localhost:8080/actuator/prometheus | tail -n 10 && docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "INSERT INTO claims (description, status) VALUES ('Water damage in the ceiling from a malfunctioning AC unit on the floor above.', 'OPEN');" && sleep 45 && curl -s http://localhost:16686/api/services


curl -s http://localhost:9090/api/v1/targets | grep -i "health" && curl -s http://localhost:16686/api/services

curl -s http://localhost:16686/api/traces?service=phoenix-service | grep -iE "weaviate|ollama|gemini" | tail -n 20

docker-compose stop zookeeper kafka schema-registry debezium phoenix-backend && docker-compose rm -f zookeeper kafka schema-registry debezium phoenix-backend && docker-compose up -d --build zookeeper kafka schema-registry debezium otel-collector prometheus jaeger phoenix-backend && sleep 45 && docker-compose ps && docker logs phoenix-phoenix-backend-1 | grep -iE "error|exception|telemetry" | tail -n 20 && docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "INSERT INTO claims (description, status) VALUES ('Water damage from a burst pipe in the kitchen.', 'OPEN');" && sleep 45 && curl -s http://localhost:16686/api/services


docker logs phoenix-otel-collector-1 | tail -n 20 && curl -s http://localhost:9090/api/v1/query?query=latency_bucket | jq '.data.result[0]' && curl -s http://localhost:9090/api/v1/query?query=calls_total | jq '.data.result[0]'

ls -l /Users/prashant/projects/phoenix/otel-collector-config.yaml


ls -ld /Users/prashant/projects/phoenix/otel-collector-config.yaml

rm -rf /Users/prashant/projects/phoenix/otel-collector-config.yaml


docker-compose restart otel-collector prometheus phoenix-backend && sleep 20 && docker logs phoenix-otel-collector-1 | tail -n 20 && curl -s http://localhost:8889/metrics | tail -n 10 && curl -s http://localhost:16686/api/services && docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "INSERT INTO claims (description, status) VALUES ('Thunderstorm causing power surge and appliance damage.', 'OPEN');" && sleep 45 && curl -s http://localhost:16686/api/services


docker-compose stop otel-collector && docker-compose rm -f otel-collector && docker-compose up -d otel-collector && sleep 10 && docker logs phoenix-otel-collector-1 && curl -s http://localhost:8889/metrics | tail -n 10 && curl -s http://localhost:16686/api/services && docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "INSERT INTO claims (description, status) VALUES ('Snow load caused a structural collapse in the attic.', 'OPEN');" && sleep 45 && curl -s http://localhost:16686/api/services



docker logs phoenix-otel-collector-1 | tail -n 20 && curl -s http://localhost:9090/api/v1/query?query=calls_total | jq '.data.result' && curl -s http://localhost:16686/api/traces?service=phoenix-service | jq '.data | length'


docker logs phoenix-phoenix-backend-1 | tail -n 50 && curl -s "http://localhost:9090/api/v1/query?query=calls_total" | jq '.data.result' && curl -s "http://localhost:16686/api/traces?service=phoenix-service" | jq '.data | length'


curl -s "http://localhost:9090/api/v1/targets" | jq '.data.activeTargets[] | {job: .labels.job, health: .health}' && docker logs phoenix-otel-collector-1 | grep -i "spanmetrics" | tail -n 10 && curl -s "http://localhost:9090/api/v1/query?query=calls_total" | jq '.data.result'


docker-compose restart prometheus && sleep 10 && curl -s "http://localhost:9090/api/v1/targets" | jq '.data.activeTargets[] | {job: .labels.job, health: .health}' && curl -s "http://localhost:9090/api/v1/query?query=calls_total" | jq '.data.result' && docker logs phoenix-otel-collector-1 | tail -n 10 && curl -s "http://localhost:16686/api/services"



docker-compose restart prometheus && sleep 10 && curl -s "http://localhost:9090/api/v1/targets" | jq '.data.activeTargets[] | {job: .labels.job, health: .health}' && curl -s "http://localhost:9090/api/v1/query?query=calls_total" | jq '.data.result' && docker logs phoenix-otel-collector-1 | tail -n 10 && curl -s "http://localhost:16686/api/services"

After Manual changes for metrics:
docker compose down -v && docker compose up -d --build


docker compose build --no-cache phoenix-backend && docker compose up -d phoenix-backend


ls -l jmx_prometheus_javaagent.jar kafka-jmx-config.yml && docker compose ps && docker compose logs kafka | tail -n 20 && docker compose logs phoenix-backend | tail -n 20

find ~/.m2/repository -name "jmx_prometheus_javaagent*.jar"

rm -rf jmx_prometheus_javaagent.jar kafka-jmx-config.yml && cp /Users/prashant/.m2/repository/io/prometheus/jmx/jmx_prometheus_javaagent/0.20.0/jmx_prometheus_javaagent-0.20.0.jar jmx_prometheus_javaagent.jar


rm -rf jmx_prometheus_javaagent.jar kafka-jmx-config.yml && cp /Users/prashant/.m2/repository/io/prometheus/jmx/jmx_prometheus_javaagent/0.20.0/jmx_prometheus_javaagent-0.20.0.jar jmx_prometheus_javaagent.jar


docker compose ps && docker compose logs kafka | tail -n 20 && docker compose logs phoenix-backend | tail -n 20 && curl -s http://localhost:9404 | head -n 10


docker compose logs debezium | tail -n 20 && curl -s http://localhost:8083/connectors

sleep 30 && curl -s http://localhost:8083/connectors && curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {job: .labels.job, health: .health}'


bash setup-debezium.sh && sleep 5 && curl -s http://localhost:8083/connectors/insurance-connector/status | jq


docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "INSERT INTO claims (description, status) VALUES ('Storm surge caused coastal flooding in the basement.', 'OPEN');" && sleep 45 && docker logs phoenix-phoenix-backend-1 | grep -iE "enrichment|summary" | tail -n 20


bash init-db.sh

docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "INSERT INTO claims (description, status) VALUES ('Storm surge caused coastal flooding in the basement.', 'OPEN');" && sleep 45 && docker logs phoenix-phoenix-backend-1 | grep -iE "enrichment|summary" | tail -n 20


docker logs phoenix-phoenix-backend-1 | grep "Kafka listener triggered" | tail -n 5 && docker logs phoenix-phoenix-backend-1 | grep "enriched successfully" | tail -n 5

docker logs phoenix-phoenix-backend-1 | grep -iE "error|exception" | tail -n 20

docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "SELECT id, description, summary FROM claims;"

docker exec -it phoenix-ollama-1 ollama list

docker exec -d phoenix-ollama-1 ollama pull llama3 && sleep 5 && docker logs phoenix-ollama-1 | tail -n 10

docker logs phoenix-otel-collector-1 | grep "Exporting" | tail -n 5 && docker logs phoenix-otel-collector-1 | grep "Exporting failed" | tail -n 5

curl -s http://localhost:16686/api/services

docker exec -it phoenix-ollama-1 ollama list

docker exec -d phoenix-ollama-1 ollama pull llama3 && sleep 10 && docker exec -it phoenix-ollama-1 ollama list

docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "SELECT * FROM claims;"

docker exec -it phoenix-ollama-1 ollama pull llama3 && docker exec -it phoenix-ollama-1 ollama list

docker exec -it phoenix-ollama-1 ollama pull tinyllama && docker exec -it phoenix-ollama-1 ollama list


docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "INSERT INTO claims (description, status) VALUES ('Wildfire smoke damage to the interior curtains and furniture.', 'OPEN');" && sleep 15 && docker logs phoenix-phoenix-backend-1 | grep -iE "enrichment|summary" | tail -n 20 && docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "SELECT * FROM claims WHERE status = 'OPEN' ORDER BY id DESC LIMIT 1;"


docker logs phoenix-phoenix-backend-1 | grep -iE "processing enrichment|enriched successfully" | tail -n 10


docker logs phoenix-phoenix-backend-1 | grep -iE "exception" -A 10 -B 5

curl -s "http://localhost:9090/api/v1/query?query=count({__name__=~'span_metrics.*'})" | jq '.data.result' && curl -s "http://localhost:9090/api/v1/query?query=span_metrics_calls_total" | jq '.data.result[0]' && curl -s "http://localhost:16686/api/dependencies?endTs=$(date +%s)000&lookback=3600000" | jq '.data'


docker logs phoenix-otel-collector-1 | tail -n 20 && docker logs phoenix-phoenix-backend-1 | grep "OpenTelemetry"

docker logs phoenix-phoenix-backend-1 | grep -iE "otel|export|log" | tail -n 50

docker compose up -d otel-collector phoenix-backend && sleep 15 && docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "INSERT INTO claims (description, status) VALUES ('OTEL Debug: Manual check for logs in collector.', 'OPEN');" && sleep 5 && docker logs phoenix-otel-collector-1 | tail -n 50


docker logs phoenix-otel-collector-1 | grep -E "LogsData|LogRecord" -A 10 | tail -n 50

docker stop phoenix-otel-collector-1 phoenix-jaeger-1 && docker rm phoenix-otel-collector-1 phoenix-jaeger-1 && docker compose up -d otel-collector jaeger && sleep 10 && docker logs phoenix-otel-collector-1 | tail -n 20

docker logs phoenix-otel-collector-1 | grep "Starting otelcol-contrib" -A 20

curl -s "http://localhost:9090/api/v1/query?query=count({__name__=~'span_metrics_latency.*'})" | jq '.data.result'

curl -s "http://localhost:9090/api/v1/label/__name__/values" | jq '.data[] | select(startswith("span_metrics"))'

docker pull jaegertracing/all-in-one:2.0.0 || echo "2.0.0 not available"

docker exec -it phoenix-jaeger-1 /jaeger-all-in-one --version

cat <<EOF > /Users/prashant/projects/phoenix/traffic-gen.sh
#!/bin/bash
for i in {1..5}; do
  docker exec -it phoenix-legacy-db-1 psql -U postgres -d insurance_corp -c "INSERT INTO claims (description, status) VALUES ('Traffic gen claim $i: Damage due to heavy winds.', 'OPEN');"
  sleep 2
done
EOF
chmod +x /Users/prashant/projects/phoenix/traffic-gen.sh
./Users/prashant/projects/phoenix/traffic-gen.sh


bash traffic-gen.sh


curl -s "http://localhost:9090/api/v1/query?query=span_metrics_calls_total" | jq '.data.result[0].metric' && curl -s "http://localhost:16686/api/dependencies?endTs=$(date +%s)000&lookback=3600000" | jq '.data'

docker stop phoenix-otel-collector-1 && docker rm phoenix-otel-collector-1 && docker compose up -d otel-collector && sleep 5 && docker logs phoenix-otel-collector-1 | head -n 50


docker stop phoenix-otel-collector-1 && docker rm phoenix-otel-collector-1 && docker compose up -d otel-collector && sleep 5 && docker logs phoenix-otel-collector-1 | head -n 30

docker stop phoenix-otel-collector-1 && docker rm phoenix-otel-collector-1 && docker compose up -d otel-collector && sleep 5 && docker logs phoenix-otel-collector-1 | head -n 30

bash traffic-gen.sh && sleep 5 && curl -s "http://localhost:9090/api/v1/query?query=span_metrics_calls_total" | jq '.data.result[0].metric'

docker logs phoenix-otel-collector-1 | grep "span_metrics_calls_total" -A 10 | tail -n 20

curl -s "http://localhost:9090/api/v1/query?query={__name__=~'span_metrics.*'}" | jq '.data.result[0].metric'

docker exec -it phoenix-otel-collector-1 curl -s localhost:8889/metrics | grep span_metrics

docker compose up -d jaeger otel-collector prometheus phoenix-backend && sleep 10 && bash traffic-gen.sh && sleep 10 && curl -s "http://localhost:9090/api/v1/query?query=span_metrics_calls_total" | jq '.data.result[0].metric'


curl -s "http://localhost:9090/api/v1/query?query=span_metrics_calls_total" | jq '.data.result[0].metric' && curl -s "http://localhost:9090/api/v1/query?query=span_metrics_duration_milliseconds_bucket" | jq '.data.result[0].metric'

curl -s "http://localhost:16686/api/dependencies?endTs=$(date +%s)000&lookback=3600000" | jq '.'

curl -s "http://localhost:16686/api/traces?service=phoenix-service&limit=1" | jq '.data[0].spans[] | {operationName: .operationName, tags: .tags}' | head -n 50

curl -s "http://localhost:16686/api/traces?service=phoenix-service&operation=legacy.public.claims%20process" | jq '.data[0].spans[] | {operationName: .operationName}'

curl -s "http://localhost:16686/api/operations?service=phoenix-service" | jq '.data[]'

curl -s "http://localhost:16686/api/traces?service=phoenix-service&operation=http%20post&limit=1" | jq '.data[0].spans[] | select(.operationName == "http post") | .tags'

docker exec -d phoenix-ollama-1 ollama pull mxbai-embed-large && docker stop phoenix-otel-collector-1 && docker rm phoenix-otel-collector-1 && docker compose up -d otel-collector && sleep 5 && bash traffic-gen.sh && sleep 5 && curl -s "http://localhost:16686/api/dependencies?endTs=$(date +%s)000&lookback=3600000" | jq '.'

curl -s "http://localhost:16686/api/dependencies?endTs=$(date +%s)000&lookback=3600000" | jq '.'

curl -s "http://localhost:16686/api/traces?service=phoenix-service&operation=http%20post&limit=1" | jq '.data[0].spans[] | select(.operationName == "http post") | .tags' | grep -A 2 "peer.service"


curl -s "http://localhost:16686/api/metrics/calls?service=phoenix-service&lookback=3600000" | jq '.' && curl -s "http://localhost:16686/api/metrics/errors?service=phoenix-service&lookback=3600000" | jq '.' && curl -s "http://localhost:16686/api/metrics/latencies?service=phoenix-service&lookback=3600000" | jq '.'


curl -s "http://localhost:16686/api/metrics/latencies?service=phoenix-service&lookback=3600000&quantile=0.95" | jq '.'


docker compose up -d jaeger otel-collector prometheus phoenix-backend && sleep 10 && bash traffic-gen.sh && sleep 10 && curl -s "http://localhost:9090/api/v1/query?query=span_metrics_calls_total" | jq '.data.result[0].metric' && curl -s "http://localhost:16686/api/metrics/calls?service=phoenix-service&lookback=3600000" | jq '.'


docker compose up -d jaeger otel-collector prometheus phoenix-backend && sleep 10 && bash traffic-gen.sh && sleep 10 && curl -s "http://localhost:9090/api/v1/query?query=span_metrics_calls_total" | jq '.data.result[0].metric' && curl -s "http://localhost:16686/api/metrics/calls?service=phoenix-service&lookback=3600000" | jq '.'

docker stop ani-gravity-otel-collector-1 && docker rm ani-gravity-otel-collector-1 && docker compose up -d otel-collector && sleep 5 && docker logs ani-gravity-otel-collector-1 | head -n 30


bash traffic-gen.sh && sleep 5 && curl -s "http://localhost:9090/api/v1/query?query=span_metrics_calls_total" | jq '.data.result[0].metric' && curl -s "http://localhost:16686/api/metrics/calls?service=phoenix-service&lookback=3600000" | jq '.'


curl -s "http://localhost:9090/api/v1/query?query=span_metrics_calls_total" | jq '.data.result[0].metric' && curl -s "http://localhost:16686/api/metrics/calls?service=phoenix-service&lookback=3600000" | jq '.'

curl -s "http://localhost:9090/api/v1/label/__name__/values" | jq '.data[] | select(startswith("span_metrics"))'

curl -s "http://localhost:9090/api/v1/query?query=span_metrics_calls_total" | jq '.'

docker compose up -d jaeger otel-collector && sleep 15 && bash traffic-gen.sh

docker compose up -d jaeger otel-collector && sleep 15 && bash traffic-gen.sh


docker compose up -d --build <service_name>

docker compose up -d --build phoenix-backend

mvn dependency:tree -Dverbose -Dincludes=com.google.protobuf   
