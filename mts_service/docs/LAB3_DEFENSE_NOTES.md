# Лабораторная работа №3 — материалы к защите

Документ собирает в одном месте всё, что нужно для защиты: пошаговый разбор
асинхронного воркфлоу подачи заявки (включая новый async-флоу создания Taiga
user story), гарантии доставки сообщений, использование JMS и Quartz,
гарантии корректности интеграции с Taiga через JCA, а также чек-лист
соответствия `docs/task.md`.

---

## 1. Чек-лист соответствия `docs/task.md`

| Требование | Реализация | Статус |
| --- | --- | --- |
| Асинхронное выполнение задач по модели "очередь сообщений" | Transactional outbox + JMS-очереди для approval/connection/taiga-sync | ✅ |
| Провайдер — RabbitMQ | `docker/rabbitmq/definitions.json`, брокер `amqp://localhost:5672` | ✅ |
| Отправка — AMQP 1.0 (любая библиотека) | Apache Qpid JMS Client (`org.apache.qpid.jms.JmsConnectionFactory`) + плагин `rabbitmq_amqp1_0` | ✅ |
| Получение — `@JmsListener` на Spring Boot | `ApprovalCommandListener`, `ConnectionCommandListener`, `TaigaSyncCommandListener` | ✅ |
| Обработка на двух независимых узлах | `docker-compose.yml`, профиль `app-cluster`: `mts_service_node1` (8081), `mts_service_node2` (8082), общая БД и RabbitMQ | ✅ |
| Распределённые транзакции, если сценарий требует транзакционности | Narayana JTA, `XADataSource`, `TransactionAwareConnectionFactoryProxy` — JMS receive + DB write в одной XA-транзакции (`JtaConfig`, `JmsMessagingConfig`) | ✅ |
| Quartz для согласованных периодических прецедентов | `QuartzSchedulerConfig`: dispatch outbox (2с), recovery застрявших сообщений (60с), очистка inbox (1ч) | ✅ |
| Интеграция с EIS через JCA | `integration/jca/*` (Taiga Resource Adapter), используется `TaigaTaskService` | ✅ |
| Изменения бизнес-процесса отражены в модели, REST API и тест-скриптах | `docs/TAIGA_APPLICATION_WORKFLOW.md` обновлён (новый статус `PENDING_TAIGA_SYNC`, асинхронный флоу создания заявки), Postman-коллекции (`MTS_JMS_E2E`, `MTS_Taiga_Integration`, `MTS_Auth_Tests`) | ✅ |

Все формальные требования задания выполнены. Ниже — подробности по каждому пункту.

---

## 2. Воркфлоу подачи заявки (полный цикл)

### 2.1 Диаграмма состояний заявки

```
POST /applications
        │
        ▼
PENDING_TAIGA_SYNC ──(async: outbox → JMS → TaigaSyncCommandListener)──▶ PENDING
        │                                                                   │
        │ (Taiga недоступна > max-delivery-attempts)                       │ webhook
        ▼                                                                   │ New -> In progress
  FAILED_EXTERNAL                                                           ▼
                                                                         PROCESSING
                                                                             │
                                                                             │ webhook
                                                                             │ In progress -> Ready for test
                                                                             ▼
                                                       ┌── баланс < цены ──▶ REJECTED (Archived в Taiga)
                                                       │
                                                    APPROVED ──(async: outbox → JMS → ConnectionCommandListener)──▶ CONNECTED
                                                                                                                    (Taiga -> Done)
```

### 2.2 Шаг 1 — создание заявки (`POST /applications`)

Главная цель доработки: **создание заявки в БД и создание карточки (user
story) в Taiga разнесены по разным транзакциям**, чтобы:

1. не держать XA-транзакцию БД на время блокирующего HTTP-вызова в Taiga;
2. не оставлять «осиротевшую» карточку в Taiga, если коммит заявки не пройдёт.

