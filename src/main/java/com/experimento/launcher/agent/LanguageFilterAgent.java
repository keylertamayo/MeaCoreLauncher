package com.experimento.launcher.agent;

public class LanguageFilterAgent {
    
    private static boolean initialized = false;
    
    public static void premain(String args, java.lang.instrument.Instrumentation inst) {
        System.out.println("[MeaCore] 🚀 MeaCore Performance Agent v1.2.2");
        
        try {
            LanguageFilterTransformer transformer = new LanguageFilterTransformer();
            inst.addTransformer(transformer, true);
            
            ChunkCacheOptimizer.initialize();
            
            initialized = true;
            System.out.println("[MeaCore] ✅ Optimizaciones activas: Idiomas + Chunks");
        } catch (Exception e) {
            System.err.println("[MeaCore] ⚠️ " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        System.out.println("[MeaCore] Este agent debe ejecutarse con -javaagent");
    }
}