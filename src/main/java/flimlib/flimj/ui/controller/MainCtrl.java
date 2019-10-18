package flimlib.flimj.ui.controller;

import flimlib.flimj.ui.FitProcessor;
import javafx.fxml.FXML;

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

	@Override
	public void setFitProcessor(FitProcessor fp) {
		super.setFitProcessor(fp);
		plotTabController.setFitProcessor(fp);
		previewTabController.setFitProcessor(fp);
		settingsTabController.setFitProcessor(fp);
		exportTabController.setFitProcessor(fp);
		fp.setControllers(this, plotTabController, previewTabController, settingsTabController,
				exportTabController);
	}
}
