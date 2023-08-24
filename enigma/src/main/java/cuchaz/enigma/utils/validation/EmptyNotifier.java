package cuchaz.enigma.utils.validation;

/**
 * A no-op {@link ValidationContext.Notifier}.
 */
public class EmptyNotifier implements ValidationContext.Notifier {
	public static EmptyNotifier INSTANCE = new EmptyNotifier();

	@Override
	public void notify(ParameterizedMessage message) {

	}

	@Override
	public boolean verifyWarning(ParameterizedMessage message) {
		return true;
	}
}
