package flimlib.flimj.ui;

/**
 * Indicates that a FLIMJ-UI specific exception has occurred.
 */
public class UIException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public UIException(String message) {
        super(message);
	}
	
	public UIException(Exception exception) {
        super(exception);
    }
}
