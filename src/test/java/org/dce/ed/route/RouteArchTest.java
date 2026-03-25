package org.dce.ed.route;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "org.dce.ed.route")
class RouteArchTest {

    @ArchTest
    static final ArchRule routePackageDoesNotDependOnSwingOrAwt = noClasses()
            .that().resideInAPackage("org.dce.ed.route..")
            .should().dependOnClassesThat().resideInAnyPackage("javax.swing..", "java.awt..");
}
