# Архитектура MTS Service: JMS, Quartz и интеграция с 1С

## Резюме: Что было добавлено и зачем

MTS Service представляет собой асинхронную систему обработки заявок на подключение услуг. В проекте реализованы три ключевых компонента:

### 1. **Java Message Service (JMS) с RabbitMQ**
- **Цель**: Асинхронная обработка событий жизненного цикла приложения
- **Бизнес-процессы**:
  - `APPLICATION_APPROVAL_REQUESTED` - процесс одобрения заявки
  - `APPLICATION_CONNECTION_REQUESTED` - процесс установки соединения
  - DLQ (Dead Letter Queue) для обработки ошибок
- **Преимущество**: Разделение ответственности, высокая масштабируемость, надежная доставка

### 2. **Quartz Scheduler**
- **Цель**: Периодическое выполнение фоновых задач
- **Бизнес-процессы**:
  - `OneCIntegrationRetryJob` - переотправка неудачных интеграций с 1С
  - `OneCStatusReconciliationJob` - синхронизация статуса с 1С
- **Преимущество**: Кластеризованное выполнение, гарантия single-node execution через JDBC JobStore

### 3. **Интеграция с 1С**
- **Цель**: Синхронизация данных заявок с системой 1С
- **Бизнес-процессы**:
  - Создание новых заявок в 1С
  - Проверка статуса заявок
  - Обработка различных типов ошибок (недостаток средств, сетевые ошибки)
- **Преимущество**: История всех синхронизаций, retry logic, идентичность операций

---

## 1. Java Message Service (JMS) - Полная архитектура

### 1.1 Что такое JMS?

**Java Message Service** - это набор интерфейсов для асинхронной передачи сообщений между компонентами приложения. JMS обеспечивает:

- **Асинхронность**: Отправитель не ждет ответа получателя
- **Надежность**: Гарантия доставки сообщений
- **Слабая связанность**: Компоненты не знают друг о друге
- **Масштабируемость**: Множество потребителей могут обрабатывать сообщения параллельно

### 1.2 Классы конфигурации JMS

#### **JmsMessagingConfig.java** - Центральная конфигурация

```
Отвечает за:
1. ConnectionFactory - подключение к брокеру сообщений
2. MessageConverter - сериализация/десериализация объектов
3. JmsTemplate - отправка сообщений
4. JmsListenerContainerFactory - получение сообщений
```

**Компоненты конфигурации**:

```java
@Bean
public ConnectionFactory jmsConnectionFactory() {
    // Создает подключение к RabbitMQ через AMQP 1.0
    // Используется Apache Qpid JMS client
    return new JmsConnectionFactory(username, password, brokerUrl);
}

@Bean
public MessageConverter jmsMessageConverter() {
    // Преобразует Java объекты в JSON TextMessage
    // Добавляет заголовок "_type" для десериализации
}

@Bean
public JmsTemplate jmsTemplate() {
    // Публикует сообщения в очереди
    // setPubSubDomain(false) = режим очереди, а не подписки
}

@Bean
public DefaultJmsListenerContainerFactory jmsListenerContainerFactory() {
    // Конфигурирует получение сообщений
    // sessionTransacted(true) = транзакционность
    // setConcurrency("1-4") = параллельно 1-4 потребителя
}
```

**Гарантии на этом уровне**:
- ✅ Атомарность: Сообщение либо будет отправлено полностью, либо откачено
- ✅ Сохраняемость: Брокер сохраняет сообщение на диск до подтверждения
- ✅ Переупорядочение: Порядок сообщений сохраняется в очереди
- ✅ Идентичность: Заголовок `_type` гарантирует правильную десериализацию

### 1.3 Классы Publisher и Listener

#### **ApprovalCommandPublisher.java** - Отправитель сообщений

```
Задача: Отправить сообщение о необходимости одобрения заявки

Процесс:
1. Получает JmsTemplate из контекста Spring
2. Вызывает jmsTemplate.convertAndSend(queue, message)
3. MessageConverter автоматически сериализует сообщение в JSON
4. Логирует факт отправки

Гарантии:
- Сообщение будет отправлено или будет выброшено исключение
- Не гарантирует доставку (нужен Outbox для гарантии)
```

#### **ApprovalCommandListener.java** - Получатель сообщений

```
Задача: Получить сообщение о одобрении и обработать его

Аннотация @JmsListener:
- destination = очередь для прослушивания
- containerFactory = какой контейнер использовать

Процесс получения:
1. Spring создает несколько потребителей (1-4)
2. Каждый свободный потребитель получает сообщение
3. MessageConverter десериализует JSON в объект
4. Метод выполняется в контексте транзакции

Обработка ошибок:
- MessageIdempotencyService.tryRegister() - проверка дубликатов
- ListenerFailureHandler.shouldRetryOrMoveToDlq() - повторная попытка или DLQ

Гарантии:
- Дубликаты отклоняются (благодаря MessageIdempotencyService)
- Ошибки либо повторяются, либо отправляются в DLQ
- Транзакция откачивается при исключении (sessionTransacted=true)
```

### 1.4 Уровень Outbox Pattern

#### **OutboxService.java** - Гарантия "at least once"

