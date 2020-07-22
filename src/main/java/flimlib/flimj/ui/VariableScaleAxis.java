package flimlib.flimj.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Dimension2D;
import javafx.geometry.Side;
import javafx.scene.chart.ValueAxis;
import javafx.util.Duration;
import javafx.util.StringConverter;
import javafx.util.converter.NumberStringConverter;

/**
 * A variable (log/linear) scale axis implementation for JavaFX 2 charts.
 * <p>
 * Inspired by Kevin Senechal (kevinsdooapp &lt;kevin.senechal@dooapp.com &gt;)
 * 
 * @author Dasong Gao
 * 
 */
public class VariableScaleAxis extends ValueAxis<Number> {

	/**
	 * The bean for passing range information.
	 */
	public static class AxisRange {

		/** Lower and upper bound and tick difference of the axis (may be log values) */
		public double lowerBound, upperBound, tickUnit;

		/** Offset and scale in display units when calculating tick position */
		public double offset, scale;

		/** Is lowerBound, upperBound, tickUnit in log scale? */
		public boolean logScale;

		public AxisRange(double lowerBound, double upperBound, double tickUnit, double offset,
				double scale, boolean logScale) {
			this.lowerBound = lowerBound;
			this.upperBound = upperBound;
			this.tickUnit = tickUnit;
			this.offset = offset;
			this.scale = scale;
			this.logScale = logScale;
		}
	}

	/** The duration of animation in ms */
	private static final double ANIMATION_TIME = 700;

	/** The timeline used in animation */
	private final Timeline animationTimeline = new Timeline();

	/** The current tick unit */
	private final DoubleProperty tickUnit = new SimpleDoubleProperty();

	/** The current value-to-display-units scale */
	private final DoubleProperty scale = new SimpleDoubleProperty();

	/** The blending factor (0, 1) of the new range */
	private final DoubleProperty transitionBlending = new SimpleDoubleProperty();

	/** Is the axis currently in log scale? */
	private final BooleanProperty logScale = new BooleanPropertyBase(true) {
		@Override
		protected void invalidated() {
			// This will effect layout if we are auto ranging
			if (isAutoRanging()) {
				invalidateRange(data == null ? new ArrayList<>() : data);
				requestAxisLayout();
			}
		}

		@Override
		public Object getBean() {
			return VariableScaleAxis.this;
		}

		@Override
		public String getName() {
			return "logScale";
		}
	};

	/** The current and previous range object */
	private AxisRange currentRange, lastRange;

	/** The recorded min and max (may be log values) of data */
	private double dataMinValue, dataMaxValue;

	/** Last data series used to auto range the axis */
	private List<Number> data;

	/** The default label formatter */
	private StringConverter<Number> defaultFormatter = new NumberStringConverter();

	/**
	 * Creates a autoranging log axis.
	 */
	public VariableScaleAxis() {
		setLowerBound(1);
		setUpperBound(100);
		setMinorTickCount(10);
		init();
	}

	/**
	 * Creates a fixed range log axis.
	 * 
	 * @param lowerBound The axis lower bound
	 * @param upperBound The axis upper bound
	 * @param tickUnit   The tick unit
	 */
	public VariableScaleAxis(double lowerBound, double upperBound, double tickUnit) {
		super(lowerBound, upperBound);
		validateBounds(lowerBound, upperBound);
		setTickUnit(tickUnit);
		setMinorTickCount(10);
		init();
	}

	public final boolean getLogScale() {
		return logScale.get();
	}

	public final void setLogScale(boolean value) {
		logScale.set(value);
	}

	public final BooleanProperty logScaleProperty() {
		return logScale;
	}

	public final double getTickUnit() {
		return tickUnit.get();
	}

	public final void setTickUnit(double value) {
		tickUnit.set(value);
	}

