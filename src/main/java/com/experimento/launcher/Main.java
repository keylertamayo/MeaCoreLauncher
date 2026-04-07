package com.experimento.launcher;

/**
 * Clase puente para iniciar JavaFX sin que el runtime de Java 
 * se queje de la falta de módulos en una aplicación no modular.
 */
public class Main {
    public static void main(String[] args) {
        // Fix para el dock de Linux: asocia la ventana al nombre del binario MeaCoreLauncher
        System.setProperty("com.sun.javafx.wm.class", "MeaCoreLauncher");
        LauncherApp.main(args);
    }
}