```
Задача: Гарантировать, что сообщение будет отправлено

Механизм (Transactional Outbox):
1. При создании заявки в одной ТРАНЗАКЦИИ:
   - Сохраняется запись в базу (APPLICATION таблица)
   - Сохраняется запись в OUTBOX таблицу со статусом PENDING
2. Если одна операция откатилась, откатилась обе
3. Сообщение гарантированно существует в БД

Гарантии:
- ✅ At-least-once: Если приложение упало перед отправкой сообщения,
  оно все равно будет отправлено после перезагрузки
- ✅ Консистентность: БД и очередь синхронизированы
- ❌ Exactly-once: Возможны дубликаты (обрабатывает получатель)
```

#### **OutboxDispatcher.java** - Доставка сообщений из Outbox

```
Процесс:
1. @Scheduled(fixedDelayString = "1000ms") периодически вызывается
2. Запрашивает все PENDING сообщения из OUTBOX таблицы
3. Для каждого сообщения:
   - Десериализует payload (JSON → Object)
   - Отправляет через JmsTemplate
   - Меняет статус на SENT
4. Если ошибка:
   - Увеличивает счетчик attempts
   - Если attempts >= maxAttempts, отправляет в DLQ
   - Иначе ставит nextAttemptAt в будущее

Гарантии:
- ✅ Дублирование: Сообщение может быть отправлено несколько раз
  (компенсируется MessageIdempotencyService у получателя)
- ✅ Порядок: Сообщения отправляются в порядке сохранения в БД
- ✅ Надежность: Даже если RabbitMQ упал, сообщение останется в БД
```

### 1.5 Уровень обработки ошибок

#### **MessageIdempotencyService.java** - Защита от дубликатов

```
Механизм:
1. Каждое полученное сообщение имеет уникальный messageId
2. При получении сообщения:
   - Проверяем, есть ли уже такой messageId в PROCESSED_MESSAGES
   - Если есть - это дубликат, пропускаем обработку
   - Если нет - сохраняем и обрабатываем

Внутренний механизм:
- PROCESSED_MESSAGES имеет UNIQUE constraint на (messageId, consumer)
- Если пытаемся дважды insert одинаковых - DataIntegrityViolationException
- Перехватываем исключение и возвращаем false

Гарантии:
- ✅ Exactly-once semantics: Каждое сообщение обрабатывается ровно один раз
```

#### **ListenerFailureHandler.java** - Обработка сбоев

```
Механизм:
1. JmsListener выбрасывает исключение
2. JMS автоматически повторяет сообщение (redelivery)
3. ListenerFailureHandler.shouldRetryOrMoveToDlq():
   - Если deliveryCount < maxDeliveryAttempts (5):
     * Логируем ошибку
     * Выбрасываем исключение для повторной попытки
   - Если deliveryCount >= 5:
     * Публикуем в DLQ (Dead Letter Queue)
     * Возвращаем false (не пытаемся еще раз)

Гарантии:
- ✅ Автоматический retry: Если произошла временная ошибка
- ✅ Изоляция: После 5 попыток сообщение не заблокирует очередь
- ✅ Мониторинг: DLQ позволяет обнаружить постоянные проблемы
```

---

## 2. Асинхронная обработка: Преимущества, недостатки, подходы

### 2.1 Преимущества асинхронной архитектуры

1. **Высокая пропускная способность**
   - Одна заявка обрабатывается несколько шагов, каждый асинхронно
   - Пока один шаг ждет ответа от 1С, другие заявки обрабатываются
   - Линейная масштабируемость: +1 сервер = +1 параллельный поток

2. **Отказоустойчивость**
   - Если 1С недоступна, заявка не теряется, а будет переотправлена позже
   - Если приложение упало, сообщения восстановятся из Outbox при перезагрузке

3. **Разделение ответственности**
   - REST API обработки заявок отделено от логики одобрения
   - Логику одобрения можно масштабировать независимо
   - Изменения в одной части не влияют на другую

4. **Буферизация нагрузки**
   - RabbitMQ буферизует сообщения на диск
   - Если приложение не может обработать сообщения сразу, они ждут
   - Система не сломается при всплеске трафика

### 2.2 Недостатки асинхронной архитектуры

1. **Сложность отладки**
   - Ошибка может произойти в другом процессе
   - Нужны хорошие логи и мониторинг

2. **Гарантии доставки требуют дополнительного кода**
   - Нужен Outbox Pattern для "at-least-once"
   - Нужен MessageIdempotencyService для "exactly-once"

3. **Задержки**
   - Сообщение обрабатывается не сразу
   - Для пользователя заявка может казаться "зависшей"

4. **Консистентность данных**
   - Несколько сервисов имеют каждый свою копию данных
   - Нужны механизмы синхронизации

### 2.3 Подходы к реализации асинхронности в проекте

#### **Подход 1: Event Sourcing (Outbox Pattern)**

```
Используется для: Создание заявок → Одобрение

Код:
1. ApplicationApprovalWorkflowService вызывает OutboxService.enqueueApprovalRequested()
2. OutboxService.enqueueApprovalRequested():
   @Transactional
   public String enqueueApprovalRequested(Long applicationId) {
       // В одной транзакции:
       // 1. Обновить APPLICATION.status = APPROVAL_REQUESTED
       // 2. Сохранить в OUTBOX
       // Если одна операция откатилась, откатилась обе
   }
3. OutboxDispatcher позже отправляет сообщение в очередь
4. ApprovalCommandListener получает и обрабатывает

Гарантии:
- At-least-once: Даже если RabbitMQ упал, сообщение в БД
- Консистентность БД: Статус заявки и наличие сообщения синхронизированы
```

