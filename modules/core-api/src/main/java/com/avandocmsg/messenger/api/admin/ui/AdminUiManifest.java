package com.avandocmsg.messenger.api.admin.ui;

import com.avandocmsg.messenger.common.admin.ui.AdminUiContributor;
import com.avandocmsg.messenger.common.admin.ui.AdminUiSectionDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Собирает разделы админ-консоли из {@link ServiceLoader} ({@link AdminUiContributor}).
 * Дубликаты {@code id} отбрасываются (остаётся первый в порядке обхода SPI).
 */
public final class AdminUiManifest {

    private static final Logger log = LoggerFactory.getLogger(AdminUiManifest.class);

    private final List<AdminUiSectionDescriptor> sections;

    private AdminUiManifest(List<AdminUiSectionDescriptor> sections) {
        this.sections = List.copyOf(sections);
    }

    public List<AdminUiSectionDescriptor> sections() {
        return sections;
    }

    public static AdminUiManifest load(ClassLoader classLoader) {
        var byId = new LinkedHashMap<String, AdminUiSectionDescriptor>();
        for (AdminUiContributor contributor : ServiceLoader.load(AdminUiContributor.class, classLoader)) {
            for (AdminUiSectionDescriptor section : contributor.sections()) {
                AdminUiSectionDescriptor previous = byId.putIfAbsent(section.id(), section);
                if (previous != null) {
                    log.warn("Duplicate admin UI section id '{}', keeping first declaration", section.id());
                }
            }
        }

        var sorted = new ArrayList<>(byId.values());
        sorted.sort(Comparator
            .comparingInt(AdminUiSectionDescriptor::sortOrder)
            .thenComparing(AdminUiSectionDescriptor::id));
        log.debug("Admin UI sections loaded: {}", sorted.size());
        return new AdminUiManifest(sorted);
    }

    /** Для тестов без SPI. */
    public static AdminUiManifest of(List<AdminUiSectionDescriptor> sections) {
        return new AdminUiManifest(sections);
    }
}
