package com.experimento.launcher;

/**
 * Clase puente para iniciar JavaFX sin que el runtime de Java 
 * se queje de la falta de módulos en una aplicación no modular.
 */
public class Main {
    public static void main(String[] args) {
        // Establecer estas propiedades lo antes posible, ANTES de cargar cualquier clase de JavaFX.
        System.setProperty("com.sun.javafx.wm.class", "meacorelauncher");
        System.setProperty("glass.gtk.wm_class", "meacorelauncher");
        System.setProperty("jdk.gtk.wm_class", "meacorelauncher");
        
        // Llamar a LauncherApp mediante reflexión para asegurar que la clase no se cargue
        // antes de que las propiedades del sistema estén establecidas.
        try {
            Class<?> appClass = Class.forName("com.experimento.launcher.LauncherApp");
            appClass.getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
