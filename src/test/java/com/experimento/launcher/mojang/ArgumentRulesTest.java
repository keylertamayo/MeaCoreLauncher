package com.experimento.launcher.mojang;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArgumentRulesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** When no rule applies to the host OS, fragment is allowed by default (Mojang-style). */
    @Test
    void fragmentAllowed_whenNoRuleApplies_defaultsAllow() throws Exception {
        var arg =
                mapper.readTree(
                        "{\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"windows\"}}],\"value\":\"-Xfoo\"}");
        OsContext linux = new OsContext("linux", "x86");
        assertTrue(ArgumentRules.fragmentAllowed(arg, linux, LaunchFeatures.defaults()));
    }
}
