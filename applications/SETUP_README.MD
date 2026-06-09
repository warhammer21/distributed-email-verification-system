# Email Verifier – Local Development Setup

## 1. Start Docker services

From the project root:

```bash
docker compose up -d
```

This starts:

* PostgreSQL
* RabbitMQ
* RabbitMQ Management UI

---

## 2. Verify Docker containers are running

```bash
docker ps
```

You should see containers similar to:

* email-verifier-database
* email-verifier-rabbit

---

## 3. RabbitMQ Management UI

Open:

```text
http://localhost:15672
```

Default credentials:

```text
username: guest
password: guest
```

---

## 4. Start Fake SendGrid server

In terminal #1:

```bash
./gradlew platform-support:fake-sendgrid:run

```

Expected port:

```text
http://localhost:9090
```

---

## 5. Start Notification Server

In terminal #2:

```bash
./gradlew applications:notification-server:run
```

Expected behavior:

* connects to PostgreSQL
* listens for RabbitMQ notifications
* forwards emails to fake-sendgrid

---

## 6. Start Registration Server

In terminal #3:

```bash
./gradlew applications:registration-server:run
```

Expected port:

```text
http://localhost:8081
```

---

# Full Request Flow

```text
Client Request
    ↓
Registration Server
    ↓
RabbitMQ Exchange
    ↓
RabbitMQ Queue
    ↓
Notification Server Consumer
    ↓
Generate Confirmation Code
    ↓
Save Request to Database
    ↓
Publish Notification Message
    ↓
Fake SendGrid
    ↓
Email Sent
```

---

# Test Registration Request

Send request:

```bash
curl -X POST http://localhost:8081/request-registration \
  -H "Content-Type: application/json" \
  -d '{"email":"jenny@example.com"}'
```

Expected result:

```text
204 No Content
```

---

# Verify Flow

## RabbitMQ UI

Open:

```text
http://localhost:15672
```

You should see:

* exchanges
* queues
* message traffic

---

## Notification Server Logs

Should show:

```text
received registration request
sending notification
```

---

## Fake SendGrid Logs

Should show:

```json
{
  "personalizations": [{"to":[{"email":"jenny@example.com"}]}],
  "subject": "Confirmation code"
}
```

---

# Useful Commands

## Stop all containers

```bash
docker compose down
```

---

## Rebuild containers

```bash
docker compose up -d --build
```

---

## View Docker logs

```bash
docker logs email-verifier-rabbit
```

```bash
docker logs email-verifier-database
```

---

# Common Problems

## PostgreSQL connection refused

Usually means Docker containers are not running.

Check:

```bash
docker ps
```

---

## RabbitMQ NOT_FOUND exchange/queue errors

Usually means:

* RabbitMQ restarted
* exchanges/queues were not re-declared
* services started in wrong order

Fix:

1. restart registration server
2. restart notification server

---

## Port already in use

Check processes:

```bash
lsof -i :8081
lsof -i :9090
lsof -i :5672
lsof -i :15672
```

Kill process:

```bash
kill -9 <PID>
```

---

# Recommended Startup Order

1. docker compose up -d
2. fake-sendgrid
3. notification-server
4. registration-server

This ensures exchanges and queues are declared correctly before consumers attach.
