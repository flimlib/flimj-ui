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
	 * @param kwMap   the map between string keywords and corresponding numeric values
	 */
	public NumericTextField(boolean intOnly, HashMap<String, Double> kwMap) {
		super();
		nh = new NumericHelper(this, this, intOnly, kwMap);
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
