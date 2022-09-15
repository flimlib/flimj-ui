/*-
 * #%L
 * Fluorescence lifetime analysis in ImageJ.
 * %%
 * Copyright (C) 2019 - 2022 Board of Regents of the University of Wisconsin-Madison.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package flimlib.flimj.ui.controls;

import java.util.HashMap;
import javafx.beans.property.ObjectProperty;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;

/**
 * The class defines a {@link TextField} that consumes only numeric inputs. A numeric property is
 * associated with this TextField and will be updated upon the user's pressing ENTER or the
 * TextField's lossing focus. If the input (even before pressing ENTER) does not match the
 * scientific or percentage format, the TextField will be highlighted.
 */
public class NumericSpinner extends Spinner<Double> {

	private NumericHelper nh;

	private double min, max, stepSize;

	/**
	 * @param min     the minimum
	 * @param max     the maximum
	 * @param step    the step size
	 * @param intOnly if this is an integral spinner
	 * @param kwMap   the map from keywords to corresponding values
	 */
	public NumericSpinner(double min, double max, double step, boolean intOnly,
			HashMap<String, Double> kwMap) {
		super();
		nh = new NumericHelper(this.getEditor(), this, intOnly, kwMap);
		ObjectProperty<Double> num = nh.getNumberProperty();
		setValueFactory(new SpinnerValueFactory<Double>() {

			@Override
			public void decrement(int steps) {
				double curVal = num.get();
				num.set(Math.max(curVal - steps * NumericSpinner.this.stepSize,
						NumericSpinner.this.min));
			}

			@Override
			public void increment(int steps) {
				double curVal = num.get();
				num.set(Math.min(curVal + steps * NumericSpinner.this.stepSize,
						NumericSpinner.this.max));
			}
		});
		this.setMin(min);
		this.setMax(max);
		this.setStepSize(step);
		this.setEditable(true);
	}

	public NumericSpinner(double min, double max, double step, boolean intOnly) {
		this(min, max, step, intOnly, null);
	}

	public NumericSpinner(double min, double max, double step) {
		this(min, max, step, false);
	}

	/**
	 * Constructs an {@link NumericSpinner}.
	 */
	public NumericSpinner() {
		this(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1);
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
