package flimlib.flimj.ui.controller;

import java.util.List;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import net.imagej.display.ColorTables;

import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.RealLUTConverter;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import org.controlsfx.control.PopOver;
import org.controlsfx.control.PopOver.ArrowLocation;

import flimlib.flimj.FitParams;
import flimlib.flimj.FitResults;
import flimlib.flimj.ui.FitProcessor;
import flimlib.flimj.ui.PreviewImageDisplay;
import flimlib.flimj.ui.UIException;
import flimlib.flimj.ui.Utils;
import flimlib.flimj.ui.controls.NumericSpinner;

/**
 * The controller of the "Preview" tab.
 */
public class PreviewCtrl extends AbstractCtrl {

	@FXML
	private Pane lClickPane, rClickPane;

	@FXML
	private Group lCsr, rCsr;

	@FXML
	private ImageView intensityImageView, resultImageView;

	@FXML
	private NumericSpinner csrXSpinner, csrYSpinner;

	@FXML
	private ChoiceBox<String> showChoiceBox, asChoiceBox;

	/** The two image previews */
	private PreviewImageDisplay intensityDisplay, resultDisplay;

	/** The colorbar pop over controller */
	private CBPopOverCtrl cbCtrl;

	/** The converter for the intensity (left) image */
	private final RealLUTConverter<FloatType> INTENSITY_CONV =
			new RealLUTConverter<>(0, 0, ColorTables.GRAYS);

	/** The converter for the result (right) image */
	private final RealLUTConverter<FloatType> RESULTS_CNVTR =
			new RealLUTConverter<>(0, 0, Utils.LIFETIME_LUT);

	/** Flags designating how the result is colorized */
	private boolean colorizeResult, compositeResult;

	/** True if l/rClickPane click handler has taken the update job */
	private boolean clickUpdate;

	/** The previous valid preview option (z, A, intensity, etc.) */
	private String lastValidPreviewOption;

	/** Mirrors fp.isPickingIRF(). Its listeners handle IRF mode entering/exiting events */
	private ObjectProperty<Boolean> pickingIRF;

	/** The controller for the colorbar pop over */
	public static class CBPopOverCtrl extends AbstractCtrl {

		@FXML
		/** The moving part */
		private VBox cbValCursor;

		@FXML
		private ImageView cbImageView;

		@FXML
		private Label cbMinValLabel, cbMaxValLabel, cbValLabel;

		private PopOver popOver;

		private WritableImage cbImage;

		private int cbWidth;

		private double cbMin, cbMax;

		private boolean visible;

		@Override
		public void initialize() {
			cbWidth = (int) cbImageView.getFitWidth();
			cbImage = new WritableImage(cbWidth, 1);
			cbImageView.setImage(cbImage);
		}

		public void setPopOver(PopOver popOver) {
			this.popOver = popOver;
		}

		/**
		 * Draw a new cb when it needs update.
		 * 
		 * @param converter the new color map
		 */
		public void setCB(RealLUTConverter<FloatType> converter) {
			for (int i = 0; i < cbWidth; i++) {
				cbImage.getPixelWriter().setArgb(i, 0,
						converter.getLUT().lookupARGB(0, cbWidth, i));
			}

			cbMin = converter.getMin();
			cbMax = converter.getMax();
			cbMinValLabel.setText(String.format("%.2g", cbMin));
			cbMaxValLabel.setText(String.format("%.2g", cbMax));
			cbValLabel.setText("0.0");
		}

		/**
		 * Changes text and moves the cursor to indicate the new value.
		 * 
		 * @param value the new value
		 */
		public void dispValue(double value) {
			cbValLabel.setText(String.format("%.2g", value));
			value = Math.min(Math.max(value, cbMin), cbMax);
			double pos = (value - cbMin) / (cbMax - cbMin + Double.MIN_VALUE);
			// move to position in 250ms
			TranslateTransition tt = new TranslateTransition(Duration.millis(250), cbValCursor);
			tt.toXProperty().set(cbWidth * (pos - 0.5));
			tt.play();
		}

