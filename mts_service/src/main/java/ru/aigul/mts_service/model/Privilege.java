package ru.aigul.mts_service.model;

public enum Privilege {
    CATALOG_READ("Чтение каталога"),
    APPLICATION_CREATE("Создание приложения"),
    APPLICATION_READ_OWN("Чтение собственных приложений"),
    APPLICATION_READ_ALL("Чтение всех приложений"),
    APPLICATION_APPROVE("Одобрение приложений"),
    APPLICATION_REJECT("Отклонение приложений"),
    TARIFF_MANAGE("Управление тарифами"),
    SERVICE_MANAGE("Управление сервисами"),
    USER_MANAGE("Управление пользователями"),
    ACCOUNT_TOPUP("Пополнение счёта"),
    SEARCH_REINDEX("Переиндексация поиска");

    private final String description;

    Privilege(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