`ApplicationService.create` (`src/main/java/ru/aigul/mts_service/service/ApplicationService.java:65-113`):

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public ApplicationDto create(String email, ApplicationCreateDto dto) {
    User user = userService.findByEmail(email)
            .orElseThrow(() -> new UserNotFoundException(email));

    Tariff tariff = tariffRepository.findById(dto.getTariffId())
            .orElseThrow(() -> new TariffNotFoundException(dto.getTariffId()));

    // ... расчёт totalPrice по тарифу и дополнительным услугам ...

    BigDecimal availableBalance = localBillingService.getBalance(user);
    if (availableBalance.compareTo(totalPrice) < 0) {
        throw new InsufficientFundsException();
    }

    Application application = applicationEntityMapper.fromCreateDto(
            user, tariff, dto, totalPrice, new HashSet<>(additionalServices));

    application = applicationRepository.save(application);

    outboxService.enqueueTaigaStoryRequested(new TaigaStoryRequestedMessage(
            UUID.randomUUID().toString(), application.getId(), null, OffsetDateTime.now()));

    return applicationMapper.toDto(application);
}
```

Что происходит:

- Заявка сохраняется со статусом **`PENDING_TAIGA_SYNC`** и `taigaTaskId = null`
  (`ApplicationEntityMapper.fromCreateDto` проставляет этот статус по умолчанию).
- В **той же** БД-транзакции в таблицу `outbox_messages` добавляется запись
  `TaigaStoryRequestedMessage` — запись заявки и постановка задачи в outbox
  коммитятся атомарно.
- HTTP-ответ клиенту возвращается **сразу**, без ожидания Taiga.

### 2.3 Шаг 2 — Quartz-диспетчер вычитывает outbox и шлёт сообщение в RabbitMQ

`OutboxDispatcher.dispatch()` запускается каждые 2 секунды
(`app.quartz.outbox.dispatch-interval-ms`):

```java
public void dispatch() {
    while (true) {
        List<OutboxMessageStateService.DispatchItem> batch = outboxMessageStateService.claimBatch();
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxMessageStateService.DispatchItem item : batch) {
            sendOne(item);
        }
    }
}

private void sendOne(OutboxMessageStateService.DispatchItem item) {
    try {
        Class<?> payloadClass = Class.forName(item.payloadType());
        Object payload = objectMapper.readValue(item.payloadJson(), payloadClass);
        jmsTemplate.convertAndSend(item.destination(), payload);
        outboxMessageStateService.markSent(item.id());
    } catch (Exception ex) {
        outboxMessageStateService.handleFailure(item.id(), item.attempts(), ex);
    }
}
```

Сообщение `TaigaStoryRequestedMessage` отправляется в очередь
`/queues/application.taiga-sync.requested` через `JmsTemplate` (AMQP 1.0,
Qpid JMS Client → RabbitMQ).

### 2.4 Шаг 3 — `TaigaSyncCommandListener` принимает сообщение

`src/main/java/ru/aigul/mts_service/messaging/TaigaSyncCommandListener.java`:

```java
@JmsListener(destination = "${app.messaging.taiga-sync.queue-address}", containerFactory = "jmsListenerContainerFactory")
public void onTaigaStoryRequested(TaigaStoryRequestedMessage message,
                                   @Header(name = "JMSXDeliveryCount", required = false) Integer deliveryCount) {
    try {
        log.info("JMS Taiga sync message consumed: messageId={}, applicationId={}, deliveryCount={}",
                message.messageId(), message.applicationId(), deliveryCount);
        asyncTaigaSyncProcessingService.process(message, deliveryCount);
    } catch (Exception ex) {
        log.warn("Taiga sync message processing failed: messageId={}, applicationId={}, deliveryCount={}",
                message.messageId(), message.applicationId(), deliveryCount, ex);
        throw ex;
    }
}
```

`containerFactory = "jmsListenerContainerFactory"` — это контейнер,
настроенный на **JTA-транзакцию** (`JtaConfig`/Narayana). Получение сообщения
из RabbitMQ и все последующие изменения в БД образуют одну распределённую
(XA) транзакцию: либо всё закоммитится (сообщение подтверждено + изменения в
БД сохранены), либо всё откатится (сообщение вернётся в очередь, изменения в
БД не применятся).

### 2.5 Шаг 4 — идемпотентность через inbox

`AsyncTaigaSyncProcessingService.process(...)`:

```java
@Transactional
public void process(TaigaStoryRequestedMessage message, Integer deliveryCount) {
    if (!messageInboxService.register(message.messageId(), "taiga-sync-listener")) {
        log.info("Duplicate Taiga sync message skipped: messageId={}, applicationId={}",
                message.messageId(), message.applicationId());
        return;
    }

    int attempt = deliveryCount != null ? deliveryCount : 1;
    log.info("Received TaigaStoryRequested message: applicationId={}, attempt={}",
            message.applicationId(), attempt);
    taigaSyncWorkflowService.createStoryForApplication(message.applicationId(), attempt);
}
```

`messageInboxService.register(...)` делает `INSERT ... ON CONFLICT DO NOTHING`
по уникальной паре `(message_id, consumer)`. Если запись уже существует —
сообщение уже было обработано, повторная обработка пропускается.

### 2.6 Шаг 5 — создание user story в Taiga (через JCA)

`ApplicationTaigaSyncWorkflowService.createStoryForApplication(...)`
(`src/main/java/ru/aigul/mts_service/service/ApplicationTaigaSyncWorkflowService.java`):

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void createStoryForApplication(Long applicationId, int deliveryAttempt) {
    Application application = applicationRepository.findByIdForUpdate(applicationId)
            .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

    if (application.getTaigaTaskId() != null) {
        log.info("Taiga story already exists for application {}, skipping", applicationId);
        if (application.getStatus() == ApplicationStatus.PENDING_TAIGA_SYNC) {
            application.setStatus(ApplicationStatus.PENDING);
            applicationRepository.save(application);
        }
        return;
    }

    if (application.getStatus() != ApplicationStatus.PENDING_TAIGA_SYNC) {
        log.info("Application {} no longer awaits Taiga sync (status={}), skipping",
                applicationId, application.getStatus());
        return;
    }

    try {
        Optional<Long> taigaTaskId = taigaTaskService.createUserStoryForApplication(application);
        if (taigaTaskId.isEmpty()) {
            throw new TaigaIntegrationException("Taiga task was not created for applicationId=" + applicationId);
        }
        application.setTaigaTaskId(taigaTaskId.get());
        application.setStatus(ApplicationStatus.PENDING);
        applicationRepository.save(application);
        log.info("Taiga story created for application {}: taigaTaskId={}", applicationId, taigaTaskId.get());
    } catch (TaigaIntegrationException ex) {
        if (deliveryAttempt >= maxDeliveryAttempts) {
            log.error("Giving up Taiga story creation for application {} after {} attempts: {}",
                    applicationId, deliveryAttempt, ex.getMessage(), ex);
            application.setStatus(ApplicationStatus.FAILED_EXTERNAL);
            application.setRejectReason("Taiga integration unavailable: " + ex.getMessage());
            applicationRepository.save(application);
            return;
        }
        throw ex;
    }
}
```

