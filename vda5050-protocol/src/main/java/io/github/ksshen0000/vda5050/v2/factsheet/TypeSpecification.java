package io.github.ksshen0000.vda5050.v2.factsheet;

import java.util.List;

import io.github.ksshen0000.vda5050.v2.AgvClass;
import io.github.ksshen0000.vda5050.v2.AgvKinematic;
import io.github.ksshen0000.vda5050.v2.LocalizationType;
import io.github.ksshen0000.vda5050.v2.NavigationType;

/** @param maxLoadMass kg */
public record TypeSpecification(
        String seriesName,
        String seriesDescription,
        AgvKinematic agvKinematic,
        AgvClass agvClass,
        double maxLoadMass,
        List<LocalizationType> localizationTypes,
        List<NavigationType> navigationTypes) {
}
