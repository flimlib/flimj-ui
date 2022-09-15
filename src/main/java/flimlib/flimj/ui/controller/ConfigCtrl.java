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
package flimlib.flimj.ui.controller;

import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import java.io.File;
import java.nio.file.Files;
import java.io.FileWriter;
import java.io.IOException;
import org.scijava.widget.FileWidget;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import flimlib.flimj.FitParams;
import flimlib.flimj.ui.controls.NumericSpinner;
import flimlib.flimj.ui.FitProcessor.FitType;
import net.imglib2.type.numeric.real.FloatType;

/**
 * The controller of the "Config" tab.
 */
public class ConfigCtrl extends AbstractCtrl {
	@FXML
	private NumericSpinner binSizeSpinner;

    @FXML
	private Button configLoadButton;

	@FXML
	private Button configSaveButton;

	@Override
	public void initialize() {

		configSaveButton.setOnAction(event -> {
            File cfgSavePath = getUIs().chooseFile("Choose config save path", new File("fit_config.txt"),
            FileWidget.SAVE_STYLE);
            if (cfgSavePath != null) {
                try {
                    FileWriter writer = new FileWriter(cfgSavePath);
                    String paramsJSONString = fp.getParams().toJSON();
                    // add the binRadius to the config file
                    String binRadiusJSONPrefix = jsonPrefix("binRadius");
                    String paramsJSON = binRadiusJSONPrefix + fp.getBinRadius() + "," + paramsJSONString.substring(1);
                    // add the fitType to the config file
                    String fitTypeJSONPrefix = jsonPrefix("fitType");
                    paramsJSON = fitTypeJSONPrefix + String.format("\"%s\"", fp.getAlgo()) + "," + paramsJSON.substring(1);
                    writer.write(paramsJSON);
                    writer.close();
                } catch (IOException e) {
                    throw new RuntimeException("Config file saving failed.", e);
                }
            }
		});

		configLoadButton.setOnAction(event -> {
            File cfgLoadPath = getUIs().chooseFile("Choose config file", null,
            FileWidget.OPEN_STYLE);
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

    private static String jsonPrefix(String fieldName){
        String fieldString = String.format("  \"%s\": ", fieldName);
        return "{\n" + fieldString;
    }

}