#### **Подход 2: Spring @Async (для фоновых задач)**

```
Используется для: Асинхронная обработка одобрений

Код:
AsyncApprovalProcessingService.process() вызывается из слушателя JMS

Гарантии:
- Слушатель немедленно возвращает управление
- Обработка выполняется в другом потоке
- Если обработка упадет, слушатель не будет заблокирован
```

#### **Подход 3: Quartz Scheduler (для плановых задач)**

```
Используется для: Периодические retry и reconciliation

Код:
OneCIntegrationRetryJob.execute():
- Находит все PENDING/RETRY записи, готовые к повторной попытке
- Переотправляет их на синхронизацию с 1С
- Использует exponential backoff: 2^retryCount секунд

OneCStatusReconciliationJob.execute():
- Синхронизирует статусы с 1С каждые 30 минут
- Находит "зависшие" заявки (не обновлялись 24+ часа)
- Попытается повторно синхронизировать

Гарантии:
- Single-node execution: JDBC JobStore заблокирует одновременное выполнение на разных узлах
- Мониторинг: Потерянные заявки будут обнаружены и переотправлены
```

---

## 3. Спецификация Java Message Service (JMS 3.1)

### 3.1 Основные интерфейсы JMS

```java
// 1. ConnectionFactory - создает подключение к брокеру
ConnectionFactory factory = new JmsConnectionFactory(user, pass, url);
Connection connection = factory.createConnection();

// 2. Session - сессия в рамках одного подключения
Session session = connection.createSession(transacted, mode);
// transacted = true → сообщения группируются в транзакции
// mode = CLIENT_ACKNOWLEDGE → приложение подтверждает получение

// 3. Destination - тема или очередь
Queue queue = session.createQueue("/queues/approval");
Topic topic = session.createTopic("/topic/orders");

// 4. Message - само сообщение
TextMessage msg = session.createTextMessage("Hello");
msg.setStringProperty("type", "ApprovalRequested");

// 5. MessageProducer - отправитель
MessageProducer producer = session.createProducer(destination);
producer.send(message);

// 6. MessageConsumer - получатель
MessageConsumer consumer = session.createConsumer(destination);
Message recvd = consumer.receive();
consumer.acknowledge(); // Подтверждаем получение
```

### 3.2 Гарантии JMS

| Уровень | Гарантия | Применение |
|---------|----------|-----------|
| **Persistence** | Сообщение сохраняется на диск | Обязательно для очередей |
| **Transactional** | Отправка/получение либо полностью, либо откачено | sessionTransacted=true |
| **Acknowledgement** | Подтверждение получения перед удалением | CLIENT_ACKNOWLEDGE |
| **Redelivery** | После ошибки сообщение переотправляется | Настраивается в brокере |
| **Order** | Сообщения доставляются в порядке отправки | Гарантия очереди |

---

## 4. Ресурсы и сообщения JMS

### 4.1 Типы сообщений

```java
// TextMessage - строка (JSON, XML)
TextMessage msg = session.createTextMessage("{\"id\": 123}");

// BytesMessage - бинарные данные
BytesMessage msg = session.createBytesMessage();
msg.writeBytes(data);

// MapMessage - ключ-значение пары
MapMessage msg = session.createMapMessage();
msg.setString("name", "John");

// ObjectMessage - сериализованный объект (рискованно)
ObjectMessage msg = session.createObjectMessage(object);

// StreamMessage - поток данных
StreamMessage msg = session.createStreamMessage();
```

**В проекте используется**: TextMessage с JSON через Jackson

### 4.2 Свойства сообщения

```java
// Стандартные JMS свойства
msg.setJMSCorrelationID("correlation-123");  // Связь между сообщениями
msg.setJMSReplyTo(replyQueue);              // Очередь для ответа
msg.setJMSExpiration(60000);                // TTL = 60 секунд
msg.setJMSDeliveryMode(DeliveryMode.PERSISTENT); // Сохранить на диск

// Пользовательские свойства
msg.setStringProperty("_type", object.getClass().getName());
msg.setIntProperty("retryCount", 3);
msg.setLongProperty("timestamp", System.currentTimeMillis());
```

**В проекте используется**:
- `_type` - полное имя класса для десериализации
- `JMSXDeliveryCount` - сколько раз было доставлено сообщение

---

## 5. Модели взаимодействия: Очередь vs Подписка

### 5.1 Модель "Очередь" (Point-to-Point)

```
Отправитель ──→ [ОЧЕРЕДЬ] ──→ Получатель 1
             ┌─────────────────┐
             │ (сообщение       │
             │  ждет получателя)│
             └─────────────────┘
```

**Характеристики**:
- Каждое сообщение обрабатывается ДО ОДНОГО получателя
- Сообщение удаляется сразу после получения и подтверждения
- Масштабирование: Несколько получателей могут обрабатывать разные сообщения параллельно

