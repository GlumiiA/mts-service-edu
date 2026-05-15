# Руководство по реализации интеграции с 1C

## 1. Архитектура решения

### Компоненты системы
```
┌─────────────────────────────────┐
│     MTS Service (Java)           │
│  ┌────────────────────────────┐  │
│  │  OneCIntegrationService    │  │
│  │  - Управление sync records │  │
│  │  - Обработка ошибок        │  │
│  └────────────────────────────┘  │
│           ↓ ↑                      │
│  ┌────────────────────────────┐  │
│  │  JCA (Jakarta Connectors)  │  │
│  │  - OneCCCIRecord           │  │
│  │  - OneCCCIInteraction      │  │
│  │  - OneCCCIInteractionSpec  │  │
│  └────────────────────────────┘  │
│           ↓ ↑                      │
│  ┌────────────────────────────┐  │
│  │  HTTP Client (WebFlux)     │  │
│  │  REST API calls to 1C      │  │
│  └────────────────────────────┘  │
│                                    │
│  ┌────────────────────────────┐  │
│  │  Quartz Scheduler (JDBC)   │  │
│  │  - Retry Job (5 min)       │  │
│  │  - Reconciliation Job      │  │
│  │    (30 min)                │  │
│  │  - Clustering support      │  │
│  └────────────────────────────┘  │
│                                    │
│  ┌────────────────────────────┐  │
│  │  PostgreSQL (Primary DB)   │  │
│  │  - one_c_sync_history      │  │
│  │  - one_c_errors            │  │
│  │  - rejected_applications   │  │
│  │  - QRTZ_* (Quartz tables)  │  │
│  └────────────────────────────┘  │
└─────────────────────────────────┘
         ↓ ↑
┌─────────────────────────────────┐
│      1C System (External)        │
│  REST API / WebService           │
└─────────────────────────────────┘
```

### Обработка жизненного цикла заявки

```
1. User создаёт заявку
↓
2. Application service вызывает OneCIntegrationService.submitApplicationToOneC()
↓
3. Создаётся OneCyncHistory (PENDING)
↓
4. OneCIntegrationService.performSync() вызывает JCA:
   - OneCCCIInteractionSpec("CREATE_APPLICATION")
   - OneCCCIRecord с payload
   - Отправляется в 1C API
↓
5. Ответ от 1C:
   
   Успех → externalId получена → SUCCESS
   ↓
   one_c_sync_history.externalId = "1C-APP-XXX"
   one_c_sync_history.syncStatus = SUCCESS
   one_c_sync_history.lastSyncAt = now()
   
   Недостаточно средств → INSUFFICIENT_FUNDS
   ↓
   Exception: InsufficientFundsException
   → handleInsufficientFundsError()
   → one_c_sync_history.syncStatus = INSUFFICIENT_FUNDS
   → RejectedApplication created
   → manualReviewRequired = true
   → User notification sent
   
   Ошибка 1C → OneCException → RETRY
   ↓
   syncRecord.syncStatus = RETRY
   syncRecord.retryCount++
   syncRecord.nextRetryAt = now + 2^retryCount seconds
   OneCError created (error_detail = VALIDATION_ERROR)
   
   Network timeout → RuntimeException → RETRY
   ↓
   syncRecord.syncStatus = RETRY
   syncRecord.retryCount++
   syncRecord.nextRetryAt = now + 2^(retryCount-1) seconds (faster)
   OneCError created (error_detail = TIMEOUT/NETWORK_ERROR)

6. Quartz Job: OneCIntegrationRetryJob (every 5 min)
   ↓
   findPendingAndReadyForRetry(now) - находит записи где
   - syncStatus IN (PENDING, RETRY)
   - nextRetryAt <= now
   ↓
   Для каждой записи:
   - if retryCount >= maxRetries: → DLQ (Dead Letter Queue)
   - else: retrySync() → performSync() снова
   ↓
   При ошибке: retryCount++, nextRetryAt обновилась

7. Quartz Job: OneCStatusReconciliationJob (every 30 min)
   ↓
   checkStatusWithOneC(syncRecord) - проверка у 1C
   ↓
   Если запись зависла > 24 часа:
   - Попытка получить текущий статус из 1C
   - Update lastSyncAt

8. Если maxRetries превышен → DLQ
   ↓
   sendToDeadLetterQueue(syncRecord)
   → one_c_sync_history.syncStatus = DLQ
   → Требуется administrator intervention
   → Manual review через API
```