Особенности:

- **Пессимистическая блокировка строки** (`findByIdForUpdate`,
  `@Lock(LockModeType.PESSIMISTIC_WRITE)`) — защищает от гонки, если два
  узла приложения одновременно получат повторно доставленное сообщение.
- **Идемпотентность относительно повторов**:
  - если `taigaTaskId` уже заполнен (story создана, но коммит предыдущего
    раза не успел проставить статус) — повторный вызов в Taiga **не**
    делается, только статус доводится до `PENDING`;
  - если статус уже не `PENDING_TAIGA_SYNC` (кто-то другой уже обработал) —
    выходим без побочных эффектов.
- **Терминальная ошибка после N попыток**: если Taiga недоступна
  `app.messaging.taiga-sync.max-delivery-attempts` раз (по умолчанию 5),
  заявка переводится в `FAILED_EXTERNAL` с заполненным `rejectReason`,
  видимым через `GET /applications/{id}`, и в лог пишется `ERROR`.
- Если ошибка не терминальная — `throw ex` приводит к откату JTA-транзакции,
  сообщение **не подтверждается** (no ack) и RabbitMQ повторно доставит его
  (`JMSXDeliveryCount` увеличивается).

### 2.7 Дальнейшие шаги (без изменений в этой лабораторной)

После того как `taigaTaskId` заполнен и статус — `PENDING`, остальной флоу
работает как и раньше (см. `docs/TAIGA_APPLICATION_WORKFLOW.md`):

- webhook `New -> In progress` → `PROCESSING`;
- webhook `In progress -> Ready for test` → проверка баланса → списание →
  `APPROVED` → `ConnectionRequestedMessage` в outbox/JMS;
- `ConnectionCommandListener` выполняет подключение → `CONNECTED`, карточка
  Taiga переводится в `Done`.

---