**Использование в проекте**:
```properties
app.messaging.approval.queue-address=/queues/application.approval.requested
app.messaging.connection.queue-address=/queues/application.connection.requested
app.messaging.dead-letter.queue-address=/queues/application.workflow.dlq

# Конфигурация
template.setPubSubDomain(false);  // false = очередь, true = подписка
```

**Пример применения**:
```
Заявка создана
  ↓
Публикуем в очередь: application.approval.requested
  ↓
Несколько сервисов прослушивают эту очередь
  ↓
Первый свободный сервис получает сообщение и одобряет
  ↓
Другие сервисы НЕ получат это сообщение (оно было удалено)
```

### 5.2 Модель "Подписка" (Publish-Subscribe)

```
Отправитель ──→ [ТЕМА] ──→ Подписчик 1 (получает копию)
             ┌──────────┐
             ├──────────┤ Подписчик 2 (получает копию)
             └──────────┘
                 ↓
           Подписчик 3 (получает копию)
```

**Характеристики**:
- Каждое сообщение доставляется ВСЕМ подписчикам
- Каждый подписчик получает свою копию
- Подписчик может подписаться уже после отправки (для persistent subscriptions)

**Не используется в текущем проекте, но подходит для**:
- Уведомлений всем пользователям
- Broadcast событий

### 5.3 Сравнение

| Характеристика | Очередь | Подписка |
|----------------|---------|---------|
| Куда идет сообщение? | Одному получателю | Всем подписчикам |
| Кто обрабатывает? | Первый доступный | Все параллельно |
| Масштабирование | Горизонтальное (больше потребителей) | Вертикальное (каждый всё обрабатывает) |
| Порядок | Гарантирован | Не гарантирован |
| Идеален для | Работа, RPC | Оповещения, события |

---

## 6. Quartz Scheduler - Распределённое планирование

### 6.1 Кластеризованное выполнение

```
Узел 1 (Scheduler 1)          Узел 2 (Scheduler 2)
        │                               │
        │     JDBC JobStore              │
        │  PostgreSQL QRTZ_* таблицы    │
        │           ↕                   │
        ├───────────────────────────────┤
        │
        └─→ Кворум выбирает лидера
             (JDBC Row Lock)
             
Только ОДИН Scheduler может выполнить Job одновременно
благодаря Database Locking
```

**Таблицы Quartz**:
- `QRTZ_JOBS` - описание работ
- `QRTZ_TRIGGERS` - триггеры запуска
- `QRTZ_LOCKS` - блокировка для координации узлов
- `QRTZ_SIMPLE_TRIGGERS` - периодические триггеры

### 6.2 Конфигурация в QuartzSchedulerConfig

```properties
# Кластеризация
org.quartz.jobStore.isClustered=true
org.quartz.jobStore.clusterCheckinInterval=15000  # 15 секунд

# Блокировка на уровне БД
org.quartz.jobStore.lockHandler.class=...StdRowLockSemaphore

# Драйвер для PostgreSQL
org.quartz.jobStore.driverDelegateClass=...PostgreSQLDelegate

# Пул потоков для исполняющих работ
org.quartz.threadPool.threadCount=5
```

### 6.3 Jobs в проекте

#### **OneCIntegrationRetryJob**

```
Период: Каждые 5 минут (SimpleScheduleBuilder.simpleSchedule().withIntervalInMinutes(5))

Логика:
1. Запрос: SELECT * FROM onec_sync_history 
   WHERE sync_status = 'RETRY' 
   AND next_retry_at <= NOW()

2. Для каждой записи:
   - retryCount < maxRetries?
     YES: Переотправить в 1С, увеличить retryCount
     NO:  Отправить в DLQ

3. Exponential backoff:
   nextRetryAt = NOW() + (2^retryCount) секунд
   
   Пример:
   - 1-я попытка: повтор через 2 сек
   - 2-я попытка: повтор через 4 сек
   - 3-я попытка: повтор через 8 сек
   ... максимум 5 попыток

Гарантии:
- Даже если приложение упало, Job выполнится после перезагрузки
- Если выполнение на узле 1 началось, узел 2 подождет
- Если узел 1 упал во время выполнения, узел 2 возьмется (через 15 сек checkIn)
```

#### **OneCStatusReconciliationJob**

```
Период: Каждые 30 минут

Логика:
1. Поиск зависших заявок:
   SELECT * FROM onec_sync_history 
   WHERE sync_status = 'PENDING' 
   AND updated_at < NOW() - INTERVAL '24 hours'

2. Для каждой "зависшей" заявки:
   - Выполнить checkStatusWithOneC(record)
   - Обновить статус на основе ответа 1С

3. Bulk status check:
   integrationService.performBulkStatusCheck()

Гарантии:
- Заявка не будет потеряна за 24+ часа
- Автоматическое восстановление при восстановлении 1С
```

### 6.4 Мониторинг Quartz

```java
@Bean
public Scheduler scheduler(SchedulerFactoryBean factory) {
    Scheduler scheduler = factory.getObject();
    String instanceId = scheduler.getSchedulerInstanceId();
    // instanceId = hostname + timestamp
    // Позволяет идентифицировать узел в логах
}
```

