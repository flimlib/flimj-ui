package flimlib.flimj.ui.controller;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import org.controlsfx.control.CheckComboBox;
import org.controlsfx.control.IndexedCheckModel;
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
	public void initialize(URL location, ResourceBundle resources) {
		exportOptions = exportComboBox.getItems();
		exportCBCheckModel = exportComboBox.getCheckModel();

		// disable export button if no item is selected
		exportCBCheckModel.getCheckedIndices().addListener((ListChangeListener<Integer>) change -> {
			exportButton.setDisable(exportCBCheckModel.getCheckedIndices().isEmpty());
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
						imgp.setChannelMinimum(0,
								getOps().stats().percentile(img, 10).getRealDouble());
						imgp.setChannelMaximum(0,
								getOps().stats().percentile(img, 90).getRealDouble());
					}
				}

				getUIs().show(option, imgp);
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
}
