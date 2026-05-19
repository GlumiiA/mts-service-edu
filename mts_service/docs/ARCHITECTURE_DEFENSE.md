# Архитектура MTS Service: JMS, Quartz, Outbox и 1C

Я добавила асинхронную обработку заявок, чтобы не держать HTTP-запрос открытым, пока идет долгий шаг или внешний вызов. `JMS` у нас передает сообщения между частями сервиса, `Quartz` запускает повторные попытки и сверку статусов, а `1C` используется как внешняя система, куда мы отправляем заявку и где потом проверяем результат. Чтобы не потерять событие между сохранением заявки и отправкой в брокер, я добавила `outbox_messages`: сначала запись сохраняется в БД, а потом отдельный процесс отправляет ее в `JMS`.

## Коротко о ролях

- `JMS` отвечает за отправку и получение сообщений.
- `Quartz` отвечает за повторные попытки интеграции с `1C` и проверку зависших записей.
- `Outbox` отвечает за то, чтобы не потерять событие между БД и `JMS`.
- `1C` у нас не открывается как веб-страница, мы работаем с ней через API.

## Общая схема

1. Клиент создает заявку через API.
2. В одной транзакции сохраняются данные заявки и запись в `outbox_messages`.
3. `OutboxDispatcher` по расписанию забирает записи со статусом `PENDING` и отправляет их в `JMS`.
4. `ApprovalCommandListener` и `ConnectionCommandListener` получают сообщение и запускают обработку.
5. Для внешней системы `OneCIntegrationService` отправляет данные в `1C` и пишет историю синхронизации.
6. `OneCIntegrationRetryJob` и `OneCStatusReconciliationJob` повторяют неудачные попытки и проверяют зависшие записи.

### Какие классы смотреть для демонстрации

- [ApplicationController.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/api/ApplicationController.java) - входной HTTP-API для заявки.
- [ApplicationService.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/service/ApplicationService.java) - создание заявки и запуск async-обработки.
- [OutboxService.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/service/OutboxService.java) - сохранение события в `outbox_messages`.
- [OutboxDispatcher.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/service/OutboxDispatcher.java) - отправка сообщений из `outbox` в `JMS`.
- [ApprovalCommandListener.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/messaging/ApprovalCommandListener.java) - получение approval-сообщения.
- [ConnectionCommandListener.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/messaging/ConnectionCommandListener.java) - получение connection-сообщения.
- [AsyncApprovalProcessingService.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/service/AsyncApprovalProcessingService.java) - запуск логики одобрения.
- [ApplicationApprovalWorkflowService.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/service/ApplicationApprovalWorkflowService.java) - основная бизнес-логика одобрения.
- [OneCIntegrationService.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/service/integration/OneCIntegrationService.java) - работа с `1C`.
- [OneCIntegrationRetryJob.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/service/integration/OneCIntegrationRetryJob.java) - повтор неудачных интеграций.
- [OneCStatusReconciliationJob.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/service/integration/OneCStatusReconciliationJob.java) - сверка зависших статусов.
- [QuartzSchedulerConfig.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/config/QuartzSchedulerConfig.java) - настройка Quartz cluster.
- [OneCQuartzJobsConfig.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/config/OneCQuartzJobsConfig.java) - регистрация job и расписания.
- [JmsMessagingConfig.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/config/JmsMessagingConfig.java) - настройка JMS.

## JMS

`JMS` нужен для передачи сообщений внутри системы. Он подходит, когда одно действие должно запустить другое, но не хочется делать все в одном синхронном запросе.

### Что делают методы `JmsMessagingConfig`

- `jmsConnectionFactory(...)` - создает подключение к брокеру RabbitMQ через JMS.
- `jmsMessageConverter(...)` - превращает Java-объекты в JSON для отправки и обратно для чтения.
- `objectMapper()` - настраивает `ObjectMapper` для работы с JSON и датами.
- `jmsTemplate(...)` - создает объект для отправки сообщений в очередь.
- `jmsListenerContainerFactory(...)` - настраивает, как приложение будет слушать очередь и обрабатывать сообщения.