## 3. Гарантии доставки сообщений

### 3.1 Паттерн Transactional Outbox (продюсер)

Запись в outbox-таблицу (`outbox_messages`) делается **в той же
бизнес-транзакции**, что и изменение состояния (например, сохранение
заявки). Это гарантирует: если транзакция закоммитилась — задача точно
будет отправлена; если откатилась — задачи не будет вовсе.

`OutboxService` (`messaging/outbox/OutboxService.java`):

```java
@Transactional(propagation = Propagation.MANDATORY)
public void enqueueTaigaStoryRequested(TaigaStoryRequestedMessage message) {
    persist(taigaSyncQueueAddress, "taiga-story-requested", message.messageId(), message);
}

private void persist(String destination, String eventType, String messageId, Object payload) {
    OutboxMessage outboxMessage = new OutboxMessage();
    outboxMessage.setMessageId(messageId);
    outboxMessage.setDestination(destination);
    outboxMessage.setEventType(eventType);
    outboxMessage.setPayloadType(payload.getClass().getName());
    outboxMessage.setPayloadJson(objectMapper.writeValueAsString(payload));
    outboxMessage.setStatus(OutboxMessageStatus.NEW);
    outboxMessage.setAttempts(0);
    outboxMessage.setNextAttemptAt(OffsetDateTime.now());
    outboxMessageRepository.save(outboxMessage);
}
```

`Propagation.MANDATORY` намеренно: метод обязан вызываться внутри уже
открытой транзакции (если транзакции нет — ошибка), чтобы запись в outbox
никогда не закоммитилась отдельно от бизнес-изменения.

### 3.2 Диспетчер с retry / backoff / recovery (доставка at-least-once)

`OutboxMessageStateService`:

- `claimBatch()` — выбирает до 20 сообщений со статусом `NEW`, у которых
  `nextAttemptAt <= now`, переводит их в `PROCESSING`, увеличивает `attempts`.
- `markSent(id)` — после успешной отправки в JMS помечает `SENT`.
- `handleFailure(id, attempts, ex)` — при ошибке отправки:
  - если `attempts >= MAX_ATTEMPTS` (10) → статус `FAILED` (конечный, без
    дальнейших попыток);
  - иначе → статус снова `NEW`, `nextAttemptAt = now + backoff`, где backoff
    растёт экспоненциально (`10 * 2^(attempts-1)`, максимум 900с — 15 минут).
- `recoverStaleProcessing()` — отдельная Quartz-задача (раз в 60с): если
  сообщение «зависло» в `PROCESSING` дольше `PROCESSING_TIMEOUT` (5 минут) —
  например, узел упал между отправкой в JMS и записью `markSent` — статус
  возвращается в `NEW`, чтобы сообщение было отправлено повторно.

Эта связка даёт гарантию **at-least-once**: сообщение точно будет
отправлено хотя бы один раз (и потенциально — несколько раз при сбоях).

### 3.3 Паттерн Transactional Inbox (idempotent consumer)

Чтобы повторная доставка (at-least-once) не привела к повторной обработке
(например, повторному созданию карточки в Taiga или повторному списанию
денег), используется inbox-таблица `message_inbox` с уникальным
constraint'ом `(message_id, consumer)`:

`MessageInboxRepository`:

```java
@Modifying
@Query(value = """
        INSERT INTO message_inbox (message_id, consumer, expires_at)
        VALUES (:messageId, :consumer, :expiresAt)
        ON CONFLICT (message_id, consumer) DO NOTHING
        """, nativeQuery = true)
int insertIfAbsent(@Param("messageId") String messageId,
                   @Param("consumer") String consumer,
                   @Param("expiresAt") OffsetDateTime expiresAt);
```

`MessageInboxService.register(...)` возвращает `true`, только если строка
была реально вставлена (`insertIfAbsent == 1`). Если сообщение с таким
`messageId` для этого `consumer` уже было — `false`, и обработчик
(`AsyncTaigaSyncProcessingService.process`, аналогично для approval/connection)
просто выходит без побочных эффектов.

Запись в inbox делается **в той же транзакции**, что и бизнес-логика
обработки — поэтому невозможна ситуация «inbox обновили, а бизнес-логику
не выполнили» (или наоборот).

### 3.4 Итоговая гарантия: effectively-once

