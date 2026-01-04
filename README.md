# Report Function

A Google Cloud Function that generates feedback reports by fetching evaluation data from the ms-feedback service and sending formatted reports via email through a notification service.

## Overview

This function:
1. Retrieves feedback evaluation data from the ms-feedback service for the current date
2. Formats the data into a readable text report
3. Sends the formatted report via email using the notification service

## Prerequisites

- Java 11 or higher
- Maven 3.6 or higher
- Google Cloud SDK (for deployment)
- Access to Google Cloud Platform

## Environment Variables

The function requires the following environment variables:

- `MS_FEEDBACK_BASE_URL`: Base URL for the ms-feedback service (e.g., `http://localhost:9084`)
- `NOTIFICATION_BASE_URL`: Base URL for the notification service (e.g., `https://notification-780675609467.europe-west1.run.app`)
- `REPORT_EMAIL`: Email address where the report will be sent

## Local Development

### Running Locally

1. Set environment variables:
   ```bash
   export MS_FEEDBACK_BASE_URL=http://localhost:9084
   export NOTIFICATION_BASE_URL=https://notification-780675609467.europe-west1.run.app
   export REPORT_EMAIL=recipient@example.com
   ```

2. Run the function locally:
   ```bash
   mvn function:run
   ```

3. Test the function:
   ```bash
   curl --location 'http://localhost:8080' \
     --header 'Content-Type: application/json'
   ```

### Building

```bash
mvn clean package
```

### Testing

Run tests:
```bash
mvn test
```

Run tests with coverage report:
```bash
mvn test jacoco:report
```

Coverage report will be generated at `target/site/jacoco/index.html`

**Current Coverage:**
- Instruction Coverage: 98%
- Branch Coverage: 88%

## Deployment

### Deploy to Google Cloud Functions (Gen 2)

```bash
gcloud functions deploy report \
  --gen2 \
  --runtime=java11 \
  --region=YOUR_REGION \
  --source=. \
  --entry-point=functions.Report \
  --trigger-http \
  --allow-unauthenticated \
  --set-env-vars MS_FEEDBACK_BASE_URL=http://localhost:9084,NOTIFICATION_BASE_URL=https://notification-780675609467.europe-west1.run.app,REPORT_EMAIL=recipient@example.com
```

### Configuration

- **Build Context Directory**: `.` (root directory containing pom.xml)
- **Entrypoint**: `functions.Report`
- **Function Target**: `functions.Report`
- **Runtime**: `java11`

## API

### Endpoint

The function accepts HTTP POST requests (any HTTP method is supported).

### Request

No request body is required. The function automatically uses the current date/time in ISO 8601 format.

**Example:**
```bash
curl --location 'https://report-780675609467.us-central1.run.app' \
  --header 'Content-Type: application/json'
```

### Response

**Success (200):**
```json
{
  "message": "Report generated and sent successfully"
}
```

**Error (500):**
```json
{
  "error": "Error message description"
}
```

## Report Format

The generated report includes:

1. **Evaluations per day**: Daily breakdown of evaluation counts
2. **Evaluations per urgency**: Breakdown by urgency level (LOW, MEDIUM, URGENT, etc.)

Example output:
```
Feedback Report
===============

Evaluations per day:
  2026-01-03: 6
  2026-01-02: 2
  2026-01-01: 1

Evaluations per urgency:
  LOW: 4
  URGENT: 5
```

## Project Structure

```
report/
├── pom.xml                          # Maven configuration
├── src/
│   ├── main/
│   │   └── java/
│   │       └── functions/
│   │           └── Report.java      # Main function implementation
│   └── test/
│       └── java/
│           └── functions/
│               └── ReportTest.java  # Unit tests
└── README.md                         # This file
```

## Dependencies

- **Google Cloud Functions Framework API**: For Cloud Functions runtime
- **Gson**: For JSON parsing and serialization
- **JUnit 5**: For unit testing
- **Mockito**: For mocking in tests
- **JaCoCo**: For code coverage reporting

## Error Handling

The function handles the following error scenarios:

- Missing environment variables (returns 500 with error message)
- HTTP errors from ms-feedback service (returns the service's status code)
- HTTP errors from notification service (returns the service's status code)
- Network exceptions (returns 500 with error message)

## License

Copyright 2020 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

