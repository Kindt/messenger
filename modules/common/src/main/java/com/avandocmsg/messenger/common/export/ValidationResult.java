package com.avandocmsg.messenger.common.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of export completeness validation. */
public final class ValidationResult {

    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    public void addWarning(String code, String field) {
        warnings.add(code + ":" + field);
    }

    public void addError(String code, String field) {
        errors.add(code + ":" + field);
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<String> getMessages() {
        var all = new ArrayList<String>(warnings.size() + errors.size());
        all.addAll(warnings);
        all.addAll(errors);
        return Collections.unmodifiableList(all);
    }

    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }

    public boolean isComplete() {
        return errors.isEmpty();
    }
}
