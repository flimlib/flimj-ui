package flimlib.flimj.ui.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import java.io.File;
import java.nio.file.Files;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.controlsfx.control.CheckComboBox;
import org.controlsfx.control.IndexedCheckModel;
import org.scijava.widget.FileWidget;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javax.swing.JFileChooser;
import net.imagej.ImgPlus;
import flimlib.flimj.FitParams;
import flimlib.flimj.FitResults;
import flimlib.flimj.ui.Utils;
import flimlib.flimj.ui.controls.NumericSpinner;
import flimlib.flimj.ui.FitProcessor.FitType;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;

/**
 * The controller of the "Export" tab.
 */
public class ConfigCtrl extends AbstractCtrl {
	@FXML
	private NumericSpinner binSizeSpinner;

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
            // File cfgSavePath = getUIs().chooseFile("Choose config save path", new File("fit_config.txt"),
            // FileWidget.SAVE_STYLE);
            File cfgSavePath = new File("fit_config.txt");
            if (cfgSavePath != null) {
                try {
                    FileWriter writer = new FileWriter(cfgSavePath);
                    String paramsJSON = fp.getParams().toJSON();
                    // add the binRadius to the config file
                    String paramsBinRadiusJSON = paramsJSON.substring(0, 1) + "\n" + "  \"binRadius\": "
                            + fp.getBinRadius() + "," + paramsJSON.substring(1);
                    String paramsBinRadiusFitTypeJSON = paramsBinRadiusJSON.substring(0, 1) + "\n" + "  \"fitType\": "
                            + fp.getAlgo() + "," + paramsBinRadiusJSON.substring(1);
                    // System.out.println(fp.getParams());
                    writer.write(paramsBinRadiusFitTypeJSON);
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

                        JsonElement jsonObj = JsonParser.parseString(cfgStr);
                        JsonElement binRadiusField = jsonObj.getAsJsonObject().get("binRadius");
                        JsonElement fitTypeField = jsonObj.getAsJsonObject().get("fitType");

                        int binRadius = 0;
                        if (binRadiusField != null) {
                            binRadius = binRadiusField.getAsInt();
                        } else {
                            // show an alert
                            Alert a = new Alert(AlertType.WARNING);
                            a.setContentText("Kernel size was not found in the config file. Using value of 0.");
                            a.show();
                        }

                        String fitType = "LMA";
                        if (fitTypeField != null) {
                            fitType = fitTypeField.getAsString();
                        } else {
                            // show an alert
                            Alert a = new Alert(AlertType.WARNING);
                            a.setContentText("fitType not found in the config file. Using value of LMA.");
                            a.show();
                        }

                        final FitParams<FloatType> params = FitParams.fromJSON(cfgStr);
                        fp.setAlgo(FitType.valueOf(fitType));
                        fp.setBinning(binRadius);
                        fp.updateParamsFromFile(params);
                        requestUpdate();

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
