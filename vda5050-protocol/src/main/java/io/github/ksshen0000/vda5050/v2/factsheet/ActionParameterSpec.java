package io.github.ksshen0000.vda5050.v2.factsheet;

import io.github.ksshen0000.vda5050.v2.ValueDataType;

public record ActionParameterSpec(String key, ValueDataType valueDataType, String description, Boolean isOptional) {
}
