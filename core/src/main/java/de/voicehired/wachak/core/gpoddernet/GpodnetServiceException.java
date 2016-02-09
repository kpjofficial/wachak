package de.voicehired.wachak.core.gpoddernet;

public class GpodnetServiceException extends Exception {

    public GpodnetServiceException() {
    }

    public GpodnetServiceException(String message) {
        super(message);
    }

    public GpodnetServiceException(Throwable cause) {
        super(cause);
    }

    public GpodnetServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
