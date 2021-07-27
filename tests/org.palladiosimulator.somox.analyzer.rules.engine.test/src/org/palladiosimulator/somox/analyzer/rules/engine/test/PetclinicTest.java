package org.palladiosimulator.somox.analyzer.rules.engine.test;

import static org.junit.Assert.assertEquals;

import org.junit.jupiter.api.Test;
import org.palladiosimulator.somox.analyzer.rules.all.DefaultRule;

public class PetclinicTest extends RuleEngineTest {

    protected PetclinicTest() {
        super("spring-petclinic-microservices", DefaultRule.SPRING);
    }

    /**
     * Tests the basic functionality of the RuleEngineAnalyzer when executing the SPRING rule.
     * Requires it to execute without an exception and produce an output file with the correct contents.
     */
    @Test
    void test() {
        assertEquals(11, getComponents().size());
        assertEquals(20, getDatatypes().size());
        assertEquals(0, getFailuretypes().size());
        assertEquals(11, getInterfaces().size());
    }
}