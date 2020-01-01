package flimlib.flimj.ui.controller;

import java.net.URL;
import java.nio.IntBuffer;
import java.util.List;
import java.util.ResourceBundle;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.control.ChoiceBox;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import net.imagej.display.ColorTables;
import flimlib.flimj.FitParams;
import flimlib.flimj.FitResults;
import flimlib.flimj.ui.FitProcessor;
import flimlib.flimj.ui.Utils;
import flimlib.flimj.ui.controls.NumericSpinner;

import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.converter.RealLUTConverter;
import net.imglib2.display.screenimage.awt.ARGBScreenImage;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.FloorInterpolatorFactory;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

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

	/** The current height and width of the image in preview */
	private int imgW, imgH;

	/** The ScreenImage used to cache the colorized dataset */
	private ARGBScreenImage intensityScreenImage, resultScreenImage;

	/** The Images in display */
	private WritableImage intensityImage, resultImage;

	/** The minimal image side length */
	private final double IMAGE_MIN_SIZE = 256.0;

	/** The actual size on screen of a pixel from the dataset */
	private double pixelSize;

	/** The interpolator used to scale the image */
	private final InterpolatorFactory<FloatType, RandomAccessible<FloatType>> INT_INTERP =
			new FloorInterpolatorFactory<>();

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

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		resizeImage((int) IMAGE_MIN_SIZE, (int) IMAGE_MIN_SIZE);

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

		// snaps cursor to pixel center and adjusts preview position
		EventHandler<MouseEvent> paneClickedHandler = event -> {
			int pixelX = (int) Math.min(Math.round((event.getX()) / pixelSize - 0.5),
					(int) (imgW / pixelSize) - 1);
			int pixelY = (int) Math.min(Math.round((event.getY()) / pixelSize - 0.5),
					(int) (imgH / pixelSize) - 1);
			updateCsrPos(pixelX, pixelY);
		};

		lClickPane.setOnMouseClicked(paneClickedHandler);
		rClickPane.setOnMouseClicked(paneClickedHandler);

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

		long[] permutedCoordinates = FitProcessor.swapOutLtAxis(new long[] { 0, 1, 2 }, params.ltAxis);
		final int w = (int) results.intensityMap.dimension((int) permutedCoordinates[0]);
		final int h = (int) results.intensityMap.dimension((int) permutedCoordinates[1]);
		resizeImage(w, h);
		// results.intensityMap() is 3d
		loadAnotatedIntensityImage(Views.dropSingletonDimensions(results.intensityMap));

		// enable preview only if a fitted image/IRF intensity is available

		// update options if changed
		List<String> fpOptions = fp.getPreviewOptions();
		if (!showChoiceBox.getItems().equals(fpOptions)) {
			showChoiceBox.getItems().setAll(fpOptions);
		}
		// showChoiceBox.setValue(null);
		// if (prevewOptions != fp.getPreviewOptions()) {
		// prevewOptions = fp.getPreviewOptions();
		// // setAll() will cause the selection to be cleared and then set to either items[0]
		// // or the last available item (if that is still present), which will cause a
		// // refresh, so no need to refresh here
		// return;
		// }

		refreshResultImage();
		// }
	}

	@Override
	public void destroy() {
		intensityScreenImage = resultScreenImage = null;
		intensityImage = resultImage = null;
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
		loadARGBRAI(intensity, intensityScreenImage, INTENSITY_CONV, intensityImage);
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
		loadARGBRAI(result, resultScreenImage, RESULTS_CNVTR, resultImage);
		if (compositeResult) {
			composeIntensityRestult();
		}
	}

	/**
	 * Allocates new Image if size has changed and resizes (doubles size) image view until both
	 * dimensions are no less than {@link #IMAGE_MIN_SIZE}. The cursors are scaled as well.
	 * 
	 * @param w the new width
	 * @param h the new height
	 */
	private void resizeImage(final int w, final int h) {
		if (w != imgW || h != imgH) {
			pixelSize = Math.ceil(IMAGE_MIN_SIZE / Math.min(w, h));
			imgW = (int) (w * pixelSize);
			imgH = (int) (h * pixelSize);

			// scale cursors (so that it encircles exactly one pixel)
			lCsr.setScaleX(pixelSize);
			lCsr.setScaleY(pixelSize);
			rCsr.setScaleX(pixelSize);
			rCsr.setScaleY(pixelSize);

			// scale each displays
			intensityImageView.setFitWidth(imgW);
			intensityImageView.setFitHeight(imgH);
			intensityScreenImage = new ARGBScreenImage(imgW, imgH);
			intensityImage = new WritableImage(imgW, imgH);
			intensityImageView.setImage(intensityImage);

			resultImageView.setFitWidth(imgW);
			resultImageView.setFitHeight(imgH);
			resultScreenImage = new ARGBScreenImage(imgW, imgH);
			resultImage = new WritableImage(imgW, imgH);
			resultImageView.setImage(resultImage);

			csrXSpinner.setMax(w - 1);
			csrYSpinner.setMax(h - 1);
		}
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

		csrXSpinner.getNumberProperty().set((double) x);
		csrYSpinner.getNumberProperty().set((double) y);

		double cursorX = (x + 0.5) * pixelSize;
		double cursorY = (y + 0.5) * pixelSize;

		lCsr.setTranslateX(cursorX);
		lCsr.setTranslateY(cursorY);
		rCsr.setTranslateX(cursorX);
		rCsr.setTranslateY(cursorY);

		fp.setPreviewPos(x, y);
		requestUpdate();

		updating = false;
	}

	/**
	 * Converts each pixel in {@code src} with {@code converter} and put the image into
	 * {@code dest}.
	 * 
	 * @param src          the source image
	 * @param convertedSrc the scaled and colored screen image
	 * @param converter    the LUT converter
	 * @param dest         the destination JavaFX image
	 */
	private void loadARGBRAI(RandomAccessibleInterval<FloatType> src, ARGBScreenImage convertedSrc,
			RealLUTConverter<FloatType> converter, WritableImage dest) {
		// process
		double[] scale = new double[] {pixelSize, pixelSize};
		RandomAccessibleInterval<FloatType> scaled =
				getOps().transform().scaleView(src, scale, INT_INTERP);
		IterableInterval<ARGBType> colored =
				Converters.convert(Views.iterable(scaled), converter, new ARGBType());

		// copy to screen
		getOps().copy().iterableInterval(convertedSrc, colored);
		WritablePixelFormat<IntBuffer> pf = PixelFormat.getIntArgbPreInstance();
		dest.getPixelWriter().setPixels(0, 0, imgW, imgH, pf, convertedSrc.getData(), 0, imgW);
	}

	/**
	 * Composes the intensity image with the result image. The former is used as the luminance while
	 * the later the hew.
	 */
	private void composeIntensityRestult() {
		int[] newColor = new int[imgH * imgW];
		int[] lData = intensityScreenImage.getData();
		int[] hData = resultScreenImage.getData();

		for (int i = 0; i < newColor.length; i++) {
			int h = hData[i];
			int l = lData[i];

			int r = (int) (ARGBType.red(h) / 255.0 * ARGBType.red(l));
			int g = (int) (ARGBType.green(h) / 255.0 * ARGBType.green(l));
			int b = (int) (ARGBType.blue(h) / 255.0 * ARGBType.blue(l));
			int a = (int) (ARGBType.alpha(h) / 255.0 * ARGBType.alpha(l));
			newColor[i] = ARGBType.rgba(r, g, b, a);
		}
		resultImage.getPixelWriter().setPixels(0, 0, imgW, imgH, PixelFormat.getIntArgbInstance(),
				newColor, 0, imgW);
	}
}
