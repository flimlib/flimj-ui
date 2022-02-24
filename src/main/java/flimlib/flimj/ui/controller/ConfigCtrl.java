package flimlib.flimj.ui.controller;

import java.io.File;
import java.nio.file.Files;
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
import flimlib.flimj.ui.Utils;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;

/**
 * The controller of the "Export" tab.
 */
public class ConfigCtrl extends AbstractCtrl {

    @FXML
	private Button configLoadButton;

	@FXML
	private Button configSaveButton;

	// @FXML
	// private CheckComboBox<String> exportComboBox;

	// @FXML
	// private CheckBox withLUTCheckBox, saveConfigCheckBox;

	// /** The list of all export options */
	// private ObservableList<String> exportOptions;

	// /** The {@link IndexedCheckModel} of the export option CheckBox */
	// private IndexedCheckModel<String> exportCBCheckModel;

	@Override
	public void initialize() {

		configSaveButton.setOnAction(event -> {
            File cfgSavePath = getUIs().chooseFile("Choose config save path", new File("fit_config.txt"),
            FileWidget.SAVE_STYLE);
            if (cfgSavePath != null) {
                try {
                    FileWriter writer = new FileWriter(cfgSavePath);
                    writer.write(fp.getParams().toJSON());
                    writer.close();
                } catch (IOException e) {
                    throw new RuntimeException("Config file saving failed.", e);
                }
            }
		});

		configLoadButton.setOnAction(event -> {
            // File cfgLoadPath = getUIs().chooseFile("Choose config file", null,
            // FileWidget.OPEN_STYLE);
            File cfgLoadPath = new File("fit_config.txt");
            if (cfgLoadPath != null) {
                String cfgPath = cfgLoadPath.getPath();
                if (cfgPath.endsWith(".txt")) {
                    try {
                        String cfgStr = new String(Files.readAllBytes(cfgLoadPath.toPath()));
                        final FitParams<FloatType> params = new FitParams<>();
                        System.out.println(cfgStr);
                    } catch (IOException e) {
                        throw new RuntimeException("Config file loading failed.", e);
                    }
                } else {
                    throw new RuntimeException("Config file must be a text file.");
                }
            }
		});

	}

}
