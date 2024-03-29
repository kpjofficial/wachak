package de.voicehired.wachak.core.util;

import java.util.concurrent.Callable;

/**
 * Created by daniel on 04.08.13.
 */
public interface ShownotesProvider {
    /**
     * Loads shownotes. If the shownotes have to be loaded from a file or from a
     * database, it should be done in a separate thread. After the shownotes
     * have been loaded, callback.onShownotesLoaded should be called.
     */
    public Callable<String> loadShownotes();

}
