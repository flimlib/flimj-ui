package flimlib.flimj.ui;

import java.util.Iterator;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.converter.RealLUTConverter;
import net.imglib2.display.screenimage.awt.ARGBScreenImage;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

/**
 * Manages a clickable image display in the Preview pannel.
 */
public class PreviewImageDisplay {

	/** Threshold of pixScale change that necessitates resampling */
	static final private double RELOAD_THR = 1.5;

	/** The pixel cursor */
	final private Group cursor;

	/** The number properties bound to the cursor's X and Y coordinates */
	final private ObjectProperty<Double> cursorX, cursorY;

	/** Handles the cursor's location change */
	final private ChangeListener<Double> cursorXYChangedHandler;

	/** The clickable overlay */
	final private Pane clickPane;

	/** The place to show image */
	final private ImageView view;

	/** Height and width of the source image */
	private int imgW, imgH;

	/** The actual size (in pixel) on screen of a pixel from the source image */
	private double pixScale, lastReloadPixScale;

	/** The underlying pixel cache */
	private WritableImage writableImage;

	/** The intermediate cache between IJ and FX */
	private ARGBScreenImage screenImage;

	/** The values */
	private RandomAccessibleInterval<FloatType> rawImage;

	public PreviewImageDisplay(final Pane pane, final Group cursor, final ImageView view) {
		this.clickPane = pane;
		this.cursor = cursor;
		this.view = view;
		cursorX = new SimpleObjectProperty<>();
		cursorX.set(0.0);
		cursorY = new SimpleObjectProperty<>();
		cursorY.set(0.0);

		// when clicked: change coordinate
		clickPane.setOnMouseClicked(event -> {
			cursorX.set(getMousePixCoord(event.getX(), clickPane.getWidth()));
			cursorY.set(getMousePixCoord(event.getY(), clickPane.getHeight()));
		});
		clickPane.setVisible(false);

		// when coordinate changed: move cursor
		cursorXYChangedHandler =
				(obs, oldVal, newVal) -> moveCursorImpl(cursorX.get(), cursorY.get());
		cursorX.addListener(cursorXYChangedHandler);
		cursorY.addListener(cursorXYChangedHandler);
		cursor.setVisible(false);

		// when parent resized: resize and possibly reload
		// HACK: update view size after parent nodes' resizing; inset of 10px allows shrinking
		ChangeListener<Bounds> bChangeListener = (obs, oldVal, newVal) -> Platform.runLater(() -> {
			fitSize(newVal.getWidth() - 10, newVal.getHeight() - 10);
			reloadImageIfNecessary();
		});
		view.getParent().layoutBoundsProperty().addListener(bChangeListener);
	}

	/**
	 * @return {@link #cursorX}
	 */
	public ObjectProperty<Double> getCursorXProperty() {
		return cursorX;
	}

	/**
	 * @return {@link #cursorY}
	 */
	public ObjectProperty<Double> getCursorYProperty() {
		return cursorY;
	}

	/**
	 * @return The pixel cache, may be used by another display to composite the image
	 * @see #setImage(RandomAccessibleInterval, RealLUTConverter, ARGBScreenImage)
	 */
	public ARGBScreenImage getArgbScreenImage() {
		return screenImage;
	}

	/**
	 * @param x the x coordinate of mouse
	 * @param y the y coordinate of mouse
	 * @return The value from source image under the mouse event
	 */
	public double getValueUnderMouse(final double x, double y) {
		int pixelX = (int) getMousePixCoord(x, clickPane.getWidth());
		int pixelY = (int) getMousePixCoord(y, clickPane.getHeight());

		if (rawImage == null)
			return Double.NaN;
		RandomAccess<FloatType> ra = rawImage.randomAccess();
		ra.setPosition(new int[] {pixelX, pixelY});
		return ra.get().getRealDouble();
	}

