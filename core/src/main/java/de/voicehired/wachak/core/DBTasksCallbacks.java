package de.voicehired.wachak.core;

import de.voicehired.wachak.core.storage.AutomaticDownloadAlgorithm;
import de.voicehired.wachak.core.storage.EpisodeCleanupAlgorithm;

/**
 * Callbacks for the DBTasks class of the storage module.
 */
public interface DBTasksCallbacks {

    /**
     * Returns the client's implementation of the AutomaticDownloadAlgorithm interface.
     */
    public AutomaticDownloadAlgorithm getAutomaticDownloadAlgorithm();

    /**
     * Returns the client's implementation of the EpisodeCacheCleanupAlgorithm interface.
     */
    public EpisodeCleanupAlgorithm getEpisodeCacheCleanupAlgorithm();
}
