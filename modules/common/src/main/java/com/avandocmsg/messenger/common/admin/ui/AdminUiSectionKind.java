package com.avandocmsg.messenger.common.admin.ui;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Тип встроенной панели в админ-консоли. Клиент выбирает способ отрисовки по полю {@code kind}.
 */
public enum AdminUiSectionKind {
    /** Встроенная панель «статистика ядра» (данные из {@link AdminUiSectionDescriptor#dataPath()}). */
    CORE_STATS("core_stats"),
    /**
     * Панель «как есть»: GET по {@link AdminUiSectionDescriptor#dataPath()} (от корня {@code /api/v1}),
     * ответ показывается как форматированный JSON (массив или объект).
     */
    JSON_PANEL("json_panel"),
    /** Встроенная панель fleet snapshot (все компоненты). */
    FLEET_GRID("fleet_grid"),
    /** Расширение: статический бандл или отдельный endpoint модуля (будущее). */
    CUSTOM("custom");

    private final String jsonValue;

    AdminUiSectionKind(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
