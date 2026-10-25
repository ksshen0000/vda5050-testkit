package io.github.ksshen0000.vda5050.sim;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "io.github.ksshen0000.vda5050.sim", importOptions = ImportOption.DoNotIncludeTests.class)
class CoreArchitectureTest {

    @ArchTest
    static final ArchRule onlyTheProtocol = classes()
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "io.github.ksshen0000.vda5050.sim..",
                    "io.github.ksshen0000.vda5050..", "org.slf4j..")
            .because("the simulated vehicles must work with any VDA 5050 fleet manager, and in plain unit tests");
}