**Пример лога**:
```
INFO  Quartz scheduler configured with JDBC JobStore (clustering enabled)
INFO  Quartz Scheduler initialized: DESKTOP-ABC1234-1694520123456
INFO  Starting 1C integration retry job - node: DESKTOP-ABC1234-1694520123456
```

---

## 7. Интеграция с 1С - Архитектура

### 7.1 Модель данных 1С

```
Application (заявка)
  ├── User ID
  ├── Tariff
  ├── Address
  └── Status

OneCyncHistory (история синхронизации)
  ├── Application ID
  ├── ExternalId (ID в системе 1С)
  ├── SyncDirection (TO_1C, FROM_1C)
  ├── SyncStatus (PENDING, RETRY, SUCCESS, INSUFFICIENT_FUNDS, DLQ)
  ├── RetryCount
  ├── NextRetryAt
  └── LastError

OneCError (ошибки 1С)
  ├── SyncHistory ID
  ├── ErrorCode (INSUFFICIENT_FUNDS, TIMEOUT, NETWORK_ERROR)
  ├── ErrorMessage
  └── Stacktrace
```

### 7.2 Процесс синхронизации

```
Шаг 1: Создание заявки в MTS Service
  Application application = new Application(...);
  applicationRepository.save(application);
  
Шаг 2: Отправка в очередь (OutboxService)
  outboxService.enqueueApprovalRequested(app.getId());
  // Сохраняет запись в OUTBOX таблицу
  
Шаг 3: Отправка из Outbox (OutboxDispatcher)
  // Периодически отправляет PENDING сообщения
  jmsTemplate.convertAndSend("/queues/application.approval.requested", message);
  
Шаг 4: Получение в слушателе (ApprovalCommandListener)
  // JMS получает сообщение
  asyncApprovalProcessingService.process(message);
  
Шаг 5: Синхронизация с 1С (OneCIntegrationService)
  oneCIntegrationService.submitApplicationToOneC(application);
  
  Создает: OneCyncHistory {
    status = PENDING,
    retryCount = 0,
    nextRetryAt = NOW()
  }
  
  Выполняет: performSync(syncHistory, application) →
    - Подготавливает payload (JSON)
    - Вызывает JCA Resource Adapter → 1С
    - Получает externalId
    - Сохраняет: status = SUCCESS, externalId = "1C-APP-..."
  
Шаг 6: Если ошибка - классификация
  
  InsufficientFundsException?
  → status = INSUFFICIENT_FUNDS
  → Создает RejectedApplication
  → Отправляет уведомление пользователю
  
  OneCException (валидация, etc)?
  → status = RETRY
  → retryCount++
  → nextRetryAt = NOW() + (2^retryCount) сек
  
  RuntimeException (сеть, timeout)?
  → status = RETRY
  → retryCount++
  → nextRetryAt = NOW() + min(60, 2^(max(0, retryCount-1))) сек
  
Шаг 7: Retry Job (Quartz, каждые 5 минут)
  
  OneCIntegrationRetryJob.execute():
    SELECT * FROM onec_sync_history
    WHERE sync_status = 'RETRY'
    AND next_retry_at <= NOW()
    
    Для каждой:
      if (retryCount >= 5) {
        status = DLQ
      } else {
        integrationService.retrySync(record)
      }
```

### 7.3 Гарантии каждого уровня

| Уровень | Гарантия | Механизм |
|---------|----------|----------|
| **Outbox** | Сообщение не потеряется | ACID транзакция БД |
| **JMS** | Доставка в правильный формат | MessageConverter с `_type` |
| **Listener** | Ровно один раз обработка | MessageIdempotencyService |
| **1C Sync** | История всех попыток | OneCyncHistory таблица |
| **Retry** | Экспоненциальный backoff | Quartz Job с calculatedNextRetry |
| **Monitoring** | Потеря не будет незаметна | OneCStatusReconciliationJob через 24ч |

---

## 8. Протоколы взаимодействия с очередями сообщений

### 8.1 AMQP (Advanced Message Queuing Protocol)

**Версия**: 1.0  
**Используется в проекте**: ✅ ДА

```properties
app.messaging.broker-url=amqp://localhost:5672
```

**Характеристики AMQP 1.0**:
- **Надежность**: Гарантирует доставку (Settled/Unsettled state)
- **Ориентированность на стандартную модель**: На основе очередей+подписок
- **Межоперационность**: Поддерживается многими брокерами (RabbitMQ, Apache Qpid, etc)
- **Производительность**: Эффективная кодировка двоичных данных

**Сравнение версий AMQP**:

| Версия | Особенности | Брокеры |
|--------|-----------|---------|
| **0.9.1** | Старая, менее стандартизирована | RabbitMQ (основная) |
| **1.0** | Стандартная ISO, more portable | RabbitMQ, Qpid, Artemis |
| **1.1, 1.2** | Новые фичи (flow control) | Artemis, современные |

**В проекте используется**: Apache Qpid JMS Client (реализация AMQP 1.0)

```java
// build.gradle
implementation 'org.apache.qpid:qpid-jms-client:2.8.0'

// ConnectionFactory создается через Apache Qpid
return new JmsConnectionFactory(username, password, brokerUrl);
```

### 8.2 MQTT (Message Queuing Telemetry Transport)

**Используется в проекте**: ❌ НЕТ