		/**
		 * Pops over a node ({@link PreviewCtrl#lClickPane}/{@link PreviewCtrl#rClickPane})
		 * 
		 * @param owner the node to attach the pop over; {@code null} to hide.
		 */
		public void setOwner(Node owner) {
			if (owner != null) {
				visible = true;
				popOver.show(owner);
			} else {
				visible = false;
				// delay hide by 100ms
				// other wise fast swiping between two panes causes hide() to take
				// effect immediately after show() in the later pane
				new Timeline(new KeyFrame(Duration.millis(100), ae -> {
					if (!visible)
						popOver.hide();
				})).play();
			}
		}
	}

	@Override
	public void initialize() {
		intensityDisplay = new PreviewImageDisplay(lClickPane, lCsr, intensityImageView);
		resultDisplay = new PreviewImageDisplay(rClickPane, rCsr, resultImageView);

		pickingIRF = new SimpleObjectProperty<>(false);

		// make two int spinners
		csrXSpinner.setMin(0);
		csrXSpinner.setStepSize(1);
		csrXSpinner.setIntOnly(true);
		csrYSpinner.setMin(0);
		csrYSpinner.setStepSize(1);
		csrYSpinner.setIntOnly(true);

		final ObjectProperty<Double> csrSpinnerX = csrXSpinner.getNumberProperty();
		final ObjectProperty<Double> csrSpinnerY = csrYSpinner.getNumberProperty();
		final ObjectProperty<Double> intensityDisplayX = intensityDisplay.getCursorXProperty();
		final ObjectProperty<Double> intensityDisplayY = intensityDisplay.getCursorYProperty();
		final ObjectProperty<Double> resultDisplayX = resultDisplay.getCursorXProperty();
		final ObjectProperty<Double> resultDisplayY = resultDisplay.getCursorYProperty();

		// updates cursor position info on spinner change
		csrSpinnerX.bindBidirectional(intensityDisplayX);
		csrSpinnerY.bindBidirectional(intensityDisplayY);
		csrSpinnerX.bindBidirectional(resultDisplayX);
		csrSpinnerY.bindBidirectional(resultDisplayY);

		// handle spinner value changes, unless already handled by click handler below
		ChangeListener<Double> spinnerChangeListener = (obs, oldVal, newVal) -> {
			if (!clickUpdate)
				updateCoords(intensityDisplayX, intensityDisplayY, false);
		};
		csrSpinnerX.addListener(spinnerChangeListener);
		csrSpinnerY.addListener(spinnerChangeListener);

		// handle pane click event
		EventHandler<? super MouseEvent> lClickHandlerOld = lClickPane.getOnMouseClicked();
		EventHandler<? super MouseEvent> rClickHandlerOld = rClickPane.getOnMouseClicked();
		EventHandler<MouseEvent> paneClickHandler = event -> {
			// disable x, y property change handling
			clickUpdate = true;
			// move cursor, update x, y property, etc.
			if (event.getSource().equals(lClickPane)) {
				lClickHandlerOld.handle(event);
				updateCoords(intensityDisplayX, intensityDisplayY, false);
			} else {
				rClickHandlerOld.handle(event);
				// reroute coordinate to irf setting
				updateCoords(resultDisplayX, resultDisplayY, pickingIRF.get());
			}
			clickUpdate = false;
		};
		lClickPane.setOnMouseClicked(paneClickHandler);
		rClickPane.setOnMouseClicked(paneClickHandler);

		// creates cb pop over
		try {
			FXMLLoader loader = getFXMLLoader("preview-colorbar");
			PopOver popOver = new PopOver(loader.<VBox>load());
			popOver.setArrowLocation(ArrowLocation.TOP_CENTER);
			cbCtrl = loader.<CBPopOverCtrl>getController();
			cbCtrl.setPopOver(popOver);
		} catch (Exception e) {
			throw new UIException(e);
		}

		// make cbCtrl display the value under cursor
		EventHandler<MouseEvent> cbUpdateHandler = event -> {
			PreviewImageDisplay display =
					event.getSource() == lClickPane ? intensityDisplay : resultDisplay;

			double dispVal =
					display == null ? 0.0 : display.getValueUnderMouse(event.getX(), event.getY());
			cbCtrl.dispValue(dispVal);
		};
		// attach cb to the pane and display the corresponding bar
		EventHandler<MouseEvent> cbShowHandler = event -> {
			if (event.getSource() == lClickPane) {
				cbCtrl.setCB(INTENSITY_CONV);
				cbCtrl.setOwner(lClickPane);
			} else {
				cbCtrl.setCB(RESULTS_CNVTR);
				cbCtrl.setOwner(rClickPane);
			}
		};
		// hide cb
		EventHandler<MouseEvent> cbHideHandler = event -> cbCtrl.setOwner(null);

		lClickPane.setOnMouseMoved(cbUpdateHandler);
		rClickPane.setOnMouseMoved(cbUpdateHandler);
		lClickPane.setOnMouseEntered(cbShowHandler);
		rClickPane.setOnMouseEntered(cbShowHandler);
		lClickPane.setOnMouseExited(cbHideHandler);
		rClickPane.setOnMouseExited(cbHideHandler);

		showChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> {
			// HACK: The section below handles two corner cases to this change listener:
			// 1. The list of items is updated, and the selected item is in the new list:
			// -- ChoiceBox behavior: Clear the selection (set to null).
			// -- Handling: Retain the last known selection (the same one, but from the old list).
			// 2. The list of items is updated, but the selected item is no longer present:
			// -- ChoiceBox behavior: Retain the selection.
			// -- Handling: Clear the selection (the HACK below) and display the placeholder image.
			if (oldVal == null && newVal.equals(lastValidPreviewOption)) {
				// ignore update if we are recovering from the below situation
				return;
			}
			// this happens when the items list is changed
			if (newVal == null) {
				// use old option if it is still there
				List<String> items = showChoiceBox.getItems();
				lastValidPreviewOption =
						items.contains(lastValidPreviewOption) ? lastValidPreviewOption : null;
				showChoiceBox.setValue(lastValidPreviewOption);
				// enabled if there is a show option
				asChoiceBox.setDisable(lastValidPreviewOption == null);
				return;
			} else {
				lastValidPreviewOption = newVal;
			}
			asChoiceBox.setDisable(false);

			// entering and exiting IRF mode
			if ("IRF Intensity".equals(showChoiceBox.getValue())) {
				fp.setIsPickingIRF(true);
				requestUpdate();
				return;
			} else if ("IRF Intensity".equals(oldVal)) {
				fp.setIsPickingIRF(false);
				requestUpdate();
				return;
			}
			refreshResultImage();
		});
		asChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> {
			// asChoiceBox gives null if the item list is updated
			switch (newVal) {
				case "Grayscale":
					colorizeResult = compositeResult = false;
					break;
				case "Color":
					colorizeResult = true;
					compositeResult = false;
					break;
				case "Composite Color":
					colorizeResult = compositeResult = true;
					break;
			}
			refreshResultImage();
		});
		// enabled if there is a show option
		asChoiceBox.setDisable(true);

		pickingIRF.addListener(new ChangeListener<Boolean>() {

			private int savedIRFX, savedIRFY;

			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue,
					Boolean newValue) {
				if (newValue) {
					// unbind result display
					csrSpinnerX.unbindBidirectional(resultDisplayX);
					csrSpinnerY.unbindBidirectional(resultDisplayY);
					// restore cursor position in IRF mode from last time
					resultDisplayX.set((double) savedIRFX);
					resultDisplayY.set((double) savedIRFY);

					fp.setPreviewPos(savedIRFX, savedIRFY, true);
					return;
				} else {
					savedIRFX = resultDisplayX.get().intValue();
					savedIRFY = resultDisplayY.get().intValue();

					// rebind result display
					resultDisplayX.set(csrSpinnerX.get());
					resultDisplayY.set(csrSpinnerY.get());
					csrSpinnerX.bindBidirectional(resultDisplayX);
					csrSpinnerY.bindBidirectional(resultDisplayY);
				}
			}
		});

		colorizeResult = compositeResult = true;
	}

	@Override
	protected void refresh(FitParams<FloatType> params, FitResults results) {
		long[] permutedCoordinates =
				FitProcessor.swapOutLtAxis(new long[] {0, 1, 2}, params.ltAxis);
		final int w = (int) results.intensityMap.dimension((int) permutedCoordinates[0]);
		final int h = (int) results.intensityMap.dimension((int) permutedCoordinates[1]);

		csrXSpinner.setMax(w - 1);
		csrYSpinner.setMax(h - 1);

		loadAnotatedIntensityImage(fp.getPreviewImg("Intensity"));

		// load new options
		showChoiceBox.getItems().setAll(fp.getPreviewOptions());
		// HACK: If the selected value is removed from the list, {@link ChoiceBox#valueProperty()}
		// will retain the removed value and so no change event will be fired. Rather, {@link
		// ChoiceBox#selectionModelProperty()} will set its selected index to -1. When this happens,
		// the default behavior is to clear the #showChoiceBox selection and display the default
		// image placeholder.
		if (showChoiceBox.getSelectionModel().getSelectedIndex() == -1)
			showChoiceBox.setValue(null);

		refreshResultImage();

		pickingIRF.set(fp.isPickingIRF());
	}

	@Override
	public void destroy() {
		intensityDisplay.destroy();
		resultDisplay.destroy();
		intensityDisplay = resultDisplay = null;
		super.destroy();
	}

	/**
	 * Updates the result image and the preview options.
	 */
	private void refreshResultImage() {
		String showOption = showChoiceBox.getValue();
		if (showOption == null) {
			// show placeholder image
			resultDisplay.setImage(null, null, null);
			return;
		}
		loadAnotatedResultsImage(fp.getPreviewImg(showOption), colorizeResult, compositeResult);
	}

	/**
	 * Annotates the intensity image and load to the on-screen Image.
	 * 
	 * @param intensity the intensity data
	 */
	private void loadAnotatedIntensityImage(RandomAccessibleInterval<FloatType> intensity) {
		IterableInterval<FloatType> itr = Views.iterable(intensity);
		INTENSITY_CONV.setMax(getOps().stats().max(itr).getRealDouble());
		intensityDisplay.setImage(intensity, INTENSITY_CONV, null);
	}

	/**
	 * Annotates the result image and load to the on-screen Image.
	 * 
	 * @param result    the result data
	 * @param color     {@code true} if LUT should be applied
	 * @param composite {@code true} if the image is to be composed with intensity
	 */
	private void loadAnotatedResultsImage(RandomAccessibleInterval<FloatType> result, boolean color,
			boolean composite) {
		IterableInterval<FloatType> itr = Views.iterable(result);
		// TODO a more sensible range
		RESULTS_CNVTR.setMin(getOps().stats().percentile(itr, 10).getRealDouble());
		RESULTS_CNVTR.setMax(getOps().stats().percentile(itr, 90).getRealDouble());
		RESULTS_CNVTR.setLUT(colorizeResult ? Utils.LIFETIME_LUT : ColorTables.GRAYS);
		resultDisplay.setImage(result, RESULTS_CNVTR,
				compositeResult ? intensityDisplay.getArgbScreenImage() : null);
	}

	/**
	 * Updates the trans/IRF coordinates.
	 * 
	 * @param xProperty the new x coordinate in pixels
	 * @param yProperty the new y coordinate in pixels
	 * @param irf       whether the update is on trans or IRF coordinate
	 */
	private void updateCoords(final ObjectProperty<Double> xProperty,
			final ObjectProperty<Double> yProperty, final boolean irf) {
		final int x = xProperty.get().intValue();
		final int y = yProperty.get().intValue();

		fp.setPreviewPos(x, y, irf);
		requestUpdate();
	}
}
