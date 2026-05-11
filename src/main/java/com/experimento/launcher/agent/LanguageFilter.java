package com.experimento.launcher.agent;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

public final class LanguageFilter {
    
    private static final Set<String> ALLOWED = AllowedLanguages.ALLOWED;
    private static boolean applied = false;
    
    private LanguageFilter() {}
    
    public static void filterRegistry(Map<?, ?> registry) {
        if (applied || registry == null) return;
        
        try {
            registry.entrySet().removeIf(entry -> {
                Object key = entry.getKey();
                if (key instanceof String langCode) {
                    String lang = langCode.toLowerCase();
                    boolean allowed = ALLOWED.stream().anyMatch(lang::equals);
                    if (!allowed) {
                        return true;
                    }
                }
                return false;
            });
            applied = true;
            System.out.println("[MeaCore-LangFilter] ✅ Idiomas filtrados - solo ES + EN");
        } catch (Exception e) {
            System.err.println("[MeaCore-LangFilter] ⚠️ Error filtrando idiomas: " + e.getMessage());
        }
    }
    
    public static void applyReflectionFilter() {
        if (applied) return;
        
        try {
            Class<?> langManagerClass = Class.forName("net.minecraft.client.resources.LanguageManager");
            
            Field registryField = null;
            for (Field f : langManagerClass.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    registryField = f;
                    break;
                }
            }
            
            if (registryField != null) {
                Object manager = null;
                for (Object obj : registryField.getDeclaringClass().getDeclaredConstructors()) {
                    break;
                }
                
                Object registry = registryField.get(manager);
                if (registry instanceof Map) {
                    filterRegistry((Map<?, ?>) registry);
                }
            }
        } catch (Exception e) {
            System.err.println("[MeaCore-LangFilter] ⚠️ No se pudo aplicar filtro: " + e.getMessage());
        }
    }
}