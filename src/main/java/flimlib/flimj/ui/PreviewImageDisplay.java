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
package flimlib.flimj.ui;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.converter.RealLUTConverter;
import net.imglib2.display.screenimage.awt.ARGBScreenImage;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.FloatType;

/**
 * Manages a clickable image display in the Preview pannel.
 */
public class PreviewImageDisplay {

	/**
	 * Interface for a location-aware image annotator to postprocess LUT-converted colors in
	 * {@link PreviewImageDisplay#setImage}.
	 */
	@FunctionalInterface
	public static interface ImageAnnotator {

		/**
		 * Recolors the rendered pixel given a pointer to the source float value and the LUT
		 * converted color. The value and color can be retrived directly from
		 * <code>srcRA.get()</code> and <code>lutedRA.get()</code>. The implementation may refer to
		 * the location through e.g. <code>srcRA.getPosition()</code>.
		 * 
		 * @param srcRA   the {@link RandomAccess} pointing at the value being converted
		 * @param lutedRA the {@link RandomAccess} pointing at the converted color
		 * @return the annotated color
		 */
		public ARGBType annotate(RandomAccess<FloatType> srcRA, RandomAccess<ARGBType> lutedRA);
	}

	/** Threshold of pixScale change that necessitates resampling */
	private static final double RELOAD_THR = 1.5;

	/** The path to the logo image */
	private static final String ICON_PATH = "img/logo.png";

	/** The default image to show */
	private static final Image PLACEHOLDER_IMAGE =
			new Image(PreviewImageDisplay.class.getClassLoader().getResource(ICON_PATH).toString());

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

	/** The LUT-colored but unannotated image */
	private RandomAccessibleInterval<ARGBType> coloredImage;

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
	 * @return The LUT colored image, may be used by another display to composite the image
	 * @see #setImage
	 */
	public RandomAccessibleInterval<ARGBType> getColorImage() {
		return coloredImage;
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
	 * Shows an float-valued image, colored by a converter and possibly annotated by a annotator. If
	 * either of the first two arguments are <code>null</code>, the display will show the
	 * {@link #PLACEHOLDER_IMAGE}.
	 * 
	 * @param src       The source image
	 * @param converter The LUT converter
	 * @param annotator The post-conversion processor functional
	 */
	public void setImage(final RandomAccessibleInterval<FloatType> src,
			final RealLUTConverter<FloatType> converter, final ImageAnnotator annotator) {
		rawImage = src;

		final int oldW = imgW;
		final int oldH = imgH;

		if (src != null && converter != null) {
			imgW = (int) src.dimension(0);
			imgH = (int) src.dimension(1);
		} else {
			imgW = (int) PLACEHOLDER_IMAGE.getWidth();
			imgH = (int) PLACEHOLDER_IMAGE.getHeight();
		}

		// reallocate buffers
		if (oldW != imgW || oldH != imgH)
			screenImage = new ARGBScreenImage(imgW, imgH);

		if (src != null && converter != null) {
			coloredImage = Converters.convert(src, converter, new ARGBType());

			// convert and annotate image
			Cursor<ARGBType> dstCsr = screenImage.localizingCursor();
			RandomAccess<ARGBType> lutedRA = coloredImage.randomAccess();
			RandomAccess<FloatType> valRA = src.randomAccess();
			while (dstCsr.hasNext()) {
				dstCsr.fwd();
				lutedRA.setPosition(dstCsr);
				valRA.setPosition(dstCsr);

				dstCsr.get().set(annotator != null ? //
						annotator.annotate(valRA, lutedRA) : lutedRA.get());
			}

			view.setOpacity(1);

			clickPane.setVisible(true);
			cursor.setVisible(true);
		} else {
			// show placeholder
			SwingFXUtils.fromFXImage(PLACEHOLDER_IMAGE, screenImage.image());

			view.setOpacity(0.3);

			clickPane.setVisible(false);
			cursor.setVisible(false);
		}

		// resize with parent
		Bounds parentBounds = view.getParent().getLayoutBounds();
		fitSize(parentBounds.getWidth() - 10, parentBounds.getHeight() - 10);

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
