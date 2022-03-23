package flimlib.flimj.ui.controller;

import flimlib.flimj.ui.FitProcessor;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;

/**
 * The main controller.
 */
public class MainCtrl extends AbstractCtrl {

	@FXML
	private PlotCtrl plotTabController;

	@FXML
	private PreviewCtrl previewTabController;

	@FXML
	private SettingsCtrl settingsTabController;

	@FXML
	private ExportCtrl exportTabController;

	@FXML
	private ConfigCtrl configTabController;

	@FXML
	private BorderPane windowOverlayAssembly;

	@FXML
	private ProgressIndicator busyIndicator;

	@Override
	public void setFitProcessor(FitProcessor fp) {
		super.setFitProcessor(fp);
		plotTabController.setFitProcessor(fp);
		plotTabController.setParentCtrl(this);
		previewTabController.setFitProcessor(fp);
		previewTabController.setParentCtrl(this);
		settingsTabController.setFitProcessor(fp);
		settingsTabController.setParentCtrl(this);
		exportTabController.setFitProcessor(fp);
		exportTabController.setParentCtrl(this);
		configTabController.setFitProcessor(fp);
		configTabController.setParentCtrl(this);
		fp.setControllers(this, plotTabController, previewTabController, settingsTabController,
				exportTabController, configTabController);
	}

	/**
	 * Set the state of progress overlay. Numbers in [0, 1) will be shown as
	 * percentage; 1 for "Done"; -1 for indeterminate; <code>null</code> for
	 * disabling the overlay. When the overlay is active, any other UI components
	 * will be hidden behind and not accessible.
	 *
	 * @param progress the progress to show
	 */
	public void setProgress(Double progress) {
		if (progress != null) {
			windowOverlayAssembly.setVisible(true);
			busyIndicator.setProgress(progress);
		} else
			windowOverlayAssembly.setVisible(false);
	}
}
