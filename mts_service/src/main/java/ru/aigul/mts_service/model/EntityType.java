package ru.aigul.mts_service.model;

public enum EntityType {
    APPLICATION("APPLICATION")
    ;

    private final String value;

    EntityType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static EntityType fromValue(String value) {
        for (EntityType type : EntityType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown entity type: " + value);
    }
}
