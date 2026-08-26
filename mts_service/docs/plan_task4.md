# План реализации ЛР №4: миграция MTS Service на BPM-движок Camunda

## Контекст

Задание (`docs/task4.md`) требует переработать приложение из ЛР №3 так, чтобы бизнес-процессом
управлял BPM-движок **Camunda**, запущенный в режиме **standalone-сервиса**. «Статическая»
бизнес-логика (жёстко зашитая в Java-сервисах оркестрация и state machine заявки) заменяется
на «динамическую» — описанную в **BPMN 2.0** и исполняемую движком. При этом ВСЕ подсистемы из
ЛР №3 должны быть сохранены и интегрированы с движком как «точки интеграции»: асинхронный обмен
(RabbitMQ/AMQP 1.0 + JMS), периодические задачи (Quartz), JCA-коннектор к Taiga, JTA/Narayana,
ролевой доступ (JAAS), Elasticsearch. UI задачи согласования генерируется **генератором форм
Camunda**. Итоговая сборка — **WAR на Apache Tomcat** (helios; в рамках этого плана —
локальный эквивалент через Docker/Tomcat).

**Текущее состояние.** Spring Boot 4.0.2 / Java 17, Gradle, сейчас `bootJar` + Docker (2 ноды).
Ядро — жизненный цикл «Заявки» (`Application`), разнесённый по сервисам-оркестраторам:
`PENDING_TAIGA_SYNC → PENDING → PROCESSING → APPROVED → CONNECTED` + ветки `REJECTED` /
`FAILED_EXTERNAL`. Переходы статусов сейчас «зашиты» в Java (валидации в workflow-сервисах) —
именно это переносится в BPMN.

**Принятые решения (согласованы с пользователем).**
1. Архитектура: **remote engine + External Tasks** — Camunda 7 отдельным сервисом, MTS как WAR
   интегрируется через External Task client + REST/message correlation. Разрывает версии,
   обходит несовместимость Camunda со Spring Boot 4, чисто отвечает требованию «standalone».
2. Глубина: **полный перенос оркестрации в BPMN**. Движок владеет потоком и статусами; Java-слой
   сводится к тонким worker'ам и адаптерам, переиспользующим существующие leaf-реализации
   (списание, JCA-вызов Taiga, отправка JMS) — подсистемы сохраняются, переписывается оркестрация.
3. Развёртывание: локально/Docker как эквивалент helios; реальный деплой описывается инструкцией.

---

## Целевая архитектура

```
┌────────────────────────┐         REST (start/correlate)        ┌─────────────────────────┐
│   MTS Service (WAR)     │ ───────────────────────────────────▶ │  Camunda 7 (standalone) │
│   на Apache Tomcat      │ ◀─── External Task fetch&lock ─────── │  Run / Tomcat-дистриб.  │
│                         │                                        │  Engine REST API        │
│  ┌──────────────────┐   │                                        │  Cockpit / Tasklist /   │
│  │ External Task    │   │  service tasks: createTaigaStory,      │  Admin + Camunda Forms  │
│  │ workers          │───┼─▶ debitBalance, sendConnectionCmd,     │                         │
│  └──────────────────┘   │   syncTaigaStatus, connect             │  своя БД/схема          │
│  ┌──────────────────┐   │                                        └─────────────────────────┘
│  │ Существующие     │   │   точки интеграции (СОХРАНЯЮТСЯ):
│  │ подсистемы:      │   │   • JMS/RabbitMQ + Outbox/Inbox  • Quartz (infra)
│  │ Billing, Taiga   │   │   • JCA → Taiga                  • JTA/Narayana (XA)
│  │ JCA, JMS, JAAS,  │   │   • JAAS-роли ↔ Camunda groups   • Elasticsearch
│  │ Quartz, ES       │   │
│  └──────────────────┘   │
└────────────────────────┘
```

**Принцип владения данными:** процесс Camunda — источник истины по *потоку управления*;
колонка `applications.status` обновляется worker'ами как read-model (для `DemoStateController`,
списков, отчётности). На старте процесса в переменные кладутся `applicationId`, `userId`,
`tariffId`, `address`, `lockedPrice`, `additionalServices`, `correlationId`.

---

## Модель бизнес-процесса (BPMN 2.0)

**Процесс `applicationProcess`** (основной, авторинг в Camunda Modeler):

1. **Start event** — стартует REST-вызовом из `ApplicationService.create()` (после сохранения заявки).
2. **Service task (external) `createTaigaStory`** → worker зовёт `TaigaTaskService` через JCA.
   Boundary error event: после N ретраев → end event `FAILED_EXTERNAL`. Успех → статус `PENDING`.
3. **User task `Обработка заявки`** (кандидатная группа `MANAGER`) — **форма Camunda** с полями
   `decision` (approve/reject), `rejectReason`, просмотр данных заявки. Соответствует `PROCESSING`.
   Boundary **message event** от Taiga-webhook (Kanban-переходы In Progress / Ready For Test /
   Archived коррелируются в процесс — заменяют старое прямое управление статусами из webhook).
