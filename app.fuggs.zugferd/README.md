# zugferd

ZugFerd e-invoice extraction microservice for intelligent invoice data extraction.

This service extracts embedded ZugFerd XML invoice data from PDF documents using the Mustang Project library. It provides a REST API for ZugFerd extraction and integrates with the main Fuggs application.

## What is ZugFerd?

ZugFerd (Zentraler User Guide des Forums elektronische Rechnung Deutschland) is a German e-invoicing standard that embeds structured XML invoice data within PDF documents. This allows both human-readable and machine-readable invoice data in a single file.

## Environment Configuration

This service optionally uses OpenAI for enhanced invoice processing.

### Quick Setup

1. (Optional) Copy the example environment file if you want to use OpenAI:
   ```bash
   cp .env.example .env
   ```

2. Edit `.env` and add your OpenAI API key if needed

3. Run the service:
   ```bash
   ./mvnw quarkus:dev
   ```

### Optional Environment Variables

Create a `.env` file in the `app.fuggs.zugferd` directory with the following variables:

```bash
# OpenAI API (OPTIONAL - for enhanced processing)
OPENAI_API_KEY=sk-your-openai-api-key-here
```

**IMPORTANT:** The `.env` file is gitignored to prevent accidentally committing secrets.

You can also copy `.env.example` as a starting point:
```bash
cp .env.example .env
```

### Service Configuration

- **Port (dev mode):** 8103
- **Extraction library:** Mustang Project (`org.mustangproject`)
- **Supported formats:** ZugFerd 1.0, 2.0, 2.1 (Comfort, Basic, Minimum profiles)
- **Input:** PDF files with embedded ZugFerd XML

## How It Works

1. Receives PDF document for analysis
2. Uses `ZUGFeRDImporter` to extract embedded XML invoice data
3. Parses invoice header fields (totals, tax amounts, dates, vendor info)
4. Returns structured invoice data as JSON
5. Falls back to Azure Document AI if ZugFerd extraction fails

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_** Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8103/q/dev/>.

## API Endpoints

- `POST /api/zugferd/analyze` - Extract ZugFerd data from PDF document

## Development Notes

### Calculation Errors

The service uses `doIgnoreCalculationErrors()` to handle incomplete invoices where line item totals don't match header totals. We read values directly from the XML header using:

- `ZUGFeRDImporter.getAmount()` - Grand total from XML header
- `ZUGFeRDImporter.getTaxTotalAmount()` - Total tax from XML header
- `ZUGFeRDImporter.getTaxBasisTotalAmount()` - Tax basis from XML header

**Important:** Do NOT use `TransactionCalculator` which returns 0 when calculation errors are ignored.
```