### Схема JMS

- `OutboxDispatcher` отправляет `ApprovalRequestedMessage` и `ConnectionRequestedMessage` в RabbitMQ.
- `ApprovalCommandListener` и `ConnectionCommandListener` слушают эти очереди.
- Если обработка сообщения не удалась много раз, оно уходит в DLQ через `DeadLetterPublisher`.

### Какие сообщения мы отправляем

- `ApprovalRequestedMessage` - сообщение про одобрение заявки.
- `ConnectionRequestedMessage` - сообщение про следующий шаг после одобрения, то есть подключение.
- `DeadLetterMessage` - сообщение для DLQ, когда основное сообщение не удалось обработать.

### Где это видно в коде

- [ApprovalRequestedMessage.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/messaging/dto/ApprovalRequestedMessage.java)
- [ConnectionRequestedMessage.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/messaging/dto/ConnectionRequestedMessage.java)
- [DeadLetterMessage.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/messaging/dto/DeadLetterMessage.java)
- [ApprovalCommandPublisher.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/messaging/ApprovalCommandPublisher.java)
- [ApprovalCommandListener.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/messaging/ApprovalCommandListener.java)
- [ConnectionCommandListener.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/messaging/ConnectionCommandListener.java)
- [DeadLetterPublisher.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/messaging/DeadLetterPublisher.java)

### Что означает `sessionTransacted(true)`

- обработка сообщения в `@JmsListener` идет как транзакция;
- если обработчик завершился успешно, сообщение считается обработанным;
- если во время обработки выбросилось исключение, транзакция откатывается;
- тогда сообщение не считается принятым и брокер пришлет его снова.

### Что мы говорим про гарантии

- брокер не гарантирует, что сообщение будет обработано ровно один раз;
- он дает повторную доставку, если обработка упала;
- чтобы дубли не ломали логику, мы добавили проверку `messageId`;
- если сообщение не удается обработать много раз, оно уходит в `DLQ`.

## Outbox

`Outbox` нужен, чтобы не потерять событие между сохранением заявки и отправкой в очередь.

Если сохранить заявку в БД и сразу отправлять сообщение в `JMS`, есть риск разрыва: запись в БД уже есть, а сообщение еще не ушло. Если приложение упадет в этот момент, событие потеряется. `Outbox` решает это так:

- сначала событие сохраняется в `outbox_messages`;
- потом отдельный фоновый процесс читает эту таблицу;
- после успешной отправки запись получает статус `SENT`;
- если отправка не удалась, запись остается в БД и будет повторена позже.

### Что делают методы `OutboxService`

- `enqueueApprovalRequested(...)` - создает сообщение про одобрение заявки и сохраняет его в `outbox_messages`.
- `enqueueConnectionRequested(...)` - создает сообщение про подключение заявки и тоже сохраняет его в `outbox_messages`.
- `save(...)` - собирает объект `OutboxMessage` и кладет его в базу со статусом `PENDING`.
- `serialize(...)` - превращает сообщение в JSON-строку перед сохранением в outbox.

### Кто отправляет сообщения из Outbox

- `OutboxDispatcher` отправляет `ApprovalRequestedMessage` и `ConnectionRequestedMessage` в RabbitMQ.
- `DeadLetterPublisher` отправляет `DeadLetterMessage` в DLQ.
- `ApprovalCommandPublisher` тоже умеет отправлять `ApprovalRequestedMessage`, но в основной схеме важнее `OutboxDispatcher`.

### Когда это происходит

- `OutboxDispatcher` запускается по расписанию через `@Scheduled`.
- у тебя это примерно раз в секунду;
- он берет только записи со статусом `PENDING` и `nextAttemptAt <= now`;
- после отправки ставит статус `SENT`;
- если ошибка повторяется, сообщение уходит в `DEAD` и дальше в DLQ.

### Почему мы выбрали Outbox