## 2. REST API endpoints

### Синхронизация

#### GET /api/one-c/sync/application/{applicationId}
Получить историю синхронизации для заявки.

```bash
curl -H "X-Mock-User: admin@local" \
  "http://localhost:8080/api/one-c/sync/application/123?page=0&size=20"
```

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": 1,
      "applicationId": 123,
      "externalId": "1C-APP-2024050114123456",
      "syncStatus": "SUCCESS",
      "syncDirection": "TO_1C",
      "retryCount": 0,
      "maxRetries": 5,
      "lastError": null,
      "lastSyncAt": "2024-05-14T10:15:23.456+00:00",
      "nextRetryAt": null,
      "createdAt": "2024-05-14T10:15:20.000+00:00",
      "updatedAt": "2024-05-14T10:15:23.000+00:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

#### GET /api/one-c/sync/external/{externalId}
Получить синхронизацию по внешнему ID 1C.

```bash
curl -H "X-Mock-User: admin@local" \
  "http://localhost:8080/api/one-c/sync/external/1C-APP-2024050114123456"
```

### Управление ошибками

#### GET /api/one-c/errors/stats
Статистика ошибок по типам.

```bash
curl -H "X-Mock-User: admin@local" \
  "http://localhost:8080/api/one-c/errors/stats"
```

**Response (200 OK):**
```json
[
  ["INSUFFICIENT_FUNDS", 15],
  ["NETWORK_ERROR", 3],
  ["TIMEOUT", 2],
  ["VALIDATION_ERROR", 1]
]
```

### Управление отклонениями

#### GET /api/one-c/rejected
Все отклоненные заявки, требующие manual review.

```bash
curl -H "X-Mock-User: admin@local" \
  "http://localhost:8080/api/one-c/rejected?page=0&size=20"
```

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": 5,
      "applicationId": 100,
      "syncHistoryId": 3,
      "rejectionReason": "Insufficient funds in 1C system",
      "errorCode": "INSUFFICIENT_FUNDS",
      "manualReviewRequired": true,
      "reviewedAt": null,
      "reviewedBy": null,
      "createdAt": "2024-05-14T10:16:00.000+00:00"
    }
  ],
  "totalElements": 15,
  "totalPages": 1
}
```

#### GET /api/one-c/rejected/count
Быстрая проверка количества на review.

```bash
curl -H "X-Mock-User: admin@local" \
  "http://localhost:8080/api/one-c/rejected/count"
```

**Response (200 OK):**
```json
{"pendingReviewCount": 15}
```

#### GET /api/one-c/rejected/application/{applicationId}
Все отклонения для конкретной заявки.

```bash
curl -H "X-Mock-User: admin@local" \
  "http://localhost:8080/api/one-c/rejected/application/100"
```

#### POST /api/one-c/rejected/{rejectionId}/review
Отметить как reviewed (administrator decision).

```bash
curl -X POST -H "X-Mock-User: admin@local" \
  -H "Content-Type: application/json" \
  -d '{
    "reviewedBy": "admin@local",
    "notes": "Дозвонилась до клиента, пополнил баланс в 1C",
    "approve": true
  }' \
  "http://localhost:8080/api/one-c/rejected/5/review"
```

### Ручные операции

#### POST /api/one-c/sync/{syncId}/retry
Ручной retry синхронизации по требованию администратора.

```bash
curl -X POST -H "X-Mock-User: admin@local" \
  "http://localhost:8080/api/one-c/sync/3/retry"