**Когда использовать**:
- IoT устройства (ограниченные ресурсы)
- Мобильные приложения
- Нужна очень низкая задержка

**Уровни QoS (Quality of Service)**:

```
QoS 0: At most once
  ┌─────────────┐
  │ Отправитель │ ──→ [сообщение] ──→ [Получатель]
  └─────────────┘     
  Гарантия: Сообщение может быть потеряно

QoS 1: At least once
  ┌─────────────┐
  │ Отправитель │ ──→ [сообщение] ──→ [Получатель]
  └─────────────┘     ↓ PUBACK ↑
  Гарантия: Может быть дубликат

QoS 2: Exactly once
  ┌─────────────┐
  │ Отправитель │ ──→ [сообщение] ──→ [Получатель]
  └─────────────┘     ↓ PUBREC ↑
                      ↓ PUBREL ↑
                      ↓ PUBCOMP ↑
  Гарантия: Ровно один раз (медленнее)
```

### 8.3 STOMP (Simple Text Oriented Messaging Protocol)

**Используется в проекте**: ❌ НЕТ

**Когда использовать**:
- Простая текстовая коммуникация
- Веб-приложения с WebSocket
- Когда нужна простота, а не производительность

**Пример STOMP фрейма**:
```
SEND
destination:/queue/orders
content-length:13

Hello, Queue!
```

### 8.4 XMPP (Extensible Messaging and Presence Protocol)

**Используется в проекте**: ❌ НЕТ

**Когда использовать**:
- Real-time чат приложения
- Рассылка уведомлений с наличием
- Требуется presence (кто онлайн)

---

## 9. RabbitMQ - Архитектура и реализация

### 9.1 Установка и запуск

```yaml
# docker-compose.yml
rabbitmq:
  image: rabbitmq:4.1-management
  environment:
    RABBITMQ_DEFAULT_USER: rabbitmq
    RABBITMQ_DEFAULT_PASS: rabbitmq
  ports:
    - "5672:5672"        # AMQP port
    - "15672:15672"      # Management UI
  command: >
    sh -c "rabbitmq-plugins enable --offline rabbitmq_amqp1_0 && rabbitmq-server"
  volumes:
    - ./docker/rabbitmq/rabbitmq.conf:/etc/rabbitmq/rabbitmq.conf:ro
    - ./docker/rabbitmq/definitions.json:/etc/rabbitmq/definitions.json:ro
```

**Management UI**: http://localhost:15672
- Username: `rabbitmq`
- Password: `rabbitmq`

### 9.2 Внутренняя архитектура RabbitMQ

```
┌─────────────────────────────────────────────────┐
│          RabbitMQ Broker (Docker контейнер)     │
├─────────────────────────────────────────────────┤
│                                                 │
│  ┌───────────────────────────────────────────┐ │
│  │         Virtual Host (/)                   │ │
│  ├───────────────────────────────────────────┤ │
│  │                                           │ │
│  │  EXCHANGES:                               │ │
│  │    • amq.direct                           │ │
│  │    • amq.topic                            │ │
│  │    • amq.fanout                           │ │
│  │                                           │ │
│  │  Binding to QUEUES:                       │ │
│  │    • /queues/application.approval         │ │
│  │    • /queues/application.connection       │ │
│  │    • /queues/application.workflow.dlq     │ │
│  │                                           │ │
│  └───────────────────────────────────────────┘ │
│                                                 │
└─────────────────────────────────────────────────┘
```

**Компоненты RabbitMQ**:

1. **Virtual Host** - изоляция (как schema в PostgreSQL)
2. **Exchange** - правила маршрутизации сообщений
3. **Queue** - хранения сообщений (очередь на диск)
4. **Binding** - связь между Exchange и Queue

### 9.3 Типы Exchange

```
1. Direct Exchange
   ┌──────────┐                         ┌────────────┐
   │          │ ──(routing_key)─────→  │  approval  │
   │ Producer │                         │   queue    │
   │          │                         └────────────┘
   └──────────┘

2. Topic Exchange
   ┌──────────┐
   │          │ ──(application.#)──→  ┌────────────┐
   │ Producer │                       │ all.queues │
   │          │                       └────────────┘
   └──────────┘

3. Fanout Exchange (broadcast)
   ┌──────────┐                  ┌──────────┐
   │          │ ──(broadcast)─→  │Queue 1   │
   │ Producer │                  └──────────┘
   │          │                  ┌──────────┐
   └──────────┘                  │Queue 2   │
                                 └──────────┘
```

**В проекте используется**: Direct Exchange (через `/queues/...` адреса)

```properties
# AMQP 1.0 через Apache Qpid автоматически маскирует exchange
# Адрес /queues/xxx означает: Direct exchange с routing_key=xxx
```

### 9.4 Конфигурация брокера

```conf
# docker/rabbitmq/rabbitmq.conf

vm_memory_high_watermark.relative = 0.6
# Остановить производителей при 60% памяти (backpressure)

channel_max = 2048
# Максимум каналов на соединение

heartbeat = 60
# Heartbeat каждые 60 сек (обнаружение мертвых соединений)

disk_free_limit.absolute = 500MB
# Оставить минимум 500MB на диске
```