	public final DoubleProperty tickUnitProperty() {
		return tickUnit;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Adapted from {@link #calculateTickValues}.
	 */
	@Override
	protected List<Number> calculateTickValues(double length, Object range) {
		final AxisRange rangeProps = (AxisRange) range;
		final double lowerBound = rangeProps.lowerBound;
		final double upperBound = rangeProps.upperBound;
		final double tickUnit = rangeProps.tickUnit;

		final List<Number> tickValues = new ArrayList<>();
		if (lowerBound == upperBound) {
			tickValues.add(toDisp(lowerBound));
		} else if (tickUnit <= 0) {
			tickValues.add(toDisp(lowerBound));
			tickValues.add(toDisp(upperBound));
		} else if (tickUnit > 0) {
			tickValues.add(toDisp(lowerBound));
			if (((upperBound - lowerBound) / tickUnit) > 2000) {
				// This is a ridiculous amount of major tick marks, something has probably gone
				// wrong
				System.err.println(
						"Warning we tried to create more than 2000 major tick marks on a NumberAxis. "
								+ "Lower Bound=" + toDisp(lowerBound) + ", Upper Bound="
								+ toDisp(upperBound) + ", Tick Unit=" + toDisp(tickUnit));
			} else if (lowerBound + tickUnit < upperBound) {
				// If tickUnit is integer, start with the nearest integer
				double major = Math.rint(tickUnit) == tickUnit ? Math.ceil(lowerBound)
						: lowerBound + tickUnit;
				final int count = (int) Math.ceil((upperBound - major) / tickUnit);
				for (int i = 0; major < upperBound && i < count; major += tickUnit, i++) {
					final double majorValue = toDisp(major);
					if (!tickValues.contains(majorValue)) {
						tickValues.add(majorValue);
					}
				}
			}
			tickValues.add(toDisp(upperBound));
		}
		return tickValues;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Adapted from {@link javafx.scene.chart.NumberAxis#calculateMinorTickMarks}.
	 */
	@Override
	protected List<Number> calculateMinorTickMarks() {
		final double lowerBound = toBnd(getLowerBound());
		final double upperBound = toBnd(getUpperBound());
		final double tickUnit = toBnd(getTickUnit());
		final int nMinorTick = Math.max(1, getMinorTickCount());

		final List<Number> minorTickMarks = new ArrayList<>();
		if (tickUnit > 0) {
			if (((upperBound - lowerBound) / tickUnit * nMinorTick) > 10000) {
				// This is a ridiculous amount of major tick marks, something has probably gone
				// wrong
				System.err.println(
						"Warning we tried to create more than 10000 minor tick marks on a NumberAxis. "
								+ "Lower Bound=" + lowerBound + ", Upper Bound=" + upperBound
								+ ", Tick Unit=" + tickUnit);
				return minorTickMarks;
			}

			// NB: var_ denote display values (same as var under linear scale, 10^var under log
			// scale)
			final boolean tickUnitIsInteger = Math.rint(tickUnit) == tickUnit;
			// fill the gap between lowerBound and the lowest major tick
			if (tickUnitIsInteger) {
				final double major = Math.floor(lowerBound);
				final double major_ = toDisp(major);
				final double next_ = toDisp(Math.min(major + tickUnit, upperBound));
				final double minorUnit = (next_ - major_) / (nMinorTick - 1);
				double minor = major_ + minorUnit;
				final int minorCount = (int) Math.ceil((next_ - minor) / minorUnit);
				for (int i = 0; minor < next_ && i < minorCount; minor += minorUnit, i++) {
					if (minor > lowerBound) {
						minorTickMarks.add(minor);
					}
				}
			}

			// find each major tick and linearly mark minor ticks
			double major = tickUnitIsInteger ? Math.ceil(lowerBound) : lowerBound;
			final int majorCount = (int) Math.ceil((upperBound - major) / tickUnit);
			for (int i = 0; major < upperBound && i < majorCount; major += tickUnit, i++) {
				final double major_ = toDisp(major);
				final double next_ = toDisp(Math.min(major + tickUnit, upperBound));
				final double minorUnit = (next_ - major_) / (nMinorTick - 1);
				double minor = major_ + minorUnit;
				int minorCount = (int) Math.ceil((next_ - minor) / minorUnit);
				for (int j = 0; minor < next_ && j < minorCount; minor += minorUnit, j++) {
					minorTickMarks.add(minor);
				}
			}
		}
		return minorTickMarks;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected AxisRange getRange() {
		return currentRange;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Adapted from {@link javafx.scene.chart.NumberAxis#setRange}.
	 */
	@Override
	protected void setRange(Object range, boolean animate) {
		if (range != null) {
			lastRange = currentRange;
			currentRange = (AxisRange) range;

			final double lowerBound = toDisp(currentRange.lowerBound);
			final double upperBound = toDisp(currentRange.upperBound);
			final double tickUnit = toDisp(currentRange.tickUnit);
			final double scale = (currentRange.scale);

			if (getLogScale())
				validateBounds(lowerBound, upperBound);

			final double oldLowerBound = getLowerBound();
			final double oldScale = getScale();
			setLowerBound(lowerBound);
			setUpperBound(upperBound);
			setTickUnit(tickUnit);
			setScale(scale);

			if (animate) {
				animationTimeline.stop();
				// NB: The original implementation uses currentLowerBound and scale to map
				// display positions. Here we introduce transitionBlending to generate a even
				// smoother transition. Useful when switching axis scale.
				animationTimeline.getKeyFrames().setAll( //
						new KeyFrame(Duration.ZERO, new KeyValue(currentLowerBound, oldLowerBound),
								new KeyValue(this.scale, oldScale),
								new KeyValue(transitionBlending, 0)),
						new KeyFrame(Duration.millis(ANIMATION_TIME),
								new KeyValue(currentLowerBound, lowerBound),
								new KeyValue(this.scale, scale),
								new KeyValue(transitionBlending, 1)));
				animationTimeline.play();
			} else {
				currentLowerBound.set(lowerBound);
				setScale(scale);
				transitionBlending.set(1);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getTickMarkLabel(Number value) {
		return Utils.prettyFmt(value);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Adapted from {@link javafx.scene.chart.NumberAxis#getDisplayPosition}.
	 */
	@Override
	public double getDisplayPosition(Number value) {
		final double newValue = value.doubleValue();
		final double lowerBound = currentLowerBound.get();
		final double newDelta = toBnd(newValue) - toBnd(lowerBound);

		// find position in [lowerBound upperBound] in the previous range
		double oldDelta = newDelta;
		if (lastRange != null) {
			oldDelta = lastRange.logScale && newValue > 0
					? Math.log10(newValue) - Math.log10(Math.max(lowerBound, 1e-10))
					: newValue - lowerBound;
		}

		final double blending = transitionBlending.get();
		final double delta = oldDelta * (1 - blending) + newDelta * blending;
		return getRange().offset + delta * getScale();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Adapted from {@link javafx.scene.chart.NumberAxis#autoRange}.
	 */
	@Override
	protected Object autoRange(double minValue, double maxValue, double length, double labelSize) {
		final boolean logScale = getLogScale();

		// xxxValue are short-circuited because they are not visible. See #invalidateRange
		minValue = logScale ? Math.log10(dataMinValue) : dataMinValue;
		maxValue = logScale ? Math.log10(dataMaxValue) : dataMaxValue;

		final Side side = getSide();
		// calculate the number of tick-marks we can fit in the given length
		// can never have less than 2 tick marks one for each end
		final int numOfTickMarks = Math.max((int) Math.floor(length / labelSize), 2);
		final int minorTickCount = Math.max(getMinorTickCount(), 1);

		double range = maxValue - minValue;

		if (range != 0 && range / (numOfTickMarks * minorTickCount) <= Math.ulp(minValue)) {
			range = 0;
		}
		// pad min and max by 2%, checking if the range is zero
		final double paddedRange = (range == 0) ? minValue == 0 ? 2 : Math.abs(minValue) * 0.02
				: Math.abs(range) * 1.02;
		final double padding = (paddedRange - range) / 2;
		// if min and max are not zero then add padding to them
		double paddedMin = minValue - padding;
		double paddedMax = maxValue + padding;

		if (true) {
			// check padding has not pushed min or max over zero line
			if ((paddedMin < 0 && minValue >= 0) || (paddedMin > 0 && minValue <= 0)) {
				// padding pushed min above or below zero so clamp to 0
				paddedMin = 0;
			}
			if ((paddedMax < 0 && maxValue >= 0) || (paddedMax > 0 && maxValue <= 0)) {
				// padding pushed min above or below zero so clamp to 0
				paddedMax = 0;
			}
		}

		// calculate tick unit for the number of ticks can have in the given data range
		double tickUnit = paddedRange / numOfTickMarks;
		// search for the best tick unit that fits
		double tickUnitRounded = 0;
		double minRounded = 0;
		double maxRounded = 0;
		int count = 0;
		double reqLength = Double.MAX_VALUE;
		String formatter = "0.00000000";
		// loop till we find a set of ticks that fit length and result in a total of less than 20
		// tick marks
		while (reqLength > length || count > 20) {
			int exp = (int) Math.floor(Math.log10(tickUnit));
			final double mant = tickUnit / Math.pow(10, exp);
			double ratio = mant;
			// only accept base 10 numbers in logscale
			if (mant > 5d || logScale) {
				exp++;
				ratio = 1;
			} else if (mant > 1d) {
				ratio = mant > 2.5 ? 5 : 2.5;
			}
			if (exp > 1) {
				formatter = "#,##0";
			} else if (exp == 1) {
				formatter = "0";
			} else {
				final boolean ratioHasFrac = Math.rint(ratio) != ratio;
				final StringBuilder formatterB = new StringBuilder("0");
				int n = ratioHasFrac ? Math.abs(exp) + 1 : Math.abs(exp);
				if (n > 0)
					formatterB.append(".");
				for (int i = 0; i < n; ++i) {
					formatterB.append("0");
				}
				formatter = formatterB.toString();

			}
			tickUnitRounded = ratio * Math.pow(10, exp);
			// move min and max to nearest tick mark
			minRounded = Math.floor(paddedMin / tickUnitRounded) * tickUnitRounded;
			maxRounded = Math.ceil(paddedMax / tickUnitRounded) * tickUnitRounded;
			// calculate the required length to display the chosen tick marks for real, this will
			// handle if there are huge numbers involved etc or special formatting of the tick mark
			// label text
			double maxReqTickGap = 0;
			double last = 0;
			count = (int) Math.ceil((maxRounded - minRounded) / tickUnitRounded);
			double major = minRounded;
			for (int i = 0; major <= maxRounded && i < count; major += tickUnitRounded, i++) {
				Dimension2D markSize =
						measureTickMarkSize(toDisp(major), getTickLabelRotation(), formatter);
				double size = side.isVertical() ? markSize.getHeight() : markSize.getWidth();
				if (i == 0) { // first
					last = size / 2;
				} else {
					maxReqTickGap = Math.max(maxReqTickGap, last + 6 + (size / 2));
				}
			}
			reqLength = (count - 1) * maxReqTickGap;
			tickUnit = tickUnitRounded;

			// fix for RT-35600 where a massive tick unit was being selected
			// unnecessarily. There is probably a better solution, but this works
			// well enough for now.
			if (numOfTickMarks == 2 && reqLength > length) {
				break;
			}
			if (reqLength > length || count > 20)
				tickUnit *= 2; // This is just for the while loop, if there are still too many
								// ticks
		}
		// calculate new scale
		final double newScale = calculateNewScale(length, minRounded, maxRounded);
		final double newOffset = getSide().isVertical() ? length : 0;
		// return new range
		return new AxisRange(minRounded, maxRounded, tickUnitRounded, newOffset, newScale,
				logScale);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Adapted from {@link javafx.scene.chart.NumberAxis#invalidateRange}.
	 */
	@Override
	public void invalidateRange(List<Number> data) {
		// save data in case axis scale changes (no new data feed)
		this.data = data;
		if (data.isEmpty()) {
			dataMinValue = getLowerBound();
			dataMaxValue = getUpperBound();
		} else {
			dataMinValue = Double.POSITIVE_INFINITY;
			dataMaxValue = Double.NEGATIVE_INFINITY;
		}
		final boolean positiveOnly = getLogScale();
		for (Number dataValue : data) {
			if (!positiveOnly || dataValue.doubleValue() > 0) {
				dataMinValue = Math.min(dataMinValue, dataValue.doubleValue());
				dataMaxValue = Math.max(dataMaxValue, dataValue.doubleValue());
			}
		}
		if (!Double.isFinite(dataMinValue) || !Double.isFinite(dataMaxValue)) {
			dataMinValue = getLowerBound();
			dataMaxValue = getUpperBound();
		}
		super.invalidateRange(data);
	}

	private void init() {
		// bind scale to upper class scale property to make it writable
		scaleProperty().addListener((obs, oldVal, newVal) -> scale.set(newVal.doubleValue()));
		scale.addListener((obs, oldVal, newVal) -> setScale(newVal.doubleValue()));
	}

	/**
	 * Validate the bounds by throwing an exception if the values are not conform to the mathematics
	 * log interval: (0, Double.MAX_VALUE]
	 * 
	 * @param lowerBound the new upper bound
	 * @param upperBound the new lower bound
	 * @throws IllegalArgumentException if the bounds are not valid
	 */
	private void validateBounds(double lowerBound, double upperBound)
			throws IllegalArgumentException {
		if (lowerBound < 0 || upperBound < 0 || lowerBound > upperBound) {
			throw new IllegalArgumentException(
					"The logarithmic range should be include to (0, Double.MAX_VALUE] "
							+ "and the lowerBound should be less than the upperBound");
		}
	}

	/**
	 * Convert data value from display space to axis bounds space depending on if the axis is log
	 * scale.
	 * 
	 * @param value the value to convert
	 * @return log_10(<code>value</code>) if log scale, <code>value</code> otherwise
	 */
	private double toBnd(double value) {
		return getLogScale() ? Math.log10(value) : value;
	}

	/**
	 * Convert data value from axis bounds space to display space depending on if the axis is log
	 * scale.
	 * 
	 * @param value the value to convert
	 * @return 10^<code>value</code> if log scale, <code>value</code> otherwise
	 */
	private double toDisp(double value) {
		return getLogScale() ? Math.pow(10, value) : value;
	}

	/**
	 * Measure the size of the label for given tick mark value. This uses the font that is set for
	 * the tick marks.
	 * <p>
	 * Adapted from {@link javafx.scene.chart.NumberAxis#measureTickMarkSize}.
	 *
	 * @param value        tick mark value
	 * @param rotation     The text rotation
	 * @param numFormatter The number formatter
	 * @return size of tick mark label for given value
	 */
	private Dimension2D measureTickMarkSize(Number value, double rotation, String numFormatter) {
		String labelText;
		StringConverter<Number> formatter = getTickLabelFormatter();
		if (formatter == null)
			formatter = defaultFormatter;
		// if(formatter instanceof DefaultFormatter) {
		// labelText = ((DefaultFormatter)formatter).toString(value, numFormatter);
		// } else {
		labelText = formatter.toString(value);
		// }
		return measureTickMarkLabelSize(labelText, rotation);
	}
}
