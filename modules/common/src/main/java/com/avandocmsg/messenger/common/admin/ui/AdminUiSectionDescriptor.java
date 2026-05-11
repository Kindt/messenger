package com.avandocmsg.messenger.common.admin.ui;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Описание одного раздела бокового меню встроенной админ-консоли.
 * Модули добавляют разделы через {@link AdminUiContributor} и {@link java.util.ServiceLoader}.
 */
public final class AdminUiSectionDescriptor {

    private final String id;
    private final String title;
    private final int sortOrder;
    private final AdminUiSectionKind kind;
    /**
     * Относительный путь API (от корня {@code /api/v1}) для загрузки панели; может быть {@code null},
     * если раздел полностью обрабатывается в SPA по {@link #id()}.
     */
    private final String dataPath;

    public AdminUiSectionDescriptor(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("sort_order") int sortOrder,
        @JsonProperty("kind") AdminUiSectionKind kind,
        @JsonProperty("data_path") String dataPath
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = Objects.requireNonNull(title, "title");
        this.sortOrder = sortOrder;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.dataPath = dataPath;
    }

    @JsonProperty("id")
    public String id() {
        return id;
    }

    @JsonProperty("title")
    public String title() {
        return title;
    }

    @JsonProperty("sort_order")
    public int sortOrder() {
        return sortOrder;
    }

    @JsonProperty("kind")
    public AdminUiSectionKind kind() {
        return kind;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("data_path")
    public String dataPath() {
        return dataPath;
    }
}
