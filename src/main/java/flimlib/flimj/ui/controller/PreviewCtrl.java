package flimlib.flimj.ui.controller;

import java.util.List;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
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

	/** Circular update prevention */
	private boolean updating;

	/** The previous valid preview option (z, A, intensity, etc.) */
	private String lastValidPreviewOption;

	/** Used to save coordinates before switching between IRF selection and normal mode */
	private int savedX, savedY;

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

		// make two int spinners
		csrXSpinner.setStepSize(1);
		csrXSpinner.setIntOnly(true);
		csrYSpinner.setStepSize(1);
		csrYSpinner.setIntOnly(true);
		// updates cursor position info on spinner change
		csrXSpinner.getNumberProperty().addListener((obs, oldVal, newVal) -> {
			updateCsrPos(newVal.intValue(), csrYSpinner.getNumberProperty().get().intValue());
		});
		csrYSpinner.getNumberProperty().addListener((obs, oldVal, newVal) -> {
			updateCsrPos(csrXSpinner.getNumberProperty().get().intValue(), newVal.intValue());
		});
		csrXSpinner.getNumberProperty().bindBidirectional(intensityDisplay.getCursorXProperty());
		csrYSpinner.getNumberProperty().bindBidirectional(intensityDisplay.getCursorYProperty());
		csrXSpinner.getNumberProperty().bindBidirectional(resultDisplay.getCursorXProperty());
		csrYSpinner.getNumberProperty().bindBidirectional(resultDisplay.getCursorYProperty());

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

		// both CB are enabled after the first fit is done
		// showChoiceBox.setDisable(true);
		// asChoiceBox.setDisable(true);

		showChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> {
			if (oldVal == null && newVal.equals(lastValidPreviewOption)) {
				// ignore update if we are recovering from the below situation
				return;
			}
			// this happens when the items list is changed
			if (newVal == null) {
				// use old option if it is still there
				List<String> items = showChoiceBox.getItems();
				lastValidPreviewOption =
						items.contains(lastValidPreviewOption) ? lastValidPreviewOption
								: items.get(0);
				showChoiceBox.setValue(lastValidPreviewOption);
				return;
			} else {
				lastValidPreviewOption = newVal;
			}

			// special cases
			if ("IRF Intensity".equals(showChoiceBox.getValue())) {
				// restore cursor position in IRF mode from last time
				int prevIRFX = savedX;
				int prevIRFY = savedY;
				// save cursor position in normal mode from now
				savedX = csrXSpinner.getNumberProperty().get().intValue();
				savedY = csrYSpinner.getNumberProperty().get().intValue();
				fp.setIsPickingIRF(true);

				updateCsrPos(prevIRFX, prevIRFY);
			} else if ("IRF Intensity".equals(oldVal)) {
				// restore cursor position in preview (normal) mode from last time
				int prevPreviewX = savedX;
				int prevPreviewY = savedY;
				// save cursor position in IRF mode from now
				savedX = csrXSpinner.getNumberProperty().get().intValue();
				savedY = csrYSpinner.getNumberProperty().get().intValue();
				fp.setIsPickingIRF(false);

				updateCsrPos(prevPreviewX, prevPreviewY);
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

		colorizeResult = compositeResult = true;
	}

	@Override
	protected void refresh(FitParams<FloatType> params, FitResults results) {
		updateCsrPos(csrXSpinner.getNumberProperty().get().intValue(),
				csrYSpinner.getNumberProperty().get().intValue());

		long[] permutedCoordinates =
				FitProcessor.swapOutLtAxis(new long[] {0, 1, 2}, params.ltAxis);
		final int w = (int) results.intensityMap.dimension((int) permutedCoordinates[0]);
		final int h = (int) results.intensityMap.dimension((int) permutedCoordinates[1]);

		csrXSpinner.setMax(w - 1);
		csrYSpinner.setMax(h - 1);

		loadAnotatedIntensityImage(fp.getPreviewImg("Intensity"));

		// update options if changed
		List<String> fpOptions = fp.getPreviewOptions();
		if (!showChoiceBox.getItems().equals(fpOptions)) {
			showChoiceBox.getItems().setAll(fpOptions);
		}

		refreshResultImage();
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
	 * Updates the cursor position info.
	 * 
	 * @param x the new x value on the image
	 * @param y the new y value on the image
	 */
	private void updateCsrPos(int x, int y) {
		if (updating) {
			return;
		}
		updating = true;

		// coordinate is linked to the two PreviewImageDisplay's cursors
		csrXSpinner.getNumberProperty().set((double) x);
		csrYSpinner.getNumberProperty().set((double) y);

		fp.setPreviewPos(x, y);
		requestUpdate();

		updating = false;
	}
}