	/**
	 * Shows an float valued image, posibly multiplied the color by another image of the same size.
	 * 
	 * @param src               The source image
	 * @param converter         The LUT
	 * @param composeBrightness The multiplier image, <code>null</code> to skip
	 */
	public void setImage(final RandomAccessibleInterval<FloatType> src,
			final RealLUTConverter<FloatType> converter, final ARGBScreenImage composeBrightness) {
		rawImage = src;
		IterableInterval<ARGBType> colored =
				Converters.convert(Views.iterable(src), converter, new ARGBType());

		final int oldW = imgW;
		final int oldH = imgH;
		imgW = (int) src.dimension(0);
		imgH = (int) src.dimension(1);

		// reallocate buffers
		if (oldW != imgW || oldH != imgH)
			screenImage = new ARGBScreenImage(imgW, imgH);

		Iterator<ARGBType> dstItr = screenImage.iterator();
		Iterator<ARGBType> srcItr = colored.iterator();
		while (srcItr.hasNext() && dstItr.hasNext())
			dstItr.next().set(srcItr.next().get());

		// multiply brightness by rgb from composeBrightness
		if (composeBrightness != null) {
			int[] lData = composeBrightness.getData();
			int[] hData = screenImage.getData();

			for (int i = 0; i < hData.length; i++) {
				int h = hData[i];
				int l = lData[i];

				hData[i] = ARGBType.rgba( //
						(int) (ARGBType.red(h) / 255.0 * ARGBType.red(l)),
						(int) (ARGBType.green(h) / 255.0 * ARGBType.green(l)),
						(int) (ARGBType.blue(h) / 255.0 * ARGBType.blue(l)),
						(int) (ARGBType.alpha(h) / 255.0 * ARGBType.alpha(l)));
			}
		}

		// resize with parent
		Bounds parentBounds = view.getParent().getLayoutBounds();
		fitSize(parentBounds.getWidth() - 10, parentBounds.getHeight() - 10);

		// enable for the first time
		clickPane.setVisible(true);
		cursor.setVisible(true);

		// force update as content may change
		lastReloadPixScale = Double.MIN_VALUE;
		reloadImageIfNecessary();
	}

	/**
	 * @param pixScale The new {@link #pixScale}
	 */
	public void setPixScale(final double pixScale) {
		if (!Double.isFinite(pixScale) || Math.abs(this.pixScale - pixScale) < 1e-6
				|| pixScale <= 0)
			return;

		this.pixScale = pixScale;
		view.setFitWidth(imgW * pixScale);
		view.setFitHeight(imgH * pixScale);
		cursor.setScaleX(pixScale);
		cursor.setScaleY(pixScale);
		moveCursorImpl(cursorX.get(), cursorY.get());
	}

	/**
	 * Moves the mouse cursor to the desired location. Updates {@link #cursorX}.
	 * 
	 * @param x The new cursor X
	 * @param y The new cursor Y
	 */
	public void moveCursor(final double x, final double y) {
		cursorX.set(x);
		cursorY.set(y);
	}

	/**
	 * Clean up.
	 */
	public void destroy() {
		cursorX.removeListener(cursorXYChangedHandler);
		cursorY.removeListener(cursorXYChangedHandler);
	}

	/**
	 * Move the cursor to the desired location.
	 * 
	 * @param x The new cursor X
	 * @param y The new cursor Y
	 */
	private void moveCursorImpl(final Double x, final Double y) {
		double cursorX = (x + 0.5) * pixScale;
		double cursorY = (y + 0.5) * pixScale;
		cursor.setTranslateX(cursorX);
		cursor.setTranslateY(cursorY);
	}

	/**
	 * Make the view fit the size.
	 * 
	 * @param w the desired width
	 * @param h the desired height
	 */
	private void fitSize(final double w, final double h) {
		double pixScaleX = w / imgW;
		double pixScaleY = h / imgH;
		setPixScale(Math.min(pixScaleX, pixScaleY));
	}

	/**
	 * Converts event coordinate to pixel coordinate in the image
	 * 
	 * @param eventCoord x/ycoordinate of the event
	 * @param imgWH      W/H of the image
	 * @return coordinate of pixel at which the event occurs
	 */
	private double getMousePixCoord(double eventCoord, double imgWH) {
		return Math.min(Math.round(eventCoord / pixScale - 0.5), (int) (imgWH / pixScale) - 1);
	}

	/**
	 * Reloads the image only if the ratio between {@link #pixScale} and {@link #lastReloadPixScale}
	 * or the inverse is no less than RELOAD_THR because small pixScale steps (e.g. during window
	 * resizing) marginally improves appearance.
	 */
	private void reloadImageIfNecessary() {
		if (screenImage == null || (Math.max(pixScale / lastReloadPixScale,
				lastReloadPixScale / pixScale) < RELOAD_THR))
			return;
		lastReloadPixScale = pixScale;

		writableImage = new WritableImage((int) view.getFitWidth(), (int) view.getFitHeight());
		view.setImage(writableImage);

		// manual nearest neighbor sampling
		PixelWriter pw = writableImage.getPixelWriter();
		RandomAccess<ARGBType> ra = screenImage.randomAccess();
		long[] position = new long[2];
		final double wiW = writableImage.getWidth();
		final double wiH = writableImage.getHeight();
		for (int x = 0; x < wiW; x++)
			for (int y = 0; y < wiH; y++) {
				position[0] = Math.round(x / wiW * (imgW - 1));
				position[1] = Math.round(y / wiH * (imgH - 1));
				ra.setPosition(position);
				pw.setArgb(x, y, ra.get().get());
			}
	}
}
