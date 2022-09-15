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
package flimlib.flimj.ui;

import java.io.File;

import net.imagej.Dataset;
import net.imagej.ImageJ;

import org.scijava.widget.FileWidget;

public class GUITestDrive {

	public static void main(String[] args) throws Exception {
		ImageJ ij = new ImageJ();
		ij.ui().showUI();

		final File file = ij.ui().chooseFile(null, FileWidget.OPEN_STYLE);
		if (file == null) return;
		final Dataset d = ij.scifio().datasetIO().open(file.getAbsolutePath());
		ij.ui().show(d);

		ij.command().run(FLIMJCommand.class, true);
	}
}