- **Outbox** гарантирует, что задача не потеряется (at-least-once на отправку).
- **Inbox** гарантирует, что повторная доставка не приведёт к повторному
  эффекту (дедупликация на стороне потребителя).
- **JTA** гарантирует атомарность связки "снять сообщение из очереди + inbox
  + бизнес-изменения в БД" — либо всё, либо ничего.

Вместе это даёт **effectively-once** обработку поверх ненадёжного
at-least-once транспорта.

### 3.5 Очистка inbox

`MessageInboxCleanupJob.cleanupExpired()` (Quartz, раз в час) удаляет записи
inbox старше `app.messaging.inbox.retention-hours` (24 часа по умолчанию),
чтобы таблица не росла бесконечно — после истечения retention повторная
доставка маловероятна (TTL очереди/брокера значительно меньше).

---

## 4. Использование JMS

### 4.1 Транспорт: AMQP 1.0 через Apache Qpid JMS Client → RabbitMQ

`JmsMessagingConfig`:

```java
@Bean
public ConnectionFactory jmsConnectionFactory(
        @Value("${app.messaging.broker-url}") String brokerUrl,
        @Value("${app.messaging.username}") String username,
        @Value("${app.messaging.password}") String password) {
    return new JmsConnectionFactory(username, password, brokerUrl);
}
```

`org.apache.qpid.jms.JmsConnectionFactory` — реализация JMS API поверх
протокола AMQP 1.0. На стороне RabbitMQ включён плагин
`rabbitmq_amqp1_0`, очереди объявлены в `docker/rabbitmq/definitions.json`.

### 4.2 Продюсер: `JmsTemplate` + кастомный `MessageConverter`

```java
@Bean(name = "cachedJmsConnectionFactory")
public CachingConnectionFactory cachedJmsConnectionFactory(ConnectionFactory jmsConnectionFactory) {
    CachingConnectionFactory factory = new CachingConnectionFactory(jmsConnectionFactory);
    factory.setSessionCacheSize(10);
    factory.setCacheConsumers(true);
    factory.setCacheProducers(true);
    return factory;
}

@Bean(name = "transactionAwareJmsConnectionFactory")
public ConnectionFactory jmsConnectionFactoryProxy(@Qualifier("cachedJmsConnectionFactory") ConnectionFactory cachedJmsConnectionFactory) {
    return new TransactionAwareConnectionFactoryProxy(cachedJmsConnectionFactory);
}

@Bean
public JmsTemplate jmsTemplate(
        @Qualifier("transactionAwareJmsConnectionFactory") ConnectionFactory jmsConnectionFactoryProxy,
        MessageConverter jmsMessageConverter) {
    JmsTemplate template = new JmsTemplate(jmsConnectionFactoryProxy);
    template.setMessageConverter(jmsMessageConverter);
    template.setPubSubDomain(false);
    return template;
}
```

`MessageConverter` сериализует payload в JSON через Jackson и кладёт
полное имя Java-класса в заголовок сообщения `_type` — это позволяет
`OutboxDispatcher` и слушателям десериализовать сообщение в правильный DTO
(`TaigaStoryRequestedMessage`, `ApprovalRequestedMessage`,
`ConnectionRequestedMessage`) без отдельного реестра типов.

### 4.3 Консьюмеры: три `@JmsListener`

| Listener | Очередь | DTO | Обработчик |
| --- | --- | --- | --- |
| `ApprovalCommandListener` | `application.approval.requested` | `ApprovalRequestedMessage` | `AsyncApprovalProcessingService` |
| `ConnectionCommandListener` | `application.connection.requested` | `ConnectionRequestedMessage` | `AsyncConnectionProcessingService` |
| `TaigaSyncCommandListener` | `application.taiga-sync.requested` | `TaigaStoryRequestedMessage` | `AsyncTaigaSyncProcessingService` |

Все три используют один и тот же `jmsListenerContainerFactory`:

```java
@Bean
public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
        @Qualifier("transactionAwareJmsConnectionFactory") ConnectionFactory jmsConnectionFactoryProxy,
        MessageConverter jmsMessageConverter,
        PlatformTransactionManager transactionManager) {
    DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
    factory.setConnectionFactory(jmsConnectionFactoryProxy);
    factory.setMessageConverter(jmsMessageConverter);
    factory.setTransactionManager(transactionManager);
    factory.setConcurrency("1-2");
    return factory;
}
```