```json
// docker/rabbitmq/definitions.json
{
  "queues": [
    {
      "name": "/queues/application.approval.requested",
      "durable": true,
      "arguments": {
        "x-message-ttl": 604800000  // 7 дней TTL
      }
    }
  ]
}
```

### 9.5 Поддержка JMS в RabbitMQ

**AMQP 1.0 в RabbitMQ**:

```
SpringBoot JMS Interface
        ↓
@EnableJms → Spring JMS Container
        ↓
JmsTemplate, JmsListener
        ↓
Apache Qpid JMS Client (реализация)
        ↓
AMQP 1.0 Protocol
        ↓
RabbitMQ AMQP 1.0 Plugin (rabbitmq_amqp1_0)
        ↓
RabbitMQ Core (внутренние очереди)
```

**Особенности реализации JMS в RabbitMQ**:

| Аспект | RabbitMQ реализация |
|--------|-------------------|
| **Очереди** | AMQP 1.0 Target (Direct Exchange) |
| **Подписки** | AMQP 1.0 Topic + Fanout Exchange |
| **Трансакции** | Полная поддержка AMQP TX |
| **Persisten** | По умолчанию (durable queues) |
| **TTL** | x-message-ttl + x-expires |
| **Dead Letter** | x-dead-letter-exchange |
| **Acknowledgment** | AUTO, MANUAL, DUPS_OK |

### 9.6 Точки мониторинга RabbitMQ

**Management API** (REST):
```bash
# Просмотр очередей
curl http://rabbitmq:rabbitmq@localhost:15672/api/queues | jq

# Просмотр сообщений в очереди
curl http://localhost:15672/api/queues/%2F/application.approval.requested | jq .messages_ready

# Просмотр потребителей
curl http://localhost:15672/api/consumers | jq '.[] | select(.queue.name=="application.approval.requested")'
```

**Логи RabbitMQ**:
```bash
docker logs rabbitmq | grep -E "(consumer_created|queue_declared|connection_created)"
```

**Metrics в Prometheus**:
```
rabbitmq_queue_messages_ready{queue="approval"}
rabbitmq_queue_messages_delivered{queue="approval"}
rabbitmq_channel_consumers{channel="..."}
```

---

## 10. Интеграция всех компонентов: Полный флоу

### 10.1 Сценарий: Создание и одобрение заявки

```
[1] USER создает заявку через REST API
    POST /api/applications
    
[2] API сохраняет Application в БД
    
[3] API вызывает OutboxService.enqueueApprovalRequested()
    В ОДНОЙ ТРАНЗАКЦИИ:
    - UPDATE application SET status = APPROVAL_REQUESTED
    - INSERT INTO outbox_message (eventType='APPROVAL_REQUESTED', ...)
    // Если 1 откатилась - откатилась 2
    // Если 2 откатилась - откатилась 1
    
[4] API возвращает 200 OK клиенту
    // Клиент не ждет завершения одобрения
    
[5] OutboxDispatcher (каждые 1 сек):
    SELECT FROM outbox_message WHERE status = 'PENDING'
    Для каждого:
      jmsTemplate.convertAndSend(...) 
        → MessageConverter сериализует в TextMessage
        → Отправляет через Apache Qpid
        → AMQP 1.0 →  RabbitMQ
        → Сохраняет в очередь на диск
        
    UPDATE outbox_message SET status = 'SENT'
    
[6] При потере соединения (RabbitMQ упал):
    OutboxDispatcher ловит исключение
    Ставит nextAttemptAt = NOW() + 2 сек
    Позже попытается еще раз
    
[7] RabbitMQ:
    /queues/application.approval.requested получает сообщение
    Хранит на диске (persist=true)
    
[8] JMS Client (мульти-потоковый):
    Несколько потребителей (1-4 параллельных)
    Один получает сообщение
    
[9] ApprovalCommandListener @JmsListener:
    Получает сообщение
    → MessageConverter десериализует TextMessage → ApprovalRequestedMessage
    → Проверяет: MessageIdempotencyService.tryRegister(messageId)
    
    Если дубликат: LOG и return (не обрабатываем)
    
    Если новый:
      → asyncApprovalProcessingService.process(message)
      → workflowService.approveAsynchronously(...)
      
    ✅ Сообщение JMS подтверждается (acknowledged)
    ✅ Откатывается из очереди RabbitMQ
    
[10] Если ошибка в обработке:
     try-catch в ApprovalCommandListener
     → ListenerFailureHandler.shouldRetryOrMoveToDlq()
     
     Если deliveryCount < 5:
       throw exception
       → JMS автоматически переотправляет (redelivery)
       → JMSXDeliveryCount увеличивается
       
     Если deliveryCount >= 5:
       → DeadLetterPublisher отправляет в DLQ
       → return false (больше не пытаемся)
       
[11] Мониторинг:
     Каждые 24 часа OneCStatusReconciliationJob
     Проверяет, нет ли зависших заявок
     Если есть - попытается восстановить
```

### 10.2 Сценарий: Синхронизация с 1С с фейлом

