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
import java.io.IOException;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;

import flimlib.flimj.FitParams;
import flimlib.flimj.FitResults;
import flimlib.flimj.ui.SupportPlaneAnalysis;
import flimlib.flimj.ui.controls.NumericSpinner;
import net.imglib2.type.numeric.real.FloatType;

/**
 * The controller of the "Support Plane Analysis" tab.
 * <p>
 * Attributed to: <b>Lakowicz 2006 Section 4.10.3 p. 134</b>
 * </p>
 */
public class SupportPlaneCtrl extends AbstractCtrl {

	@FXML
	private NumericSpinner rangeSpinner;

	@FXML
	private NumericSpinner stepsSpinner;

	@FXML
	private Button saveCSVButton;

	@Override
	public void initialize() {
		final var fcStage = new Stage();
		final var fcUI = new FileChooser();
		fcUI.getExtensionFilters().addAll(
				new ExtensionFilter("CSV files (*.csv)", "*.csv"),
				new ExtensionFilter("All files (*.*)", "*.*"));

		// Configure range spinner (default 20.0%)
		rangeSpinner.setMin(1.0);
		rangeSpinner.setMax(500.0);
		rangeSpinner.setStepSize(5.0);
		rangeSpinner.getNumberProperty().setValue(20.0);

		// Configure steps spinner (default 5 steps each side)
		stepsSpinner.setIntOnly(true);
		stepsSpinner.setMin(1.0);
		stepsSpinner.setMax(100.0);
		stepsSpinner.setStepSize(1.0);
		stepsSpinner.getNumberProperty().setValue(5.0);

		saveCSVButton.setOnAction(event -> {
			fcUI.setTitle("Save Support Plane Analysis CSV");
			fcUI.setInitialFileName("support_plane_analysis.csv");
			File csvSavePath = fcUI.showSaveDialog(fcStage);
			if (csvSavePath != null) {
				float range = rangeSpinner.getNumberProperty().get().floatValue();
				int steps = stepsSpinner.getNumberProperty().get().intValue();

				MainCtrl mainCtrl = (parentCtrl instanceof MainCtrl) ? (MainCtrl) parentCtrl : null;
				if (mainCtrl != null) {
					mainCtrl.setProgress(-1.0);
				}

				fp.submitRunnable(() -> {
					try {
						SupportPlaneAnalysis.AnalysisResult result = fp.runSupportPlaneAnalysis(range, steps);
						result.saveToFile(csvSavePath);

						Platform.runLater(() -> {
							if (mainCtrl != null) {
								mainCtrl.setProgress(null);
							}
							Alert alert = new Alert(AlertType.INFORMATION);
							alert.setTitle("Support Plane Analysis");
							alert.setHeaderText("CSV Saved Successfully");
							alert.setContentText(String.format(
									"Support Plane Analysis (%s)\nsaved %d points to:\n%s",
									SupportPlaneAnalysis.ATTRIBUTION, result.points.size(),
									csvSavePath.getAbsolutePath()));
							alert.show();
						});
					} catch (Exception e) {
						Platform.runLater(() -> {
							if (mainCtrl != null) {
								mainCtrl.setProgress(null);
							}
							Alert alert = new Alert(AlertType.ERROR);
							alert.setTitle("Support Plane Analysis Error");
							alert.setHeaderText("Failed to save Support Plane Analysis CSV");
							alert.setContentText(e.getMessage());
							alert.show();
						});
					}
				});
			}
		});
	}

	@Override
	protected void refresh(FitParams<FloatType> params, FitResults results) {
		// Enabled whenever the processor is active
		saveCSVButton.setDisable(fp == null);
	}
}