4. **Exclusive gateway** по `decision`:
   - **approve** → **Service task (external) `debitBalance`** (зовёт `LocalBillingService.debit`).
     Gateway «средств хватает?»: нет → `REJECTED` (+ `syncTaigaStatus`→Archived); да → `APPROVED`.
   - **reject** → `REJECTED` → **Service task `syncTaigaStatus`** (Archived).
5. **APPROVED** → **Send task `sendConnectionCommand`** (через `OutboxService`/JMS) →
   **Receive/message catch `connectionDone`** (коррелируется по завершении JMS-обработки) →
   **Service task `connect`** (статус `CONNECTED`) → **Service task `syncTaigaStatus`** (Done) → end.

**Процесс `periodicReconciliation`** (демонстрация периодики через движок): **timer start event**
(cycle) → service task сверки/напоминания по «зависшим» заявкам. Инфраструктурные Quartz-джобы
(outbox dispatch/recovery, inbox cleanup) остаются как есть — это плумбинг, не бизнес-логика;
бизнес-периодику показываем таймером Camunda. (Задание явно НЕ требует переносить распределённую
обработку и распределённые транзакции — оставляем их в приложении.)

---

## Карта точек интеграции (что сохраняем и как подключаем к движку)

| Подсистема | Сохраняемые классы | Интеграция с Camunda |
|---|---|---|
| **Async/JMS** | `JmsMessagingConfig`, `messaging/**` (Outbox/Inbox, listeners) | service task `sendConnectionCommand` шлёт через `OutboxService`; JMS-consumer'ы по завершении коррелируют **message** обратно в процесс |
| **Quartz** | `QuartzSchedulerConfig`, `OutboxDispatcher`, `MessageInboxCleanupJob` | оставить инфра-джобы; бизнес-периодику — timer-процесс в Camunda |
| **JCA → Taiga** | `integration/jca/**`, `TaigaTaskService`, `TaigaWebhookService` | external-task worker'ы зовут `TaigaTaskService`; webhook → **message correlation** в `applicationProcess` |
| **JTA/Narayana** | `JtaConfig`, `PrimaryDataSourceConfig`, `XaDataSourceWrapper` | остаётся в приложении; worker'ы исполняются в JTA-транзакциях приложения (движок — своя БД) |
| **JAAS-роли** | `JaasSecurityConfig`, `auth/**`, `Role`, `Privilege` | REST остаётся под JAAS; роли `USER/MANAGER/ADMIN` маппятся на Camunda groups; user task — candidate group `MANAGER` |
| **Elasticsearch** | `search/**` | без изменений |

**Переписываемая оркестрация (полный перенос):** логика и валидации переходов из
`ApplicationApprovalWorkflowService`, `ApplicationConnectionWorkflowService`,
`ApplicationTaigaSyncWorkflowService`, `TaigaApplicationWorkflowService` — переносится в BPMN
(шлюзы/события/таймеры). Сами leaf-операции (`LocalBillingService.debit`,
`TaigaTaskService.createUserStoryForApplication/moveUserStoryToStatus`, `OutboxService.enqueue*`)
**переиспользуются** из external-task worker'ов, а не переписываются.

---

## Критичные файлы

**Сборка/деплой:**
- `build.gradle` — `war`-плагин, `providedRuntime` embedded-tomcat, зависимости
  `camunda-external-task-client` (+ REST-клиент); исключить `devtools`/`docker-compose` из WAR.
- `src/main/java/ru/aigul/mts_service/MtsServiceApplication.java` — `extends SpringBootServletInitializer`.
- `Dockerfile` / `docker-compose.yml` — добавить сервис Camunda (своя БД), Tomcat-образ для WAR.
- `application.properties` — externalize `ObjectStore` и `users.xml` (вне webapps), `context-path`,
  URL движка, очереди.

**Новый интеграционный слой (создать):**
- `integration/camunda/` — `CamundaProcessClient` (старт процесса/message correlation по REST),
  external-task worker'ы: `CreateTaigaStoryWorker`, `DebitBalanceWorker`,
  `SendConnectionCommandWorker`, `ConnectWorker`, `SyncTaigaStatusWorker`.
- Адаптация `ApplicationService.create()` — вместо enqueue в Outbox стартовать процесс Camunda.
- Адаптация `messaging/consumer/**` и `TaigaWebhookService` — вместо прямых вызовов
  workflow-сервисов делать **message correlation** в процесс.

**Модели/ресурсы (создать):**
- `src/main/resources/processes/applicationProcess.bpmn`, `periodicReconciliation.bpmn`.
- Форма Camunda для user task (Camunda Forms `.form` или embedded form).

