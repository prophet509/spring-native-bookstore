package com.locpham.bookstore.searchservice;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.locpham.bookstore.searchservice",
        importOptions = ImportOption.DoNotIncludeTests.class)
public class HexagonalArchitectureTest {

    private static final String DOMAIN = "com.locpham.bookstore.searchservice.domain..";
    private static final String APPLICATION = "com.locpham.bookstore.searchservice.application..";
    private static final String ADAPTER_IN = "com.locpham.bookstore.searchservice.adapter.in..";
    private static final String ADAPTER_OUT = "com.locpham.bookstore.searchservice.adapter.out..";
    private static final String CONFIG = "com.locpham.bookstore.searchservice.config..";

    @ArchTest
    static final ArchRule domain_free_of_frameworks =
            noClasses()
                    .that()
                    .resideInAPackage(DOMAIN)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta..",
                            "javax..",
                            "org.elasticsearch..",
                            "co.elastic.clients..",
                            "org.springframework.data.elasticsearch..",
                            "org.apache.kafka..");

    @ArchTest
    static final ArchRule domain_free_of_adapters =
            noClasses()
                    .that()
                    .resideInAPackage(DOMAIN)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(ADAPTER_IN, ADAPTER_OUT, CONFIG);

    @ArchTest
    static final ArchRule application_free_of_adapters_and_infra =
            noClasses()
                    .that()
                    .resideInAPackage(APPLICATION)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(ADAPTER_IN, ADAPTER_OUT, CONFIG);

    @ArchTest
    static final ArchRule ports_are_interfaces =
            classes()
                    .that()
                    .resideInAnyPackage("..application.in..", "..application.out..")
                    .should()
                    .beInterfaces();

    @ArchTest
    static final ArchRule domain_not_accessed_by_adapters =
            classes()
                    .that()
                    .resideInAPackage(DOMAIN)
                    .should()
                    .onlyBeAccessed()
                    .byAnyPackage(DOMAIN, APPLICATION, ADAPTER_IN, ADAPTER_OUT, CONFIG);

    @ArchTest
    static final ArchRule application_not_accessed_by_domain_or_config =
            classes()
                    .that()
                    .resideInAPackage(APPLICATION)
                    .should()
                    .onlyBeAccessed()
                    .byAnyPackage(APPLICATION, ADAPTER_IN, ADAPTER_OUT, CONFIG);
}