```

**Response (200 OK):**
```json
{"message": "Retry scheduled"}
```

## 3. Обработка ошибок

### Классификация ошибок

| Ошибка | Enum | HTTP | Action |
|--------|------|------|--------|
| Недостаточно средств в 1C | `INSUFFICIENT_FUNDS` | 402 | Reject, Manual Review |
| Timeout 1C API | `TIMEOUT` | (retry) | Retry with backoff |
| Network error | `NETWORK_ERROR` | (retry) | Retry faster |
| Validation (data) | `VALIDATION_ERROR` | (retry) | Retry with backoff |
| Unknown | `UNKNOWN_ERROR` | (retry) | Retry with backoff |

### Exponential Backoff

```
retry 0 → immediately (PENDING status)
retry 1 → now + 2^1 = 2 seconds
retry 2 → now + 2^2 = 4 seconds
retry 3 → now + 2^3 = 8 seconds
retry 4 → now + 2^4 = 16 seconds
retry 5 → now + 2^5 = 32 seconds
retry 6+ → DLQ (exceeds maxRetries=5)
```

Network errors use slightly faster backoff:
```
network_retry 1 → now + 2^0 = 1 second
network_retry 2 → now + 2^1 = 2 seconds
(etc., to recover faster from temporary network issues)
```

### Dead Letter Queue (DLQ)

Если `retryCount >= maxRetries` и все попытки неудачны:
- `syncStatus = DLQ`
- Запись перемещается в очередь для manual administrator review
- Может быть:
  - Отправлена обратно в 1C (POST /retry)
  - Отклонена (RejectedApplication)
  - Требует investigation (logs, error details)

## 4. Idempotency

### Проблема
Если сообщение обработано дважды (из-за retry, например), 1C создаст дубликат заявки.

### Решение
Используется `requestId` (UUID) в payload:

**OneCApplicationDTO:**
```java
public class OneCApplicationDTO {
    private String requestId;     // UUID.randomUUID().toString()
    private Long applicationId;
    private String userName;
    // ... другие поля
}
```

**Payload отправляемый в 1C:**
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "applicationId": 123,
  "userId": 456,
  "userName": "Ivan Ivanov",
  "tariffId": 1,
  "address": "Moscow, Str. Test, 1",
  "price": 499.00,
  "status": "PENDING",
  "timestamp": "2024-05-14T10:15:20Z"
}
```

**При обработке в 1C:**
1. 1C получает запрос с `requestId`
2. Проверяет: был ли этот `requestId` обработан ранее?
3. Если да → возвращает ранее созданный `externalId` (без создания дубликата)
4. Если нет → создаёт новую запись, возвращает новый `externalId`

**В MTS:**
- При успехе: сохраняет `externalId` в `one_c_sync_history`
- При повторной обработке: обновляет (не создаёт) запись с тем же `externalId`

## 5. Quartz Scheduling (кластеризация)

### JDBC JobStore

Благодаря JDBC JobStore в PostgreSQL, Quartz может работать в кластере:

**Таблицы:**
- `QRTZ_JOB_DETAILS` — определения job'ов
- `QRTZ_TRIGGERS` — триггеры с расписанием
- `QRTZ_SCHEDULER_STATE` — состояние каждого scheduler'а
- `QRTZ_LOCKS` — механизм блокировки (ОЧЕНЬ ВАЖНО!)

### Механизм лока

```sql
-- Когда несколько узлов хотят запустить job:
SELECT * FROM QRTZ_LOCKS 
WHERE SCHED_NAME = 'MtsServiceScheduler' 
  AND LOCK_NAME = 'TRIGGER_ACCESS'
  AND INSTANCE_NAME = ?;

-- Только один узел успешно захватит lock:
UPDATE QRTZ_LOCKS 
SET INSTANCE_NAME = 'node-1'
WHERE SCHED_NAME = 'MtsServiceScheduler' 
  AND LOCK_NAME = 'TRIGGER_ACCESS' 
  AND INSTANCE_NAME = ?  -- нужно текущее значение
```

**Результат:**
- Job запускается на одном узле
- После завершения lock освобождается
- Другой узел может запустить следующее выполнение

### Jobs

#### OneCIntegrationRetryJob
- **Расписание:** каждые 5 минут
- **Что делает:**
  1. Находит записи: `syncStatus IN (PENDING, RETRY) AND nextRetryAt <= NOW()`
  2. Для каждой: `if retryCount >= maxRetries → DLQ else retrySync()`
  3. Увеличивает `retryCount`, обновляет `nextRetryAt`
- **Гарантия:** запускается ровно один раз за период на всём кластере

#### OneCStatusReconciliationJob
- **Расписание:** каждые 30 минут
- **Что делает:**
  1. Находит stuck records: `syncStatus = PENDING AND updatedAt < (now - 24 hours)`
  2. Проверяет статус в 1C: `checkStatusWithOneC(syncRecord)`
  3. Восстанавливается от потери статуса обновлений
- **Гарантия:** запускается один раз на кластере

### Configuration

**application.properties:**
```properties
spring.quartz.job-store-type=jdbc
spring.quartz.properties.org.quartz.scheduler.isClustered=true
spring.quartz.properties.org.quartz.scheduler.clusterCheckinInterval=15000
spring.quartz.properties.org.quartz.jobStore.class=org.quartz.impl.jdbcjobstore.JobStoreTX
spring.quartz.properties.org.quartz.jobStore.driverDelegateClass=org.quartz.impl.jdbcjobstore.PostgreSQLDelegate
spring.quartz.properties.org.quartz.jobStore.tablePrefix=QRTZ_
```

