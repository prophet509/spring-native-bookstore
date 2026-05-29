package com.locpham.bookstore.catalogservice;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.locpham.bookstore.catalogservice",
        importOptions = ImportOption.DoNotIncludeTests.class)
public class HexagonalArchitectureTest {

    private static final String DOMAIN = "com.locpham.bookstore.catalogservice.domain..";
    private static final String APPLICATION = "com.locpham.bookstore.catalogservice.application..";
    private static final String ADAPTER_IN = "com.locpham.bookstore.catalogservice.adapter.in..";
    private static final String ADAPTER_OUT = "com.locpham.bookstore.catalogservice.adapter.out..";
    private static final String CONFIG = "com.locpham.bookstore.catalogservice.config..";
    private static final String DEMO = "com.locpham.bookstore.catalogservice.demo..";

    // ── Domain isolation ──────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule domain_free_of_frameworks =
            noClasses()
                    .that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..", "jakarta..", "javax..",
                            "java.sql..", "org.flywaydb..", "com.zaxxer.hikari..",
                            "org.springframework.jdbc..", "org.springframework.data.jdbc..");

    @ArchTest
    static final ArchRule domain_free_of_adapters =
            noClasses()
                    .that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            ADAPTER_IN, ADAPTER_OUT, CONFIG, DEMO);

    // ── Application isolation ─────────────────────────────────────────────────

    @ArchTest
    static final ArchRule application_free_of_adapters_and_infra =
            noClasses()
                    .that().resideInAPackage(APPLICATION)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            ADAPTER_IN, ADAPTER_OUT, CONFIG, DEMO);

    // ── Port interfaces ───────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule ports_are_interfaces =
            classes()
                    .that().resideInAPackage("..port..")
                    .should().beInterfaces();

    // ── Dependency direction ──────────────────────────────────────────────────

    @ArchTest
    static final ArchRule domain_not_accessed_by_adapters =
            classes()
                    .that().resideInAPackage(DOMAIN)
                    .should().onlyBeAccessed().byAnyPackage(
                            DOMAIN, APPLICATION, ADAPTER_IN, ADAPTER_OUT, CONFIG, DEMO);

    @ArchTest
    static final ArchRule application_not_accessed_by_domain_or_config =
            classes()
                    .that().resideInAPackage(APPLICATION)
                    .should().onlyBeAccessed().byAnyPackage(
                            APPLICATION, ADAPTER_IN, ADAPTER_OUT, CONFIG, DEMO);
}
