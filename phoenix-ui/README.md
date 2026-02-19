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