**QuartzSchedulerConfig.java:**
```java
@Configuration
public class QuartzSchedulerConfig {
    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(...) {
        // Использует primary DataSource для JobStore
        // XA transaction support
        // Clustering enabled
    }
}
```

## 6. Transaction Management

Все операции в `OneCIntegrationService` аннотированы `@Transactional`:

```java
@Transactional
public void submitApplicationToOneC(Application application) {
    // 1. Create sync history record
    OneCyncHistory syncHistory = OneCyncHistory.builder()...;
    syncHistoryRepository.save(syncHistory);
    
    // 2. Call 1C API via JCA
    performSync(syncHistory, application);  // может выбросить Exception
    
    // 3. Если успех: save updated record
    syncHistoryRepository.save(syncHistory);
    
    // Если любой .save() или выб exception: ENTIRE transaction ROLLBACK
}
```

**Гарантии:**
- Либо всё сохранилось, либо ничего
- No orphaned records
- No partial updates

## 7. Monitoring & Observability

### Logs

```log
INFO OneCIntegrationService - Submitting application id=123 to 1C
DEBUG OneCIntegrationService - Created sync history record id=456
INFO OneCIntegrationService - Successfully synced application id=123 with external_id=1C-APP-xxx
WARN OneCIntegrationService - Insufficient funds for application id=123
WARN OneCIntegrationService - 1C error for application id=123: VALIDATION_ERROR
INFO OneCIntegrationRetryJob - Found 3 records ready for retry
DEBUG OneCIntegrationRetryJob - Scheduled retry for sync record id=456 at 2024-05-14T10:20:10
```

### Metrics (via Spring Actuator)

```
GET /actuator/metrics/one_c.sync.attempts
GET /actuator/metrics/one_c.sync.failures
GET /actuator/metrics/one_c.sync.retries
```

### Database Queries

```sql
-- Ожидающие ретрай
SELECT * FROM one_c_sync_history 
WHERE sync_status IN ('PENDING', 'RETRY') 
  AND next_retry_at <= NOW()  
ORDER BY next_retry_at ASC;

-- Stuck records (не обновлялись > 24 часов)
SELECT * FROM one_c_sync_history 
WHERE sync_status = 'PENDING'
  AND updated_at < NOW() - INTERVAL '24 hours';

-- Отклоненные, требующие review
SELECT * FROM rejected_applications 
WHERE manual_review_required = true
ORDER BY created_at DESC;

-- Статистика ошибок
SELECT error_detail, COUNT(*) FROM one_c_errors
GROUP BY error_detail
ORDER BY COUNT(*) DESC;
```

## 8. Testing

### Postman Collection
Файл: `docs/OneC_Integration_Tests.postman_collection.json`

**Сценарии:**
1. **Setup** — создание тестовой заявки
2. **1C Integration** — проверка синхронизации
3. **Error Handling** — различные ошибки
4. **Rejections** — управление отклонениями
5. **Retry & Reconciliation** — ручной ретрай

### End-to-End Scenario

```bash
# 1. Create application
POST /api/applications
→ applicationId = 123

# 2. Check sync status
GET /api/one-c/sync/application/123
→ syncStatus = PENDING → SUCCESS (after job runs)

# 3. If insufficient funds, check rejections
GET /api/one-c/rejected
→ RejectedApplication record found

# 4. Admin reviews
POST /api/one-c/rejected/{rejectionId}/review
Body: {"reviewedBy": "admin@local", "approve": false}

# 5. Force retry if needed
POST /api/one-c/sync/{syncId}/retry
→ retryCount reset, nextRetryAt = NOW()
→ Job picks up next cycle
```

## 9. Production Checklist

- [ ] 1C API endpoint configured and tested
- [ ] Database migrations applied (V10, V11)
- [ ] Quartz JDBC JobStore tables created
- [ ] Multiple nodes configured and tested
- [ ] Error handling tested (insufficient funds, network errors)
- [ ] Retry mechanism tested (exponential backoff)
- [ ] DLQ tested (max retries exceeded)
- [ ] Manual review workflow tested
- [ ] Idempotency verified (duplicate requests handled)
- [ ] Monitoring/logging configured
- [ ] Postman collection updated for QA
- [ ] Documentation reviewed
- [ ] Load testing performed
- [ ] Disaster recovery tested (node failure)