```
[1] ApprovalCommandListener вызывает:
    asyncApprovalProcessingService.process(message)
    
[2] workflowService.approveAsynchronously() вызывает:
    oneCIntegrationService.submitApplicationToOneC(application)
    
[3] submitApplicationToOneC():
    INSERT INTO onec_sync_history (
      status='PENDING',
      retryCount=0,
      maxRetries=5,
      nextRetryAt=NOW()
    )
    
[4] performSync():
    Готовит JSON payload
    Вызывает JCA Resource Adapter (или эмулирует)
    
    ❌ InsufficientFundsException!
    
[5] handleSyncError():
    INSERT INTO onec_error (
      errorCode='INSUFFICIENT_FUNDS',
      errorMessage='Insufficient funds in 1C account'
    )
    
    INSERT INTO rejected_application (
      rejectionReason='Insufficient funds',
      manualReviewRequired=true
    )
    
    UPDATE onec_sync_history SET status='INSUFFICIENT_FUNDS'
    
    // Не будет retry, требуется ручная обработка
    
[6] Альтернативный сценарий: OneCException или RuntimeException
    
    UPDATE onec_sync_history SET
      status='RETRY',
      retryCount=1,
      lastError='...',
      nextRetryAt=NOW() + (2^1) секунд = 2 сек
    
[7] Через 2 секунды:
    OneCIntegrationRetryJob находит эту запись и retry
    
    ❌ Еще ошибка!
    
    UPDATE onec_sync_history SET
      retryCount=2,
      nextRetryAt=NOW() + (2^2) = 4 сек
      
[8] Exponential backoff:
    retryCount=1 → 2 сек
    retryCount=2 → 4 сек
    retryCount=3 → 8 сек
    retryCount=4 → 16 сек
    retryCount=5 → 32 сек, затем status='DLQ'
    
[9] После DLQ:
    OneCStatusReconciliationJob (каждые 30 мин)
    Обнаружит DLQ сообщение
    Попытается ручное восстановление через 24 часа
```

---

## 11. Чеклист архитектурных гарантий

```
✅ Reliability (Надежность)
   [✓] Outbox Pattern гарантирует "at-least-once"
   [✓] MessageIdempotencyService исключает дубли
   [✓] Quartz JDBC JobStore кластеризован
   [✓] OneCyncHistory + OneCError отслеживают все попытки

✅ Performance (Производительность)
   [✓] Асинхронная обработка: REST API не ждет 1С
   [✓] JMS шкалируется: 1-4 параллельных потребителя
   [✓] Outbox batch dispatch: 20 сообщений за раз
   [✓] Exponential backoff: не перегружаем 1С при сбое

✅ Scalability (Масштабируемость)
   [✓] RabbitMQ: многих узлов, один consumer pool удаляет сообщение
   [✓] Quartz: узлы координируются через JDBC блокировку
   [✓] Несколько MTS Service узлов могут работать параллельно
   

✅ Monitoring (Мониторинг)
   [✓] Каждая синхронизация логируется в OneCyncHistory
   [✓] Ошибки сохраняются в OneCError с stacktrace
   [✓] DLQ для выявления постоянных проблем
   [✓] Reconciliation Job находит зависшие заявки

✅ Disaster Recovery (Восстановление при авариях)
   [✓] Если RabbitMQ упал: сообщения в Outbox будут переотправлены
   [✓] Если 1С не отвечает: exponential backoff не перегружает
   [✓] Если узел упал: Quartz Job подхватит другой узел
   [✓] Если заявка потеряна: reconciliation через 24 часа
```

---

## 12. Возможные улучшения и варианты реализации

### 12.1 Альтернативные подходы

**Вариант 1: Использование RabbitMQ Dead Letter Exchange вместо DLQ очереди**
```properties
# Сейчас: ListenerFailureHandler отправляет в отдельную очередь
# Лучше: Настроить RabbitMQ DLX, сообщения автоматически переходят
x-dead-letter-exchange = application.dlx
x-max-length = 1000000  # Максимум 1 млн сообщений
```

**Вариант 2: Event Sourcing (сейчас используется Outbox)**
```
Сейчас: Таблица APPLICATION + Таблица OUTBOX
Альтернатива: Одна таблица EVENT_LOG
- Все изменения логируются как events
- Из events восстанавливается текущее состояние
- Больше хозяйства, но полная история
```

**Вариант 3: Использование Sagas для распределённых транзакций**
```
Сейчас: Каждый сервис независимо обрабатывает
Альтернатива: Orchestration Saga
- Центральный координатор управляет флоу
- Компенсирующие транзакции при откате
- Сложнее, но лучше для критичных операций
```

### 12.2 Мониторинг и наблюдаемость

**Рекомендуется добавить**:

```xml
<!-- Prometheus metrics -->
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- ELK Stack для логирования -->
<!-- Jaeger для distributed tracing -->
<!-- Grafana для dashboards -->
```

---

## Заключение

Архитектура MTS Service демонстрирует профессиональный подход к асинхронной обработке сообщений:

1. **JMS + RabbitMQ** - надежная доставка сообщений
2. **Quartz Scheduler** - выполнение фоновых работ с кластеризацией
3. **Outbox Pattern** - гарантия доставки даже при сбоях
4. **Exponential Backoff** - разумная обработка ошибок
5. **Idempotency** - защита от дубликатов
6. **Monitoring** - видимость всех операций

Каждый слой имеет свои гарантии, вместе обеспечивая надежную и масштабируемую систему обработки заявок.

