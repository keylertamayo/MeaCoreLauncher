package com.experimento.launcher.agent;

import java.util.HashSet;
import java.util.Set;

public final class AllowedLanguages {
    
    private AllowedLanguages() {}
    
    private static final String[] ALLOWED_ARRAY = {
        "en_us", "es_ar", "es_cl", "es_es", "es_mx", "es_uy", "es_ve"
    };
    
    public static final Set<String> ALLOWED;
    
    static {
        ALLOWED = new HashSet<>();
        for (String lang : ALLOWED_ARRAY) {
            ALLOWED.add(lang);
        }
        ALLOWED.add("en_us");
    }
    
    public static boolean isAllowed(String langCode) {
        if (langCode == null) return false;
        String lower = langCode.toLowerCase();
        for (String allowed : ALLOWED_ARRAY) {
            if (lower.equals(allowed)) return true;
        }
        return false;
    }
}