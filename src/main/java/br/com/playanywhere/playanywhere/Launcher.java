package br.com.playanywhere.playanywhere;

/**
 * Main application launcher.
 * 
 * A separate launcher class that does not extend {@link javafx.application.Application}
 * is required when running JavaFX applications from a fat JAR or classpath,
 * preventing the "JavaFX runtime components are missing" error emitted by the JVM launcher.
 */
public class Launcher {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}
