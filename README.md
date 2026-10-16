# loanflow

A consumer loan system that covers the whole life of a loan: online application, automatic credit scoring against a credit bureau, approval by bank staff, disbursement, repayment schedule, repayments, overdue penalties and default.

The application is a **modular monolith** built with Spring Modulith. Each business area is its own module. Modules talk to each other through published events and small public APIs, and the module boundaries are checked by a test on every build.

```
 application ──ApplicationSubmitted──▶ scoring ──(LoanApplications.applyScoring)──▶ application
      │                                   │
      │                               credit bureau (HTTP)
      │
      ├──ApplicationApproved──▶ loan ──LoanDisbursed / InstallmentOverdue / LoanClosed──┐
      │                                                                                 ▼
      └──ApplicationRejected──────────────────────────────────────────────────▶ notification ──▶ RabbitMQ
```

## Stack

- Java 17, Spring Boot 3.5, Gradle (Kotlin DSL)
- Spring Modulith: module verification, `@ApplicationModuleListener`, event publication registry in the database (events survive restarts)
- Oracle Database 23 (Free), Liquibase (YAML changelogs, `dbms`-specific changesets)
- Spring Data JPA, Hibernate Envers for the audit trail
- Spring Security OAuth2 resource server with Keycloak; realm roles are mapped to Spring authorities
- RabbitMQ (topic exchange for notifications)
- Quartz with a clustered JDBC job store
- RFC 7807 problem details for every error
- Java records for DTOs and events, no Lombok, hand-written mappers
- JUnit 5, ArchUnit, Spring Modulith tests, WireMock, Testcontainers (Oracle), H2 in Oracle mode

## Modules

| Module | Responsibility |
|---|---|
| `application` | submit, cancel, approve and reject applications; work queue for staff; Envers history |
| `scoring` | calls the credit bureau, applies the scoring policy, stores a scoring report |
| `loan` | loan creation, disbursement, annuity and differentiated schedules, repayments, overdue job |
| `notification` | turns domain events into messages on the `loanflow.notifications` exchange |
| `shared` | security, error handling, auditing, money helpers (open module) |

Every module keeps its implementation in `internal` and `web` sub-packages. Other modules can only use the types in the module's root package: events such as `ApplicationApproved`, and the `LoanApplications` interface. `ModularityTest` fails the build if a module reaches into another module's internals or if a dependency cycle appears.

## Business rules

**Scoring** (`ScoringPolicy`)

| | Reject | Manual review |
|---|---|---|
| Debt-to-income incl. the new payment | > 50% | > 40% |
| Bureau score | < 550 | < 650 or no history |
| Delinquencies in the last 12 months | > 2 | 1–2 |
| Amount | > 36 × monthly income | > 50,000 AZN |

If none of these apply, the application is approved automatically and goes to a loan officer for final approval. Manual reviews can only be approved by a risk manager.

The interest rate depends on the score: 14% from 750, 16.5% from 680, 19% from 600, otherwise 22%.

**Status flow:**
`SUBMITTED → PENDING_APPROVAL | UNDER_REVIEW | REJECTED → APPROVED | REJECTED`. An application can be `CANCELLED` by the applicant at any time before the final decision.

**Loans.** An approved application becomes a loan in `AWAITING_DISBURSEMENT`. When the loan is disbursed, the schedule is built and the first payment is due one month later.

**Repayments** go first to penalties, then to interest, then to principal, starting with the oldest installment. Paying more than the outstanding amount is rejected. Paying everything closes the loan.

**Overdue job.** A Quartz job runs every night at 01:00 Baku time:
- it marks late installments as `OVERDUE`;
- it charges a penalty of 0.1% per day on the overdue amount;
- it moves the loan to `OVERDUE`, or to `DEFAULTED` once a payment is more than 90 days late.

Running it twice on the same day does not charge the penalty twice.

## Running

```bash
docker compose up --build
```

The first start of the Oracle container takes about a minute.

| | |
|---|---|
| API / Swagger UI | http://localhost:8080/swagger-ui.html |
| Keycloak | http://localhost:8180 (admin / admin) |
| RabbitMQ | http://localhost:15672 (guest / guest), messages land in `loanflow.notifications.archive` |
| Credit bureau stub | http://localhost:8090/reports/5ZK7M2P |

Users in the `loanflow` realm: `leyla` / `leyla123` and `tural` / `tural123` (customers), `officer` / `officer123`, `risk` / `risk123`.

```bash
token() {
  curl -s localhost:8180/realms/loanflow/protocol/openid-connect/token \
    -d grant_type=password -d client_id=loanflow-api -d username=$1 -d password=$2 | jq -r .access_token
}
CUSTOMER=$(token leyla leyla123); OFFICER=$(token officer officer123)

APPLICATION_ID=$(curl -s localhost:8080/api/applications -H "Authorization: Bearer $CUSTOMER" -H 'Content-Type: application/json' -d '{
  "finCode": "5ZK7M2P", "amount": 5000, "termMonths": 12, "purpose": "CONSUMER",
  "repaymentType": "ANNUITY", "monthlyIncome": 2500, "monthlyDebt": 0 }' | jq -r .id)

curl -X POST localhost:8080/api/applications/$APPLICATION_ID/approve -H "Authorization: Bearer $OFFICER"
curl localhost:8080/api/loans/mine -H "Authorization: Bearer $CUSTOMER"
```

The bureau stub knows three FIN codes: `5ZK7M2P` (good history), `7QW2E9R` (goes to manual review) and `3ZX4C5V` (rejected). Any other FIN code is treated as an applicant with no credit history.

## Errors

```json
{
  "type": "https://loanflow.local/problems/invalid-status",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Application is REJECTED",
  "instance": "/api/applications/1021/approve",
  "code": "INVALID_STATUS"
}
```

Validation errors add an `errors` array with `field` and `message`.

## Tests

```bash
./gradlew test
```

- `ModularityTest` verifies the module structure and writes PlantUML diagrams to `build/spring-modulith-docs`.
- `ArchitectureTest` checks a few ArchUnit rules: controllers live in `web`, no field injection, and so on.
- `LoanflowFlowTest` runs the whole flow on H2 (Oracle mode) with a WireMock credit bureau: scoring, both approval paths, a bureau outage with rescoring, disbursement, repayment, the overdue job and the audit history.
- `OracleIntegrationTest` runs the same lifecycle on Oracle Free in Testcontainers. Without Docker it is skipped.
