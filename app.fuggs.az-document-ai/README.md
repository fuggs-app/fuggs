# az-document-ai

Azure Document Intelligence microservice for intelligent document data extraction.

This service uses Azure Document Intelligence (formerly Form Recognizer) to extract structured data from invoices, receipts, and other documents. It provides a REST API for document analysis and integrates with the main Fuggs application.

## Environment Configuration

This service requires Azure Document Intelligence credentials and optionally OpenAI API access.

### Quick Setup

1. Copy the example environment file:
   ```bash
   cp .env.example .env
   ```

2. Edit `.env` and add your Azure credentials (see "Getting Azure Credentials" below)

3. Run the service:
   ```bash
   ./mvnw quarkus:dev
   ```

### Required Environment Variables

Create a `.env` file in the `app.fuggs.az-document-ai` directory with the following variables:

```bash
# Azure Document Intelligence (REQUIRED)
FUGGS_AZURE_DOCUMENT_AI_ENDPOINT=https://your-resource.cognitiveservices.azure.com/
FUGGS_AZURE_DOCUMENT_AI_KEY=your-azure-document-ai-key-here

# OpenAI API (OPTIONAL - for enhanced processing)
OPENAI_API_KEY=sk-your-openai-api-key-here
```

**IMPORTANT:** The `.env` file is gitignored to prevent accidentally committing secrets.

You can also copy `.env.example` as a starting point:
```bash
cp .env.example .env
```

### Getting Azure Credentials

1. Create an Azure Document Intelligence resource in the [Azure Portal](https://portal.azure.com)
2. Navigate to your resource → Keys and Endpoint
3. Copy the endpoint URL to `FUGGS_AZURE_DOCUMENT_AI_ENDPOINT`
4. Copy one of the keys to `FUGGS_AZURE_DOCUMENT_AI_KEY`

### Service Configuration

- **Port (dev mode):** 8100
- **API root path:** `/api/az-document-ai`
- **Azure model:** `prebuilt-invoice` (configured in application.properties)
- **Max upload size:** 4MB

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/invoices-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.