- мы не теряем событие между БД и `JMS`;
- не надо тащить брокер в общую распределенную транзакцию;
- это проще в поддержке и надежнее при сбоях;
- данные сохраняются сразу, а сообщение отправляется отдельно и повторяется при ошибках.

## 1C

`1C` у нас не открывается как веб-страница. Мы работаем с ней через API: отправляем туда данные, получаем статус и храним историю синхронизации у себя.

### Что делает `OneCIntegrationService`

- создает запись в `one_c_sync_history`;
- отправляет данные в `1C`;
- проверяет статус уже отправленной заявки;
- сохраняет ошибки и результат синхронизации;
- ведет историю всех обменов.

### Зачем мы используем 1C

- чтобы передавать заявку во внешнюю систему;
- чтобы получать финальный статус;
- чтобы видеть причину отказа, например нехватку средств;
- чтобы хранить историю всех синхронизаций.

### Что такое зависшая заявка

Зависшая заявка - это запись по синхронизации с `1C`, у которой `syncStatus = PENDING`, но долго нет обновления.

### Откуда она берется

- `OneCStatusReconciliationJob` берет все записи со статусом `PENDING`;
- потом проверяет, что `updatedAt` старше 24 часов;
- если да, считает такую запись зависшей и вызывает проверку статуса в `1C`.

### Где это в коде

- в [OneCStatusReconciliationJob.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/service/integration/OneCStatusReconciliationJob.java) есть `SYNC_TIMEOUT_HOURS = 24`;
- он делает выборку через `syncHistoryRepository.findBySyncStatus(PENDING)`;
- дальше фильтрует записи, у которых `updatedAt` старше порога;
- для них вызывает `integrationService.checkStatusWithOneC(record)`;
- сама выборка по репозиторию находится в [OneCyncHistoryRepository.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/repository/OneCyncHistoryRepository.java).

### Что значит совсем просто

Зависшая заявка - это заявка, по которой мы отправили данные в `1C`, но долго не получили финальный статус. Тогда `Quartz` через время берет такие записи из нашей таблицы и снова проверяет их статус в `1C`.

## Quartz

`Quartz` нужен для запуска фоновых задач по расписанию. Он используется там, где надо не реагировать на один запрос, а периодически проверять состояние и повторять действие позже.

В этом проекте `Quartz` нужен для двух вещей:

- повторять неудачные синхронизации с `1C`;
- проверять заявки, которые зависли и не получили финальный статус.

### Где используется `Job`

- `OneCIntegrationRetryJob` - повтор неудачных интеграций с `1C`.
- `OneCStatusReconciliationJob` - сверка зависших статусов с `1C`.

### Где они подключаются

- `OneCQuartzJobsConfig.java` - тут создаются `JobDetail` и `Trigger`, то есть Quartz понимает, какие job запускать и по какому расписанию.
- `QuartzSchedulerConfig.java` - тут настраивается сам Quartz Scheduler и его хранение в базе.

### Как подключается Quartz cluster

- создается `SchedulerFactoryBean`;
- ему передается `DataSource` и `PlatformTransactionManager`;
- включается `setAutoStartup(true)`;
- состояние хранится в БД через JDBC JobStore;
- включается кластерный режим через:
  - `org.quartz.jobStore.isClustered = true`
  - `org.quartz.scheduler.isClustered = true`
  - `org.quartz.jobStore.clusterCheckinInterval = 15000`

### Что это дает

- можно запустить 2 инстанса приложения;
- они оба видят одну и ту же Quartz-таблицу в БД;
- одну и ту же job не должны выполнять оба узла одновременно;
- Quartz сам распределяет выполнение через блокировки в БД.

### Простая формулировка

`Quartz` нужен не для доставки JMS-сообщений, а для повторных попыток внешней интеграции с `1C`. Он помогает, если `1C` временно недоступна или статус завис. А доставку событий в очередь мы страхуем через `Outbox`.

## Транзакции

Транзакции у нас находятся в сервисах, например в `ApplicationApprovalWorkflowService` и `OutboxService`.

### Где это видно

