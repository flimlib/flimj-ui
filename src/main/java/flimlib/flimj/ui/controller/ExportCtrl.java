/*-
 * #%L
 * Fluorescence lifetime analysis in ImageJ.
 * %%
 * Copyright (C) 2019 - 2025 Board of Regents of the University of Wisconsin-Madison.
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
package flimlib.flimj.ui.controller;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.controlsfx.control.CheckComboBox;
import org.controlsfx.control.IndexedCheckModel;
import org.scijava.widget.FileWidget;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import net.imagej.ImgPlus;
import flimlib.flimj.FitParams;
import flimlib.flimj.FitResults;
import flimlib.flimj.ui.FitProcessor;
import flimlib.flimj.ui.Utils;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;

/**
 * The controller of the "Export" tab.
 */
public class ExportCtrl extends AbstractCtrl {

	@FXML
	private Button exportButton;

	@FXML
	private CheckComboBox<String> exportComboBox;

	@FXML
	private CheckBox withLUTCheckBox;

	/** The list of all export options */
	private ObservableList<String> exportOptions;

	/** The {@link IndexedCheckModel} of the export option CheckBox */
	private IndexedCheckModel<String> exportCBCheckModel;

	@Override
	public void initialize() {
		exportOptions = exportComboBox.getItems();
		exportCBCheckModel = exportComboBox.getCheckModel();

		// disable export button if no item is selected
		exportCBCheckModel.getCheckedIndices().addListener((ListChangeListener<Integer>) change -> {
			exportButton
					.setDisable(exportCBCheckModel.getCheckedIndices().isEmpty());
		});

		exportButton.setDisable(true);
		exportButton.setOnAction(event -> {
			for (int idx : exportCBCheckModel.getCheckedIndices()) {
				String option = exportOptions.get(idx);

				RandomAccessibleInterval<FloatType> previewRAI = fp.getPreviewImg(option);
				Img<FloatType> img = getOps().create().img(previewRAI);
				getOps().copy().rai(img, previewRAI);

				ImgPlus<FloatType> imgp = new ImgPlus<FloatType>(img);
				if (withLUTCheckBox.isSelected()) {
					// set bounds and LUT
					if ("Intensity".equals(option) || "IRF Intensity".equals(option)) {
						imgp.setChannelMinimum(0, 0);
						imgp.setChannelMaximum(0, getOps().stats().max(img).getRealDouble());
					} else {
						imgp.initializeColorTables(1);
						imgp.setColorTable(Utils.LIFETIME_LUT, 0);
						imgp.setChannelMinimum(0, getOps().stats().percentile(img, 10).getRealDouble());
						imgp.setChannelMaximum(0, getOps().stats().percentile(img, 90).getRealDouble());
					}
				}

				getUIs().show(convertToPlaintext(option), imgp);
			}
		});

		// export with LUT by default
		withLUTCheckBox.setSelected(true);
	}

	@Override
	protected void refresh(FitParams<FloatType> params, FitResults results) {
		// make a copy to prevent being changed by setAll()
		List<String> checked = new ArrayList<>(exportCBCheckModel.getCheckedItems());
		exportCBCheckModel.clearChecks();
		exportOptions.setAll(fp.getPreviewOptions());
		for (String oldItem : checked) {
			exportCBCheckModel.check(oldItem);
		}
	}

	private static String convertToPlaintext(String name) {
		name = name.replace("τ", "tau");
		name = name.replace("₁", "_1");
		name = name.replace("₂", "_2");
		name = name.replace("₃", "_3");
		name = name.replace("ₘ", "_m");
		name = name.replace("ᵢ", "_i");
		name = name.replace("%", "percent");
		return name;
	}
}
