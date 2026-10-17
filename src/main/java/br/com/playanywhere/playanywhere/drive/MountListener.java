package br.com.playanywhere.playanywhere.drive;

import br.com.playanywhere.playanywhere.domain.model.PlayAnywhereMetadata;
import br.com.playanywhere.playanywhere.launcher.LaunchResult;

import java.nio.file.Path;

/**
 * Listener para eventos de detecção e lançamento de jogos em mídias conectadas.
 */
public interface MountListener {
    void onGameDetected(Path drivePath, Path gameDir, PlayAnywhereMetadata metadata);
    void onGameLaunched(PlayAnywhereMetadata metadata, LaunchResult result);
    void onGameLaunchFailed(PlayAnywhereMetadata metadata, String error);
    default void onMountsChanged() {}
}
