package flimlib.flimj.ui.controls;

import java.util.HashMap;
import org.controlsfx.tools.ValueExtractor;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;

import flimlib.flimj.ui.Utils;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.scene.control.Control;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;

/**
 * The class defines a helper that takes over the control of a {@link Control} that has a
 * {@link TextField} as text input. The TextField consumes only numeric inputs. A numeric property
 * is associated with this TextField and will be updated upon the user's pressing ENTER or the
 * Control's lossing focus. If the input (even before pressing ENTER) does not match the scientific
 * or percentage format, the Control will be highlighted.
 */
public class NumericHelper {

	private TextField editor;
	private Control control;
	private boolean intOnly;
	private HashMap<String, Double> kwMap;

	private String lastText;

	private ObjectProperty<Double> num = new SimpleObjectProperty<>();
	private ObjectProperty<Boolean> isPercentage = new SimpleObjectProperty<>();

	private double min = Double.NEGATIVE_INFINITY, max = Double.POSITIVE_INFINITY;

	private final ValidationSupport vs;

	/**
	 * Constructs an {@link NumericHelper}.
	 * 
	 * @param editor  the text field to harvest input
	 * @param control the control to bind to
	 * @param intOnly <code>true</code> if accepts integer only
	 * @param kwMap   the map between string keywords and corresponding numeric values
	 */
	public NumericHelper(TextField editor, Control control, boolean intOnly,
			HashMap<String, Double> kwMap) {
		this.kwMap = kwMap == null ? new HashMap<>() : kwMap;
		vs = new ValidationSupport();
		initValidationSupport();

		this.editor = editor;
		this.control = control;
		init();
		setFormatedVal();
	}

	/**
	 * @param min the min to set
	 */
	public void setMin(double min) {
		this.min = min;
	}

	/**
	 * @return the min
	 */
	public double getMin() {
		return min;
	}

	/**
	 * @return the max
	 */
	public double getMax() {
		return max;
	}

	/**
	 * @param max the max to set
	 */
	public void setMax(double max) {
		this.max = max;
	}

	public void setKwMap(HashMap<String, Double> kwMap) {
		this.kwMap.clear();
		for (String keyword : kwMap.keySet())
			this.kwMap.put(keyword.toUpperCase(), kwMap.get(keyword));
	}

	/**
	 * @param intOnly false if this NumericHelper is allowed to consume doubles.
	 */
	public void setIntOnly(boolean intOnly) {
		this.intOnly = intOnly;
	}

	/**
	 * Retrieves the numeric property associated with this text field.
	 * 
	 * @return the number property associated with the text field
	 */
	public ObjectProperty<Double> getNumberProperty() {
		return num;
	}

	/**
	 * Retrieves the boolean property denoting whether this numeric value is a percentage.
	 * 
	 * @return the boolean property denoting whether this numeric value is a percentage
	 */
	public ObjectProperty<Boolean> getIsPercentageProperty() {
		return isPercentage;
	}

	/**
	 * Initializes the fields and set listeners for actions and value changes.
	 */
	private void init() {
		num.set(0.0);
		isPercentage.set(false);
		lastText = "";

		// update if ENTER is hit or focus is lost and select all of the text on focus
		editor.setOnAction(event -> update());
		editor.focusedProperty().addListener((obs, oldVal, newVal) -> {
			if (newVal) {
				// https://stackoverflow.com/a/57137299
				Platform.runLater(() -> editor.selectAll());
			} else if (!lastText.equals(editor.getText())) {
				editor.getOnAction().handle(new ActionEvent());
			}
		});

		num.addListener((obs, oldVal, newVal) -> setFormatedVal());
		isPercentage.addListener((obs, oldVal, newVal) -> setFormatedVal());

		vs.registerValidator(control, Validator.<String>createPredicateValidator(
				s -> Utils.matchesNumber(s) || this.kwMap.containsKey(s.toUpperCase()), ""));
	}

	private void initValidationSupport() {
		// support for spinner's textfield
		ValueExtractor.addObservableValueExtractor(o -> o instanceof Spinner,
				s -> ((Spinner<?>) s).getEditor().textProperty());
		vs.setValidationDecorator(new StyleClassValidationDecoration("error-highlight", null));
	}

	/**
	 * Updates the fields and displayed text.
	 */
	private void update() {
		String text = editor.getText();
		boolean isKW = false;
		if (text == null) {
			return;
		}
		text = text.trim().toUpperCase();
		if (text.length() == 0) {
			return;
		}
		try {
			double newVal;
			if (kwMap.containsKey(text)) {
				newVal = kwMap.get(text);
				isKW = true;
			} else {
				newVal = Math.min(Math.max(Utils.parseDec(text), min), max);
			}

			if (intOnly) {
				newVal = Math.round(newVal);
			}
			num.set(newVal);
			isPercentage.set(text.endsWith("%"));
		} catch (RuntimeException e) {
			throw new RuntimeException(e);
		} finally {
			if (isKW)
				editor.setText(text);
			else
				setFormatedVal();
			editor.selectAll();
		}
	}

	/**
	 * Formats the displayed text with either the scientific formatter or the percentage formater
	 * depending on the value of {@link #isPercentage}.
	 */
	private void setFormatedVal() {
		lastText =
				isPercentage.get() ? Utils.percentFmt(num.get() / 100) : Utils.prettyFmt(num.get());
		editor.setText(lastText);
	}
}
