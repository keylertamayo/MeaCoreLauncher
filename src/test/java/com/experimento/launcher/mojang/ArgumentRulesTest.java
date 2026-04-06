package com.experimento.launcher.mojang;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArgumentRulesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Only-allow for another OS (e.g. Windows) → exclude on Linux (e.g. skip Windows-only JVM flags). */
    @Test
    void fragmentAllowed_whenOnlyAllowForOtherOs_excludes() throws Exception {
        var arg =
                mapper.readTree(
                        "{\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"windows\"}}],\"value\":\"-Xfoo\"}");
        OsContext linux = new OsContext("linux", "x86");
        assertFalse(ArgumentRules.fragmentAllowed(arg, linux, LaunchFeatures.defaults()));
    }

    /** Mojang: -XstartOnFirstThread — only allow on macOS; must not appear on Linux. */
    @Test
    void fragmentAllowed_osxOnlyAllow_excludedOnLinux() throws Exception {
        var arg =
                mapper.readTree(
                        "{\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"osx\"}}],\"value\":[\"-XstartOnFirstThread\"]}");
        OsContext linux = new OsContext("linux", "x86");
        assertFalse(ArgumentRules.fragmentAllowed(arg, linux, LaunchFeatures.defaults()));
    }

    /** Disallow only on another OS → on Linux the rule does not apply → fragment stays included. */
    @Test
    void fragmentAllowed_disallowOtherOs_only_includesWhenAllRulesDisallow() throws Exception {
        var arg =
                mapper.readTree(
                        "{\"rules\":[{\"action\":\"disallow\",\"os\":{\"name\":\"osx\"}}],\"value\":\"-Xfoo\"}");
        OsContext linux = new OsContext("linux", "x86");
        assertTrue(ArgumentRules.fragmentAllowed(arg, linux, LaunchFeatures.defaults()));
    }
}