`transactionManager` здесь — это **JTA**-менеджер (Narayana), см. ниже.
`setConcurrency("1-2")` — от 1 до 2 потоков-консьюмеров на каждый listener,
что также является базой для распределённой обработки на двух узлах
(`mts_service_node1`/`node2` оба слушают одни и те же очереди — RabbitMQ
балансирует сообщения между ними).

### 4.4 Распределённые транзакции (JTA/XA) для JMS-приёма

`JtaConfig`:

```java
@Bean
@Primary
public JtaTransactionManager transactionManager(jakarta.transaction.TransactionManager narayanaTransactionManager,
                                                jakarta.transaction.UserTransaction narayanaUserTransaction) {
    JtaTransactionManager jtaTm = new JtaTransactionManager();
    jtaTm.setTransactionManager(narayanaTransactionManager);
    jtaTm.setUserTransaction(narayanaUserTransaction);
    jtaTm.setAllowCustomIsolationLevels(true);
    return jtaTm;
}
```

Это Narayana — Java Transaction API (JTA) реализация распределённых
(двухфазных, XA) транзакций. Два ресурса участвуют в одной транзакции:

1. **БД** — через XA `DataSource` (`XaDataSourceWrapper`,
   `spring.datasource.xa.*`).
2. **JMS-соединение** — через `TransactionAwareConnectionFactoryProxy`,
   которая делает AMQP-сессию XA-ресурсом.

`@JmsListener`-метод, выполняющийся в `jmsListenerContainerFactory`,
работает в JTA-транзакции: получение сообщения (consume) из RabbitMQ и все
JPA-операции (`@Transactional` внутри `AsyncTaigaSyncProcessingService.process`,
которая вызывается из `onTaigaStoryRequested`) подтверждаются/откатываются
**атомарно через двухфазный коммит**. Если упадёт после записи в БД, но до
ack сообщения (или наоборот) — XA-координатор гарантирует консистентность:
оба ресурса либо закоммитят, либо оба откатят свою часть.

---

## 5. Quartz: какие джобы есть и зачем

`QuartzSchedulerConfig` регистрирует три периодические задачи:

| Job | Метод | Интервал | Назначение |
| --- | --- | --- | --- |
| `outboxDispatchJobDetail` / `outboxDispatchTrigger` | `OutboxDispatcher.dispatch()` | 2 сек (`app.quartz.outbox.dispatch-interval-ms`) | Забирает порции `NEW`-сообщений из outbox и отправляет их в JMS (RabbitMQ). Это основной "мотор" асинхронной обработки — именно он превращает запись в outbox-таблице в реальное JMS-сообщение. |
| `outboxRecoveryJobDetail` / `outboxRecoveryTrigger` | `OutboxDispatcher.recoverStaleProcessing()` | 60 сек (`app.quartz.outbox.recovery-interval-ms`) | Возвращает в `NEW` сообщения, застрявшие в `PROCESSING` дольше 5 минут (например, узел упал между отправкой и фиксацией статуса) — защита от "потерянных" задач. |
| `inboxCleanupJobDetail` / `inboxCleanupTrigger` | `MessageInboxCleanupJob.cleanupExpired()` | 1 час (`app.quartz.inbox.cleanup-interval-ms`) | Удаляет из `message_inbox` записи старше `app.messaging.inbox.retention-hours` (24ч) — иначе таблица дедупликации растёт неограниченно. |

Конфигурация (`QuartzSchedulerConfig`):

```java
@Bean(name = "outboxDispatchJobDetail")
public MethodInvokingJobDetailFactoryBean outboxDispatchJobDetail(OutboxDispatcher outboxDispatcher) {
    MethodInvokingJobDetailFactoryBean job = new MethodInvokingJobDetailFactoryBean();
    job.setTargetObject(outboxDispatcher);
    job.setTargetMethod("dispatch");
    job.setConcurrent(false);
    return job;
}

@Bean
public SimpleTriggerFactoryBean outboxDispatchTrigger(
        @Qualifier("outboxDispatchJobDetail") JobDetail outboxDispatchJobDetail) {
    SimpleTriggerFactoryBean trigger = new SimpleTriggerFactoryBean();
    trigger.setJobDetail(outboxDispatchJobDetail);
    trigger.setStartDelay(2000L);
    trigger.setRepeatInterval(outboxDispatchIntervalMs);
    trigger.setRepeatCount(SimpleTrigger.REPEAT_INDEFINITELY);
    return trigger;
}
```

