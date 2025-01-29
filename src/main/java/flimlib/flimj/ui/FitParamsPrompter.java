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
package flimlib.flimj.ui;

import io.scif.HasMetaTable;
import io.scif.MetaTable;
import io.scif.img.axes.SCIFIOAxes;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.ops.OpService;
import net.imglib2.Localizable;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import flimlib.flimj.FitParams;
import flimlib.flimj.ui.controls.NumericSpinner;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Utility class to ease population of {@link FitParams} objects.
 * 
 * @author Curtis Rueden
 */
public final class FitParamsPrompter {

	/**
	 * Computes reasonable default values for the given {@link Dataset} at the
	 * specified {@link Localizable position}, then confirms them with the user
	 * using a JavaFX dialog box.
	 * 
	 * @param params The {@link FitParams} object to populate.
	 * @param dataset The {@link Dataset} from which to infer default values.
	 * @param position The position at which to slice the dataset, if
	 *          dimensionality is greater than 3D.
	 * @param <T> Dataset data type
	 * @return <code>true</code> if the operation succeeds
	 */
	public static <T extends RealType<T>> boolean populate(
		final FitParams<FloatType> params, final Dataset dataset,
		final Localizable position)
	{
		final OpService ops = dataset.context().service(OpService.class);

		// discern the X and Y axes
		final int xAxis = dataset.dimensionIndex(Axes.X);
		if (xAxis < 0) throw new IllegalArgumentException("Dataset has no X axis");
		final int yAxis = dataset.dimensionIndex(Axes.Y);
		if (yAxis < 0) throw new IllegalArgumentException("Dataset has no Y axis");
		final int nD = dataset.numDimensions();
		if (nD < 3) throw new IllegalArgumentException("Dataset must have 3 or more dimensions");

		// discern the lifetime axis
		int ltAxis = dataset.dimensionIndex(SCIFIOAxes.LIFETIME);
		if (ltAxis < 0) ltAxis = dataset.dimensionIndex(Axes.TIME);
		if (ltAxis < 0) {
			// Use the first axis with unknown type, if one exists.
			for (int d = 0; d < nD; d++) {
				if (Axes.UNKNOWN_LABEL.equals(dataset.axis(d).type().getLabel())) {
					ltAxis = d;
					break;
				}
			}
		}
		if (ltAxis < 0) ltAxis = nD - 1;

		// discern the time increment
		double timeBin = -1;
		final Object scifioMetadataGlobal = //
			dataset.getProperties().get("scifio.metadata.global");
		if (scifioMetadataGlobal instanceof HasMetaTable) {
			final MetaTable metaTable = ((HasMetaTable) scifioMetadataGlobal).getTable();
			final Object historyExtents = metaTable.get("history extents");
			final Object historyLabels = metaTable.get("history labels");
			if (historyExtents instanceof String && historyLabels instanceof String) {
				final String[] extVals = ((String) historyExtents).split("\\s+");
				final String[] extLbls = ((String) historyLabels).split("\\s+");
				final int extLen = Math.min(extVals.length, extLbls.length);
				for (int i = 0; i < extLen; i++) {
					if ("t".equals(extLbls[i])) {
						timeBin = Double.parseDouble(extVals[i]) * 1e9;
						break;
					}
				}
			}
		}
		if (timeBin < 0) timeBin = dataset.axis(ltAxis).calibratedValue(dataset.dimension(ltAxis));
		if (timeBin < 0) timeBin = 10d;

		// Ask the user to confirm the details.

		final Stage dialog = new Stage();

		dialog.setTitle("FLIMJ");
		dialog.initStyle(StageStyle.UTILITY);
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.centerOnScreen();
		final boolean[] closedByUser = {false};
		dialog.setOnCloseRequest(event -> closedByUser[0] = true);

		class Dimension {
			private final int d;

			public Dimension(final int d) {
				this.d = d;
			}

			@Override
			public String toString() {
				final String label = dataset.axis(d).type().getLabel();
				final long length = dataset.dimension(d);
				return "[" + d + "]: " + label + " (" + length + " samples)";
			}
		}
		final ComboBox<Dimension> ltAxisBox = new ComboBox<>();
		for (int d = 0; d < nD; d++)
			ltAxisBox.getItems().add(new Dimension(d));
		ltAxisBox.getSelectionModel().select(ltAxis);

		final NumericSpinner timeBinBox = new NumericSpinner(0.0, 10.0, 0.01);
		timeBinBox.getNumberProperty().set(timeBin / dataset.dimension(ltAxis));

		final Button okButton = new Button("OK");
		okButton.setDefaultButton(true);
		okButton.setOnAction(t -> dialog.close());

		final GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.add(new Text("Lifetime Axis"), 0, 0);
		grid.add(ltAxisBox, 1, 0);
		grid.add(new Text("Time Bin (ns)"), 0, 1);
		grid.add(timeBinBox, 1, 1);

		final VBox layout = new VBox(10);
		layout.setAlignment(Pos.CENTER_RIGHT);
		layout.setStyle("-fx-background-color: azure; -fx-padding: 10;");
		layout.getChildren().setAll(grid, okButton);

		dialog.setScene(new Scene(layout));
		dialog.showAndWait();

		if (closedByUser[0])
			return false;

		// Create fit params and populate from final dialog values.

		params.ltAxis = ltAxisBox.getSelectionModel().getSelectedIndex();
		params.xInc = timeBinBox.getNumberProperty().get().floatValue();

		// Slice down to 3D, fixing positions of irrelevant dimensions.
		@SuppressWarnings("unchecked")
		ImgPlus<T> imp = (ImgPlus<T>) dataset.getImgPlus();
		RandomAccessibleInterval<T> img = imp;
		for (int d = imp.numDimensions() - 1; d >= 0; --d) {
			if (d == xAxis || d == yAxis || d == params.ltAxis) continue;
			img = Views.hyperSlice(img, d, position.getLongPosition(d));
			if (d < params.ltAxis) params.ltAxis--;
		}
		if (img.numDimensions() != 3) {
			throw new RuntimeException("Unexpected FLIM image dimensionality: " +
				img.numDimensions());
		}
		// Convert sliced FLIM data to float32 data type.
		params.transMap = ops.convert().float32(Views.iterable(img));

		return true;
	}
}
