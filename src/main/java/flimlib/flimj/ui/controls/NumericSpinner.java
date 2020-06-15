package flimlib.flimj.ui.controls;

import java.util.HashMap;
import javafx.beans.property.ObjectProperty;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;

/**
 * The class defines a {@link TextField} that consumes only numeric inputs. A
 * numeric property is associated with this TextField and will be updated upon
 * the user's pressing ENTER or the TextField's lossing focus. If the input
 * (even before pressing ENTER) does not match the scientific or percentage
 * format, the TextField will be highlighted.
 */
public class NumericSpinner extends Spinner<Double> {

	private NumericHelper nh;

	private double min, max, stepSize;

	/**
	 * Constructs an {@link NumericSpinner}.
	 * 
	 * @param intOnly false if this spinner is allowed to consume doubles.
	 */
	public NumericSpinner(boolean intOnly, HashMap<String, Double> kwMap) {
		super();
		nh = new NumericHelper(this.getEditor(), this, intOnly, kwMap);
		ObjectProperty<Double> num = nh.getNumberProperty();
		setValueFactory(new SpinnerValueFactory<Double>() {

			@Override
			public void decrement(int steps) {
				double curVal = num.get();
				num.set(Math.max(curVal - steps * stepSize, min));
			}

			@Override
			public void increment(int steps) {
				double curVal = num.get();
				num.set(Math.min(curVal + steps * stepSize, max));
			}
		});
	}

	public NumericSpinner(boolean intOnly) {
		this(intOnly, null);
	}

	/**
	 * Constructs an {@link NumericSpinner}.
	 */
	public NumericSpinner() {
		this(false);
	}

	/**
	 * @return the min
	 */
	public double getMin() {
		return min;
	}

	/**
	 * @param min the min to set
	 */
	public void setMin(double min) {
		this.min = min;
		nh.setMin(min);
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
		nh.setMax(max);
	}

	/**
	 * @return the stepSize
	 */
	public double getStepSize() {
		return stepSize;
	}

	/**
	 * @param stepSize the stepSize to set
	 */
	public void setStepSize(double stepSize) {
		this.stepSize = stepSize;
	}

	/**
	 * @param intOnly false if this NumericSpinner is allowed to consume doubles.
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

	public void setClamped(Double value) {
		nh.getNumberProperty().set(Math.max(Math.min(value, max), min));
	}

	public ObjectProperty<Boolean> getIsPercentageProperty() {
		return nh.getIsPercentageProperty();
	}

	public void setKwMap(HashMap<String, Double> kwMap) {
		nh.setKwMap(kwMap);
	}
}
