package flimlib.flimj.ui.controls;

import java.util.HashMap;
import javafx.beans.property.ObjectProperty;
import javafx.scene.control.TextField;

/**
 * The class defines a {@link TextField} that consumes only numeric inputs. A
 * numeric property is associated with this TextField and will be updated upon
 * the user's pressing ENTER or the TextField's lossing focus. If the input
 * (even before pressing ENTER) does not match the scientific or percentage
 * format, the TextField will be highlighted.
 */
public class NumericTextField extends TextField {

	private NumericHelper nh;


	/**
	 * Constructs an {@link NumericTextField}.
	 * 
	 * @param intOnly false if this spinner is allowed to consume doubles.
	 */
	public NumericTextField(boolean intOnly, HashMap<String, Double> kwmMap) {
		super();
		nh = new NumericHelper(this, this, intOnly, kwmMap);
	}

	public NumericTextField(boolean intOnly) {
		this(intOnly, null);
	}

	/**
	 * Constructs an {@link NumericTextField}.
	 */
	public NumericTextField() {
		this(false);
	}

	/**
	 * @param intOnly false if this NumericTextField is allowed to consume doubles.
	 */
	public void setIntOnly(boolean intOnly) {
		this.nh.setIntOnly(intOnly);
	}

	/**
	 * Retrieves the numeric property associated with this text field.
	 * 
	 * @return the number property associated with the text field
	 */
	public ObjectProperty<Double> getNumberProperty() {
		return nh.getNumberProperty();
	}

	public ObjectProperty<Boolean> getIsPercentageProperty() {
		return nh.getIsPercentageProperty();
	}

	public void setKwMap(HashMap<String, Double> kwMap) {
		nh.setKwMap(kwMap);
	}
}
