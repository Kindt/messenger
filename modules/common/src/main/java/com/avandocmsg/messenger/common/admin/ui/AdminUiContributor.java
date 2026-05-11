package com.avandocmsg.messenger.common.admin.ui;

import java.util.List;

/**
 * Точка расширения: модуль (отдельный JAR на classpath) регистрирует разделы админ-консоли.
 * Реализации перечисляются в {@code META-INF/services/com.avandocmsg.messenger.common.admin.ui.AdminUiContributor}
 * (одна строка = полное имя класса на каждую реализацию).
 * При отсутствии модуля на classpath соответствующая строка в SPI отсутствует — разделы не появляются.
 */
public interface AdminUiContributor {

    List<AdminUiSectionDescriptor> sections();
}