- [ApplicationApprovalWorkflowService.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/service/ApplicationApprovalWorkflowService.java) - там `@Transactional(isolation = Isolation.REPEATABLE_READ)` вокруг всей логики одобрения.
- [ApplicationService.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/service/ApplicationService.java) - создание заявки и запуск async-обработки.
- [OutboxService.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/service/OutboxService.java) - сохранение записи в `outbox_messages`.
- [OneCIntegrationService.java](C:/Users/ibraa/project/blps_mts_service/mts_service/src/main/java/ru/aigul/mts_service/service/integration/OneCIntegrationService.java) - транзакции вокруг синхронизации с `1C`.

### Что у нас с распределенной транзакцией

- у нас есть `JtaTransactionManager` на Narayana;
- `PrimaryDataSourceConfig` и `BillingDataSourceConfig` используют `PGXADataSource`;
- это значит, что бизнес-операции между БД могут идти в одной распределенной транзакции;
- но JMS в эту же XA-транзакцию мы не включали;
- для JMS используем `Outbox`, чтобы не терять событие между БД и брокером.

### Как это звучит на защите

> В нашем проекте критичные операции с данными идут в одной распределенной транзакции через `JTA` и `XA`, а отправка сообщений в `JMS` вынесена в `Outbox`, чтобы не терять события при сбоях.

## Какие сообщения кто отправляет

### Кто отправляет

- `OutboxDispatcher` отправляет `ApprovalRequestedMessage` и `ConnectionRequestedMessage` в RabbitMQ.
- `DeadLetterPublisher` отправляет `DeadLetterMessage` в DLQ.
- `ApprovalCommandPublisher` тоже умеет отправлять `ApprovalRequestedMessage`, но в основной схеме важнее `OutboxDispatcher`.

### Кто слушает

- `ApprovalCommandListener` слушает очередь одобрения.
- `ConnectionCommandListener` слушает очередь подключения.

### Простая схема

- `OutboxDispatcher` -> RabbitMQ queue -> `ApprovalCommandListener` / `ConnectionCommandListener`
- `ListenerFailureHandler` -> `DeadLetterPublisher` -> DLQ queue

## Гарантии и сбои

### Если сломается сохранение заявки

- транзакция откатится;
- не сохранится ни заявка, ни `outbox_messages`;
- нет частично сохраненного состояния.

### Если сломается отправка в JMS

- запись останется в `outbox_messages` со статусом `PENDING`;
- отправка повторится;
- событие не теряется между БД и брокером.

### Если отвалится RabbitMQ

- сообщения останутся в `outbox_messages`;
- данные не теряются, просто отправка откладывается;
- надежность доставки зависит от настроек брокера.

### Если отвалится consumer

- `sessionTransacted(true)` откатит JMS-сессию;
- сообщение придет снова;
- это `at-least-once`, а не `exactly-once`.

### Если придет дубль

- `MessageIdempotencyService.tryRegister(...)` не даст обработать сообщение второй раз;
- дубли не запускают бизнес-логику повторно;
- защита работает там, где есть таблица обработанных сообщений.

### Если отвалится `1C`

- запись остается в истории синхронизации;
- статус меняется на `RETRY`;
- задача попробует снова;
- заявка не теряется.

### Если отвалится Quartz-задача

- повторная синхронизация и сверка статусов временно не выполняются;
- записи уже лежат в БД и не потеряются;
- восстановление просто задержится.

### Если сообщение ушло в DLQ

- проблемное сообщение перестает блокировать основную очередь;
- одна ошибка не ломает всю обработку;
- DLQ сама проблему не решает, нужен мониторинг и ручной replay.

## Короткая фраза для защиты

Можно сказать так: "Я разделила обработку заявки на несколько шагов. `JMS` передает внутренние события, `Outbox` не дает потерять сообщение между БД и очередью, а `Quartz` повторяет и сверяет интеграцию с `1C`. `1C` здесь нужна как внешняя система, поэтому результат доводится через сохранение, отправку, повторные попытки и проверку статуса."