`setConcurrent(false)` — важно: на каждом узле job не запускается
параллельно с самим собой (нет гонки внутри одного `SchedulerFactoryBean`).
Между двумя узлами гонка возможна (оба запускают свой Quartz-scheduler), но
она безопасна за счёт `claimBatch()`/`recoverStaleProcessing()`, работающих
через обычные `@Transactional`-блоки с UPDATE статусов — конкурентные узлы
просто возьмут разные строки или последовательно обработают одни и те же
(идемпотентно за счёт `attempts`/`status`).

---

## 6. Гарантии корректности интеграции с Taiga (JCA)

### 6.1 Архитектура JCA-коннектора

`integration/jca/*`:

- `TaigaResourceAdapter` — точка входа JCA Resource Adapter.
- `TaigaManagedConnectionFactory` / `TaigaManagedConnection` — управление
  соединениями (создание, пул, метаданные через
  `TaigaManagedConnectionMetaData`).
- `TaigaConnectionFactory` / `TaigaConnectionImpl` /
  `TaigaConnectionSpec` — прикладной интерфейс, через который
  `TaigaTaskService` вызывает методы Taiga API (создание user story, смена
  статуса, комментарии).

`TaigaTaskService` использует этот коннектор как обычную "точку
интеграции" с EIS — вся работа с HTTP/токенами Taiga инкапсулирована за
JCA-интерфейсом (`TaigaConnection`).

### 6.2 Корректность создания user story (идемпотентность + терминальные ошибки)

См. §2.6 — `ApplicationTaigaSyncWorkflowService.createStoryForApplication`:

- **Не более одной карточки на заявку**: проверка `taigaTaskId != null`
  до вызова Taiga исключает дублирование карточек при повторной доставке.
- **Пессимистическая блокировка** (`findByIdForUpdate`) исключает
  одновременную обработку одного и того же `applicationId` двумя узлами.
- **Конечное число попыток**: после `max-delivery-attempts` (5) неудачных
  попыток заявка не остаётся "вечно висящей" в `PENDING_TAIGA_SYNC` —
  переводится в терминальный статус `FAILED_EXTERNAL` с понятной причиной
  в `rejectReason`, видимой через REST API.

### 6.3 Корректность webhook-обработки (обратное направление Taiga → MTS)

`TaigaWebhookService` + проверка подписи (`app.taiga.webhook.secret`):

- запрос проверяется по HMAC-подписи — исключает обработку поддельных
  webhook'ов;
- переходы валидируются по таблице разрешённых переходов
  (`docs/TAIGA_APPLICATION_WORKFLOW.md`, раздел "Разрешённые переходы") —
  запрещённые переходы откатываются обратно в Taiga с комментарием;
- поиск заявки по `taigaTaskId` через `findByTaigaTaskIdForUpdate`
  (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) — та же защита от гонки, что и
  при создании.

### 6.4 Защита от двойного списания

Двухуровневая защита (актуально для approval-флоу, не специфично для
сегодняшней доработки, но часть общей картины "гарантии корректности"):

- приложение проверяет `existsByApplicationIdAndType(... DEBIT)` перед
  списанием;
- на уровне БД — частичный уникальный индекс
  `ux_billing_tx_application_debit` на `billing_transactions(application_id)
  WHERE type='DEBIT'` (миграция V18) — защищает даже при гонке между узлами.

---

## 7. Распределённая обработка на двух узлах

`docker-compose.yml`, профиль `app-cluster`:

```yaml
mts_service_node1:
  profiles: ["app-cluster"]
  build:
    context: .
    dockerfile: Dockerfile.local
  container_name: mts_service_node1
  environment:
    SERVER_PORT: 8081
    SPRING_DATASOURCE_URL: jdbc:postgresql://mts_db:5432/mts_db

mts_service_node2:
  profiles: ["app-cluster"]
  build:
    context: .
    dockerfile: Dockerfile.local
  container_name: mts_service_node2
  environment:
    SERVER_PORT: 8082
    SPRING_DATASOURCE_URL: jdbc:postgresql://mts_db:5432/mts_db
```

Оба узла:

