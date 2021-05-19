package com.carepay.sonar.qualitygate;

import com.github.cliftonlabs.json_simple.JsonKey;

@SuppressWarnings("java:S115")
enum TaskJsonProperty implements JsonKey {
    task,
    analysisId,
    status,
    ;

    @Override
    public String getKey() {
        return name();
    }

    @Override
    public Object getValue() {
        return null;
    }
}
