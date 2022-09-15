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
function init() {
	Math.clamp = function (number, min, max) {
		return Math.max(min, Math.min(number, max));
	}

	// set initial positions
	lCsr.setTranslateX(0);
	rCsr.setTranslateX(fitPlotAreaPane.width);

	// move together
	lCsr_res.translateXProperty().bind(lCsr.translateXProperty());
	rCsr_res.translateXProperty().bind(rCsr.translateXProperty());

	// fit height
	// without -1, the pane just gets wider and wider
	lCsrBar.endYProperty().bind(fitPlotAreaPane.heightProperty().subtract(1));
	rCsrBar.endYProperty().bind(fitPlotAreaPane.heightProperty().subtract(1));
	lCsrBar_res.endYProperty().bind(resPlotAreaPane.heightProperty().subtract(1));
	rCsrBar_res.endYProperty().bind(resPlotAreaPane.heightProperty().subtract(1));

	// link the two toggle buttons to segmented button
	fitYScaleSB.getButtons().addAll(linTB, logTB);
	linTB.setSelected(true)
}
init();

/**
 * Snap the cursors to the nearest trans point.
 * @param {DoubleProperty} csrPos 
 */
function snapCsrPos(csrPos) {
	csrPos.set(Math.round(csrPos.get() / csrDiv.get()) * csrDiv.get());
}

/**
 * Highlights the cursor when moused on.
 * @param {MouseEvent} event 
 */
function mouseOverCursor(event) {
	var csrCircle = this[event.source.id + "Circle"];
	var newCenterY = Math.clamp(event.y, csrCircle.radius * 2.5, fitPlotAreaPane.height - csrCircle.radius * 2.5);
	csrCircle.setCenterY(newCenterY);
	csrCircle.setScaleX(2);
	csrCircle.setScaleY(2);
}

/**
 * Restores cursor when lost focus.
 * @param {MouseEvent} event 
 */
function mouseOffCursor(event) {
	var csrCircle = this[event.source.id + "Circle"];
	csrCircle.setScaleX(1);
	csrCircle.setScaleY(1);
}

/**
 * Handles the dragging of a cursor.
 * @param {MouseEvent} event 
 */
function cursorDragged(event) {
	var csr = event.source;
	// event.x is relative to the previous center
	var newTranslateX = csr.translateX + event.x;
	var min = 0.0, max = fitPlotAreaPane.width;
	if (csr == rCsr) {
		min = lCsr.translateX;
	}
	else if (csr == lCsr) {
		max = rCsr.translateX;
	}
	csr.setTranslateX(Math.clamp(newTranslateX, min, max));
}
