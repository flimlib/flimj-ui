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

import java.io.IOException;
import org.scijava.log.LogService;
import org.scijava.object.ObjectService;
import org.scijava.ui.UIService;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import net.imagej.ops.OpService;
import flimlib.flimj.FitParams;
import flimlib.flimj.FitResults;
import flimlib.flimj.ui.FitProcessor;
import io.scif.services.DatasetIOService;
import net.imglib2.type.numeric.real.FloatType;

/**
 * The basis for a tab controller that is {@link Initializable} and refreshable by both external
 * control flow and other controllers backed by the same fit processor.
 */
public abstract class AbstractCtrl {

	public static final String FXML_DIR = "fxml/";

	protected FitProcessor fp;

	protected AbstractCtrl parentCtrl;

	private boolean blockUpdate;

	public static FXMLLoader getFXMLLoader(String name) throws IOException {
		ClassLoader cl = AbstractCtrl.class.getClassLoader();
		return new FXMLLoader(cl.getResource(FXML_DIR + name + ".fxml"));
	}

	public void initialize() {

	}

	/**
	 * Sets the fit processor behind the controller.
	 * 
	 * @param fp the fit processor
	 */
	public void setFitProcessor(FitProcessor fp) {
		this.fp = fp;
	}

	/**
	 * Sets the parent controller of this controller.
	 * 
	 * @param parentCtrl the parent controller of this controller
	 */
	public void setParentCtrl(AbstractCtrl parentCtrl) {
		this.parentCtrl = parentCtrl;
	}

	/**
	 * Called by external control flow to notify the controller to refresh itself.
	 */
	public void requestRefresh() {
		blockUpdate = true;
		refresh(fp.getParams(), fp.getResults());
		blockUpdate = false;
	}

	/**
	 * Release the reources occupied by fields in the controller (e.g. {@link #fp}).
	 */
	public void destroy() {
		// so that we can release the resources in datasets/imgs
		fp = null;
	}

	/**
	 * Retrieves the current fitting parameter.
	 * 
	 * @return the current fitting parameter
	 */
	protected FitParams<FloatType> getParams() {
		return fp.getParams();
	}

	/**
	 * Retrieves the current IRF information parameter.
	 * 
	 * @return the current IRF information
	 */
	protected FitParams<FloatType> getIRFInfo() {
		return fp.getIRFInfo();
	}

	/**
	 * Retrieves the current fitted results.
	 * 
	 * @return the current fitted results
	 */
	protected FitResults getResults() {
		return fp.getResults();
	}

	/**
	 * Gets the Op service in context.
	 * 
	 * @return the Op service
	 */
	protected OpService getOps() {
		return fp.getService(OpService.class);
	}

	/**
	 * Gets the UI service in context.
	 * 
	 * @return the UI service
	 */
	protected UIService getUIs() {
		return fp.getService(UIService.class);
	}

	/**
	 * Gets the object service in context.
	 * 
	 * @return the object service
	 */
	protected ObjectService getObs() {
		return fp.getService(ObjectService.class);
	}

	/**
	 * Gets the dataset service in context.
	 * 
	 * @return the dataset service
	 */
	protected DatasetIOService getDss() {
		return fp.getService(DatasetIOService.class);
	}

	/**
	 * Gets the log service in context.
	 * 
	 * @return the log service
	 */
	protected LogService getLogs() {
		return fp.getService(LogService.class);
	}

	/**
	 * Called by the controller to notify the fit processor to perform a fit and other controllers
	 * to update themselves based on the fit results.
	 */
	protected void requestUpdate() {
		if (blockUpdate) {
			return;
		}
		fp.updateFit();
		fp.refreshControllers();
	}

	/**
	 * The callback called when the controller is supposed to refresh itself based on the parameters
	 * and the results provided by the arguments.
	 * 
	 * @param params  the active fitting parameter
	 * @param results the active fitted results
	 */
	protected void refresh(FitParams<FloatType> params, FitResults results) {

	}
}