**Документация (создать/обновить):**
- `docs/task4.md` — отчёт (текст задания, описание изменений с аргументацией, выводы).
- `docs/diagrams/applicationProcess.bpmn` (модель потока управления) + экспорт SVG.
- `docs/diagrams/blps.drawio` — архитектурная схема с указанием точек интеграции BPMS.
- Обновить `application_submission_flow.puml`, `usecase.puml`.

---

## Объём работ и оценка (идеальные дни)

| # | Этап | Оценка |
|---|---|---|
| 1 | Camunda standalone: docker-сервис, отдельная БД/схема, webapps, проверка REST API | 0.5 |
| 2 | Репакеджинг в WAR + Tomcat: `SpringBootServletInitializer`, externalize путей, context-path, проверка Narayana/JAAS/ES под Tomcat | 1.5 |
| 3 | BPMN-модель `applicationProcess` в Camunda Modeler (+ периодический процесс) | 1.0 |
| 4 | Генератор форм Camunda для user task согласования | 0.5 |
| 5 | Интеграционный слой: External Task client, worker'ы, REST-старт, message correlation | 2.0 |
| 6 | Полный перенос оркестрации: убрать state-machine из workflow-сервисов, подключить worker'ы; адаптировать JMS-consumer'ы и Taiga-webhook на correlation | 2.0 |
| 7 | Маппинг ролей JAAS ↔ Camunda groups/authorization | 0.5 |
| 8 | Периодика: оставить инфра-Quartz, добавить timer-процесс | 0.5 |
| 9 | E2E-тестирование (Postman + Tasklist + Cockpit), обновление скриптов | 1.0 |
| 10 | Документация и диаграммы (BPMN, архитектура с точками интеграции, выводы) | 1.0 |
| | **Итого** | **≈10.5 дня** (диапазон 9–13 с учётом рисков) |

---

## Риски и митигирование

| Риск | Серьёзн. | Митигирование |
|---|---|---|
| **Spring Boot 4.0.2** несовместим с Camunda-стартером | Высокая | Выбран remote engine + External Task client (чистый Java/HTTP) — встроенный стартер не используется |
| **WAR под Tomcat:** Narayana как embedded TM, права на `ObjectStore`, исключение dev-зависимостей, embedded-tomcat → provided | Высокая | Ранний spike деплоя (этап 2 в начале); externalize путей; смоук-тест XA-транзакции в Tomcat до основной работы |
| **Двойной источник истины** (Camunda flow vs `applications.status`) | Средняя | Процесс — владелец потока; статус-колонка обновляется worker'ами как read-model; сверка в `DemoStateController` |
| **Taiga webhook ↔ message correlation** (Kanban-переходы, backward-переходы, идемпотентность) | Средняя | Бизнес-ключ корреляции = `applicationId`; переиспользовать Inbox-дедуп; явные сообщения на каждый Kanban-статус |
| **Race/идемпотентность** worker'ов и существующих гарантий (pessimistic lock, debit-идемпотентность) | Средняя | External Task fetch&lock + сохранённая идемпотентность `debit`/Inbox; ретраи Camunda |
| **Распределёнка (2 ноды)** vs external-task workers | Низкая | Конкурирующие worker'ы поддерживаются движком из коробки |
| **helios-доступ/версия Tomcat** | Низкая (отложено) | Демонстрация локально/Docker; helios — инструкция |

---

## Проверка (E2E)

1. **Поднять стек:** `docker-compose up` — Postgres, RabbitMQ, **Camunda standalone**, Tomcat с MTS WAR.
2. **Камунда жива:** открыть Cockpit/Tasklist/Admin; задеплоить `applicationProcess.bpmn`.
3. **Happy path:** `POST /applications` (роль USER) → в Cockpit виден инстанс на `createTaigaStory`
   → стори в Taiga создана, статус `PENDING` → в Tasklist у `MANAGER` появилась задача с
   **сгенерированной формой** → approve → worker `debitBalance` списал средства (проверить
   `GET /api/demo/state/{id}`, `consistent=true`) → `APPROVED` → JMS connection → message
   correlation → `CONNECTED` → Taiga переведён в Done.
4. **Ветки:** недостаток средств → `REJECTED` (Taiga→Archived); reject из формы; сбой Taiga →
   `FAILED_EXTERNAL`; Taiga-webhook (In Progress/Archived) двигает токен процесса.
5. **Периодика:** timer-процесс срабатывает по расписанию (видно в Cockpit); инфра-Quartz
   (outbox/inbox) продолжает работать.
6. **Роли:** USER не видит задачи менеджера; `MANAGER` видит и закрывает user task.
7. **Регрессия:** прогнать Postman-коллекции (`MTS_JMS_E2E`, `MTS_Taiga_Integration`,
   `MTS_Auth_Tests`), обновив сценарии под старт/корреляцию через Camunda.
8. **Сборка:** `./gradlew war` собирает деплоибельный WAR; разворачивается в Tomcat без ошибок старта.
