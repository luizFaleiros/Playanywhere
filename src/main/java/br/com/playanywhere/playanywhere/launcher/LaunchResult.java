package br.com.playanywhere.playanywhere.launcher;

import java.util.List;

/**
 * Resultado da tentativa de execução de um jogo.
 */
public record LaunchResult(
    boolean success,
    String runnerUsed,
    List<String> command,
    Process process,
    String errorMessage,
    String gameTitle
) {
    public static LaunchResult success(String runnerUsed, List<String> command, Process process, String gameTitle) {
        return new LaunchResult(true, runnerUsed, command, process, null, gameTitle);
    }

    public static LaunchResult failure(String runnerUsed, String errorMessage, String gameTitle) {
        return new LaunchResult(false, runnerUsed, List.of(), null, errorMessage, gameTitle);
    }
}