- подключаются к одной БД (`mts_db`) и одному RabbitMQ;
- запускают одинаковый набор `@JmsListener`'ов на одни и те же очереди —
  RabbitMQ распределяет сообщения между потребителями (round-robin);
- запускают собственный Quartz-scheduler — `OutboxDispatcher.dispatch()`
  выполняется на каждом узле независимо, но безопасно за счёт
  `claimBatch()`/блокировок в БД (см. §5).

Это и есть требуемая "обработка сообщений на двух независимых узлах сервера
приложений", а распределённая транзакционность каждого узла обеспечена JTA
(Narayana) — см. §4.4.

---

## 8. Код-ревью: проверка избыточных комментариев

Проведён обзор всех `.java`-файлов на наличие `TODO/FIXME/XXX`,
закомментированного кода и неинформативных комментариев.

**Результат**: избыточных/устаревших комментариев и закомментированного
кода не найдено. Новые файлы, добавленные для асинхронного флоу
(`TaigaStoryRequestedMessage`, `ApplicationTaigaSyncWorkflowService`,
`AsyncTaigaSyncProcessingService`, `TaigaSyncCommandListener`, изменения в
`OutboxService`/`ApplicationService`), написаны без единого комментария —
имена классов/методов самодокументированы.

Существующие комментарии в проекте — все по делу, объясняют "почему", а не
"что":

- `model/Tariff.java`, `dto/catalog/TariffDetailDto.java`,
  `dto/TariffCreateRequest.java` — группирующие комментарии
  (`// Поля для HOME и BUSINESS`, `// MOBILE`, `// DEVICES`) — облегчают
  навигацию по большим DTO/моделям с разнородными группами полей;
- `repository/TariffRepository.java` — комментарии объясняют, зачем нужен
  `JOIN FETCH` (избежание N+1) и логика фильтрации по цене города;
- `config/FlywayConfig.java:27` — `// Repair checksum mismatches before
  migration` — объясняет неочевидный шаг `flyway.repair()`;
- `config/JaasSecurityConfig.java` — комментарии объясняют разбор паттернов
  публичных эндпоинтов и логирование principal — неочевидная логика
  security-конфигурации;
- `integration/taiga/config/TaigaAuthTokenManager.java`,
  `integration/taiga/config/TaigaClientConfig.java` — javadoc объясняет
  причину авто-рефреша токена и retry на 401 — неочевидное поведение,
  важное для понимания интеграции;
- `integration/jca/TaigaManagedConnectionMetaData.java`,
  `integration/jca/TaigaConnectionSpec.java` — короткие javadoc на
  JCA SPI-классах, описывающие их роль в контракте JCA (стандартная
  практика для Resource Adapter SPI-классов, не избыточны).

Изменений по результатам ревью не требуется.

---

## 9. Шпаргалка для проверки на демо

1. `POST /applications` с валидными данными → ответ `status=PENDING_TAIGA_SYNC`,
   `taigaTaskId=null`.
2. Через ~2-3 секунды `GET /applications/{id}` (или `GET
   /api/demo/state/{id}`) → `status=PENDING`, `taigaTaskId` заполнен, в
   Taiga появилась карточка в колонке `New`.
3. Негативный кейс: временно "сломать" `app.taiga.api-token` или
   `app.taiga.base-url`, создать заявку → в логах видно
   `JMSXDeliveryCount` растущий 1..5, после 5-й попытки —
   `ERROR ... Giving up Taiga story creation ...`, статус заявки —
   `FAILED_EXTERNAL`, `rejectReason` заполнен.
4. Демонстрация Quartz: остановить RabbitMQ на ~1 минуту, создать заявку,
   снова поднять RabbitMQ — `outboxDispatchTrigger` (2с) подхватит
   сообщение из outbox автоматически, без перезапуска приложения.
5. Демонстрация распределённой обработки: подняться с профилем
   `app-cluster` (`docker compose --profile app-cluster up`), отправить
   несколько заявок — в логах обоих узлов (`node1`/`node2`) видно, что
   `TaigaSyncCommandListener` срабатывает на разных узлах для разных
   сообщений.
6. Полный сквозной сценарий — Postman-коллекция
   `docs/MTS_JMS_E2E.postman_collection.json`, шаги 2 и 2b демонстрируют
   именно переход `PENDING_TAIGA_SYNC -> PENDING`.
