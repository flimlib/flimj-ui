package flimlib.flimj.ui.controller;

import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.AnchorPane;
import org.controlsfx.control.HiddenSidesPane;
import org.controlsfx.control.SegmentedButton;
import flimlib.flimj.FitParams;
import flimlib.flimj.FitResults;
import flimlib.flimj.ui.Utils;
import flimlib.flimj.ui.VariableScaleAxis;
import flimlib.flimj.ui.controls.NumericSpinner;
import net.imglib2.type.numeric.real.FloatType;

/**
 * The controller of the "Plot" tab.
 */
public class PlotCtrl extends AbstractCtrl {

	private static final int TRN_IDX = 0;
	private static final int FIT_IDX = 1;
	private static final int RES_IDX = 2;
	private static final int IRF_IDX = 3;

	private static final int BEG_IDX = 0;
	private static final int END_IDX = 1;

	private static final int N_PLOTS = 4;

	/** cursors */
	@FXML
	private Group lCsr, rCsr;

	/** cursor position spinners */
	@FXML
	private NumericSpinner lCsrSpinner, rCsrSpinner;

	/** cursor areas */
	@FXML
	private AnchorPane fitPlotAreaPane;

	/** plots */
	@FXML
	private LineChart<Number, Number> fitPlotChart, resPlotChart;

	@FXML
	private TextField phtnCntTextField;

	@FXML
	private VariableScaleAxis fitPlotYAxis;

	@FXML
	private HiddenSidesPane plotAreaSidePane;

	@FXML
	private SegmentedButton fitYScaleSB;

	@FXML
	private ImageView frostImageView;

	/** cursor positions */
	private ObjectProperty<Double> lCsrPos, rCsrPos;

	/** cursor positions (index) */
	private ObjectProperty<Integer> fitStart, fitEnd;

	/** trans.length - 1 */
	private int nIntervals;

	/** plot series */
	private Series<Number, Number>[] plotSeries;

	/** temp lists of points */
	private List<Data<Number, Number>>[] dataLists;

	private int cursorIdc[][];

	/** lookup table for photon count before an index */
	private float[] prefixSum;

	private boolean csrBeingDragged;

	/**
	 * additive adjustment to [lr]CsrPos values. Used to expand the plotted portion when IRF is
	 * present
	 */
	private double nonIRFCursorAdj[];

	@Override
	@SuppressWarnings("unchecked")
	public void initialize() {
		// initialize properties with invalid values (corrected by refresh())
		lCsrPos = new SimpleObjectProperty<>();
		lCsrPos.set(-1.0);
		rCsrPos = new SimpleObjectProperty<>();
		rCsrPos.set(-1.0);
		fitStart = new SimpleObjectProperty<>();
		fitStart.set(-1);
		fitEnd = new SimpleObjectProperty<>();
		fitEnd.set(-1);
		lCsrSpinner.setMin(0.0);
		rCsrSpinner.setMin(0.0);
		lCsrSpinner.setMax(0.0);
		rCsrSpinner.setMax(0.0);

		initListeners(rCsr, rCsrPos, rCsrSpinner, fitEnd);
		initListeners(lCsr, lCsrPos, lCsrSpinner, fitStart);

		fitPlotAreaPane.widthProperty().addListener((obs, oldVal, newVal) -> {
			// == 0 at init
			if (oldVal.floatValue() != 0) {
				FitResults rslt = getResults();
				if (getParams().trans != null & rslt.fitted != null && rslt.residuals != null) {
					plotFit(getParams().trans, getParams().instr, getResults().residuals,
							getResults().fitted, getParams().xInc, 0);
				}
			}
		});

		plotSeries = (Series<Number, Number>[]) new Series[N_PLOTS];
		dataLists = (ArrayList<Data<Number, Number>>[]) new ArrayList[N_PLOTS];
		cursorIdc = new int[N_PLOTS][2];
		nonIRFCursorAdj = new double[2];

		// put series into chart
		fitPlotChart.getData().add(plotSeries[TRN_IDX] = new Series<Number, Number>());
		fitPlotChart.getData().add(plotSeries[IRF_IDX] = new Series<Number, Number>());
		fitPlotChart.getData().add(plotSeries[FIT_IDX] = new Series<Number, Number>());
		resPlotChart.getData().add(plotSeries[RES_IDX] = new Series<Number, Number>());
		// auto ranging prevents manual adjustment of bounds
		fitPlotChart.getXAxis().setAutoRanging(false);
		resPlotChart.getXAxis().setAutoRanging(false);
		// data point lists for swapping points in/out of series
		for (int i = 0; i < N_PLOTS; i++) {
			dataLists[i] = new ArrayList<Data<Number, Number>>();
		}

		// dummy
		prefixSum = new float[1];

		// frost glass effect for plot settings:
		// https://en.it1352.com/article/f2643fc245cd47d78c0a98e13068207f.html
		// TL;DR: Snapshot covered portion, crop to size of the pannel and set as background using
		// StackPane with blur effect

		// resize internal Image on ImageView size change
		ChangeListener<Number> frostResizeListener = (obs, oldVal, newVal) -> {
			int newWidth = (int) frostImageView.getFitWidth();
			int newHeight = (int) frostImageView.getFitHeight();
			if (newWidth > 0 && newHeight > 0) {
				frostImageView.setImage(new WritableImage(newWidth, newHeight));
			}
		};
		frostImageView.fitWidthProperty().addListener(frostResizeListener);
		frostImageView.fitHeightProperty().addListener(frostResizeListener);
		SnapshotParameters sp = new SnapshotParameters();

		// recalculate trigger distance on sliding (otherwise clicking on controls on side pane may
		// cause it to defocus and send the pane back)
		final double triggerDistance = plotAreaSidePane.getTriggerDistance();
		plotAreaSidePane.getRight().boundsInParentProperty().addListener((obs, oldVal, newVal) -> {
			final double slideDist = Math.abs(newVal.getMinX() - plotAreaSidePane.getWidth());
			plotAreaSidePane.setTriggerDistance(Math.max(slideDist, triggerDistance));

			// render the covered portion into the background image
			// HACK: move left boundary by blur radius (defined in plot-tab.fxml) to prevent
			// location shift
			sp.setViewport(new Rectangle2D(newVal.getMinX() + 30, 0, newVal.getWidth(),
					newVal.getHeight()));
			plotAreaSidePane.getContent().snapshot(sp, (WritableImage) frostImageView.getImage());
		});

		// change y scale on toggle
		fitPlotYAxis.setLogScale(false);
		fitYScaleSB.getToggleGroup().selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
			// disalbe diselection
			// see https://stackoverflow.com/a/50667161
			if (newVal == null)
				oldVal.setSelected(true);
			else if (oldVal != null)
				fitPlotYAxis.setLogScale("log".equals(newVal.getUserData()));
		});
	}

	@Override
	protected void refresh(FitParams<FloatType> params, FitResults results) {
		nIntervals = params.trans.length - 1;

		lCsrSpinner.setMax(nIntervals * params.xInc);
		rCsrSpinner.setMax(nIntervals * params.xInc);

		lCsrSpinner.setStepSize(params.xInc);
		rCsrSpinner.setStepSize(params.xInc);

		fitStart.set((fp.isPickingIRF() ? getIRFInfo() : params).fitStart);
		// the interval in the plot is [start, end]
		fitEnd.set((fp.isPickingIRF() ? getIRFInfo() : params).fitEnd - 1);

		Platform.runLater(() -> {
			FitResults rs = fp.getResults();
			if (rs == null || rs.param == null) {
				return;
			}

			int irfLength = params.instr == null ? 0 : params.instr.length;
			plotFit(params.trans, getIRFInfo().trans, rs.residuals, rs.fitted, params.xInc,
					irfLength);
			phtnCntTextField.setText(getphtnCnt());
		});

	}

	/**
	 * Adds change listeners to critical values so that they work together.
	 * 
	 * @param csr     the cursor
	 * @param csrPos  the cursor position in [0, 1]
	 * @param spinner the spinner associated with the cursor position
	 * @param index   the integer index associated with the cursor position
	 */
	private void initListeners(Group csr, ObjectProperty<Double> csrPos, NumericSpinner spinner,
			ObjectProperty<Integer> index) {
		final boolean isLCsr = csr == lCsr;
		// csrPos <-> csrX / width
		fitPlotAreaPane.widthProperty().addListener((obs, oldVal, newVal) -> {
			csr.setTranslateX(csrPos.get() * newVal.doubleValue());
		});
		csr.translateXProperty().addListener((obs, oldVal, newVal) -> {
			csrPos.set(newVal.doubleValue() / fitPlotAreaPane.getWidth());
		});

		// csrPos -> fitStart/End / nBins
		index.addListener((obs, oldVal, newVal) -> {
			csrPos.set(newVal.doubleValue() / nIntervals);
			phtnCntTextField.setText(getphtnCnt());

			// update only when triggered from other sources (e.g. textfield)
			// changes triggered by drag are handled by csr.setOnMouseReleased
			if (!csrBeingDragged) {
				// update bounds in param storage
				updateParam(isLCsr, newVal);
			}
		});

		// csrPos -> spinnerValue / (nBins * xinc)
		spinner.getNumberProperty().addListener((obs, oldVal, newVal) -> {
			csrPos.set(newVal / (nIntervals * getParams().xInc));
		});

		// fitStart/End -> round(csrPos * nBins)
		// csrX -> csrPos * width
		// spinnerValue -> csrPos * nBins * xinc
		csrPos.addListener(new ChangeListener<Double>() {

			/** Prevents circular change */
			private boolean beingChanged;

			@Override
			public void changed(ObservableValue<? extends Double> obs, Double oldVal,
					Double newVal) {
				// block circular update
				if (beingChanged) {
					return;
				}
				beingChanged = true;

				double bins = newVal * nIntervals;
				index.set((int) Math.round(bins));
				csr.translateXProperty().set(newVal * fitPlotAreaPane.getWidth());
				spinner.setClamped(bins * getParams().xInc);

				if (isLCsr) {
					rCsrSpinner.setMin(spinner.getNumberProperty().get());
				} else {
					lCsrSpinner.setMax(spinner.getNumberProperty().get());
				}

				if (fp.isPickingIRF()) {
					adjustPlottedPortion(isLCsr, IRF_IDX, newVal);
				} else {
					double adj = isLCsr ? nonIRFCursorAdj[0] : nonIRFCursorAdj[1];
					adjustPlottedPortion(isLCsr, FIT_IDX, newVal + adj);
					adjustPlottedPortion(isLCsr, RES_IDX, newVal + adj);
				}

				// unblock update
				beingChanged = false;
			}
		});

		// disable update when cursor is being dragged
		csr.setOnMousePressed(event -> {
			csrBeingDragged = true;
		});

		// snap cursors when drag is done
		csr.setOnMouseReleased(event -> {
			csrPos.set(index.get().doubleValue() / nIntervals);

			csrBeingDragged = false;

			// update bounds in param storage
			updateParam(isLCsr, index.get());
		});
	}

	/**
	 * Retrieves the photon count within the interval [fitStart, fitEnd].
	 * 
	 * @return the photon count
	 */
	private String getphtnCnt() {
		int start = fitStart.get();
		int end = fitEnd.get();
		if (start < 0 || start >= prefixSum.length || end < -1 || end >= prefixSum.length - 1) {
			return "";
		}
		return Utils.prettyFmt(prefixSum[end + 1] - prefixSum[start]);
	}

	/**
	 * Updates param in fit processors when indices are changed.
	 * 
	 * @param isLCsr true if change is on left cursor (start)
	 * @param newVal the updated value
	 */
	private void updateParam(boolean isLCsr, int newVal) {
		if (isLCsr)
			(fp.isPickingIRF() ? getIRFInfo() : getParams()).fitStart = newVal;
		else
			(fp.isPickingIRF() ? getIRFInfo() : getParams()).fitEnd = newVal + 1;

		// load irf on bound updates
		if (fp.isPickingIRF())
			fp.updateIRFRange();

		requestUpdate();
	}

	/**
	 * Updates the portion displayed for all series w.r.t. their cursors.
	 */
	private void adjustPlottedPortion() {
		// restore index counters
		for (int i = 0; i < N_PLOTS; i++) {
			if (fp.isPickingIRF() ^ i != IRF_IDX)
				cursorIdc[i][BEG_IDX] = cursorIdc[i][END_IDX] = 0;
		}

		double lCsrPosValue = lCsrPos.get();
		double rCsrPosValue = rCsrPos.get();

		if (fp.isPickingIRF()) {
			// zoom in to IRF if too small
			// fitPlotChart.setAnimated(true);
			// ((NumberAxis) fitPlotChart.getYAxis()).setAutoRanging(false);
			// FitParams<FloatType> irf = getIRFInfo();
			// float max = Float.NEGATIVE_INFINITY;
			// for (int i = irf.fitStart; i < irf.fitEnd; i++)
			// max = Math.max(irf.trans[i], max);
			// ((NumberAxis) fitPlotChart.getYAxis()).setUpperBound(max * 1.1);
			// ((NumberAxis) fitPlotChart.getYAxis()).setLowerBound(0);
			// fitPlotChart.setAnimated(false);

			adjustPlottedPortion(true, IRF_IDX, lCsrPosValue);
			adjustPlottedPortion(false, IRF_IDX, rCsrPosValue);

			// unchanged (just crop what it looked like last time)
			adjustPlottedPortion(true, FIT_IDX, -1);
			adjustPlottedPortion(false, FIT_IDX, -1);
			adjustPlottedPortion(true, RES_IDX, -1);
			adjustPlottedPortion(false, RES_IDX, -1);
		} else {
			lCsrPosValue += nonIRFCursorAdj[0];
			rCsrPosValue += nonIRFCursorAdj[1];
			// fitPlotChart.setAnimated(true);
			// ((NumberAxis) fitPlotChart.getYAxis()).setAutoRanging(true);
			// fitPlotChart.setAnimated(false);

			adjustPlottedPortion(true, FIT_IDX, lCsrPosValue);
			adjustPlottedPortion(false, FIT_IDX, rCsrPosValue);
			adjustPlottedPortion(true, RES_IDX, lCsrPosValue);
			adjustPlottedPortion(false, RES_IDX, rCsrPosValue);

			// unchanged (just crop what it looked like last time)
			adjustPlottedPortion(true, IRF_IDX, -1);
			adjustPlottedPortion(false, IRF_IDX, -1);
		}
	}

	/**
	 * The one-liner for {@link #adjustPlottedPortion(boolean, Series, List, double, int)}
	 * 
	 * @param isLCsr
	 * @param plotIdx
	 * @param csrPosValue
	 */
	private void adjustPlottedPortion(boolean isLCsr, int plotIdx, double csrPosValue) {
		int begEnd = isLCsr ? BEG_IDX : END_IDX;
		cursorIdc[plotIdx][begEnd] = adjustPlottedPortion(isLCsr, plotSeries[plotIdx],
				dataLists[plotIdx], csrPosValue, cursorIdc[plotIdx][begEnd]);
	}

	/**
	 * Adjusts the portion of the {@link Series} displayed according to the change of the cursor.
	 * 
	 * @param isLCsr      {@code true} the cursor is {@link #lCsr}
	 * @param series      the data series in question
	 * @param dataList    the list of (x, y) data
	 * @param csrPosValue the position of the cursor
	 * @param lastIdx     last known distance to data boundaries (left cursor w.r.t. the first
	 *                    element and right cursor the last in {@code dataList}
	 * @return the updated distance
	 */
	private int adjustPlottedPortion(boolean isLCsr, Series<Number, Number> series,
			List<Data<Number, Number>> dataList, double csrPosValue, int lastIdx) {
		ObservableList<Data<Number, Number>> data = series.getData();
		int sz = dataList.size();
		if (sz == 0) {
			return lastIdx;
		}

		int curIdx;
		if (csrPosValue < 0 || csrPosValue > 1) {
			// in this case, simply crop the plot
			curIdx = lastIdx;
			lastIdx = 0;
		} else {
			curIdx = (int) Math.round(csrPosValue * (sz - 1));
			curIdx = isLCsr ? curIdx : sz - curIdx - 1;
		}

		// determine whether to add or remove from the displaying set
		int diff = curIdx - lastIdx;
		if (diff < 0) {
			if (isLCsr) {
				data.addAll(0, dataList.subList(curIdx, lastIdx));
			} else {
				data.addAll(dataList.subList(sz - lastIdx, sz - curIdx));
			}
			lastIdx = curIdx;
		} else if (diff > 0) {
			// remove doesn't work well with animation
			series.getChart().setAnimated(false);
			if (isLCsr) {
				data.remove(0, diff);
			} else {
				data.remove(data.size() - diff, data.size());
			}
			series.getChart().setAnimated(true);
			lastIdx = curIdx;
		}
		return lastIdx;
	}

	/**
	 * Plots the fitted function as well as the transient data and residuals.
	 * 
	 * @param trans the transient series
	 * @param xInc  the x (time) increment
	 */
	private void plotFit(float[] trans, float[] instr, float[] residuals, float[] yFit, float xInc,
			int irfLength) {
		final int fitStart = getParams().fitStart;
		final float xMax = (trans.length - 1) * xInc;
		int irfPrefixLen = Math.min(irfLength, fitStart);

		nonIRFCursorAdj[0] = -irfLength * xInc / 2;

		instr = instr == null ? new float[0] : instr;
		// resize
		prefixSum = trans.length == (trans.length + 1) ? prefixSum : new float[trans.length + 1];

		for (int i = 0; i < trans.length; i++) {
			final float t = i * xInc;
			final float data = trans[i];
			setData(dataLists[TRN_IDX], i, t, data);
			// used for photon count later
			if (!fp.isPickingIRF()) {
				prefixSum[i + 1] = prefixSum[i] + data;
			}

			final int idx = i - fitStart + irfPrefixLen;
			float y = idx >= 0 && idx < yFit.length ? yFit[idx] : 0;
			// NaN or Inf hangs the plotting thread
			y = Float.isFinite(y) ? y : 0;
			float r = idx >= 0 && idx < residuals.length ? residuals[idx] : 0;
			r = Float.isFinite(r) ? r : 0;
			setData(dataLists[FIT_IDX], i, t, y);
			setData(dataLists[RES_IDX], i, t, r);
		}

		int irfPlotOffset = 0;
		int irfDataOffset = 0;
		if (!fp.isPickingIRF()) {
			irfPlotOffset = getParams().fitStart;
			irfDataOffset = getIRFInfo().fitStart;
		}
		for (int i = 0; i < instr.length; i++) {
			// make IRF follow the start cursor
			final float t =
					(i - irfDataOffset + irfPlotOffset + (fp.isPickingIRF() ? 0 : -irfLength))
							* xInc;
			setData(dataLists[IRF_IDX], i, t, instr[i]);
			// display IRF intensity when picking
			if (fp.isPickingIRF()) {
				prefixSum[i + 1] = prefixSum[i] + instr[i];
			}
		}
		dataLists[IRF_IDX].subList(instr.length, dataLists[IRF_IDX].size()).clear();

		// again, remove doesn't work well with animations
		fitPlotChart.setAnimated(false);
		resPlotChart.setAnimated(false);

		// refreshes each series
		ObservableList<Data<Number, Number>> data;
		data = plotSeries[TRN_IDX].getData();
		data.clear();
		data.addAll(dataLists[TRN_IDX]);

		data = plotSeries[FIT_IDX].getData();
		data.clear();
		data.addAll(dataLists[FIT_IDX]);

		data = plotSeries[IRF_IDX].getData();
		data.clear();
		data.addAll(dataLists[IRF_IDX]);

		data = plotSeries[RES_IDX].getData();
		data.clear();
		data.addAll(dataLists[RES_IDX]);

		adjustPlottedPortion();

		fitPlotChart.setAnimated(true);
		resPlotChart.setAnimated(true);

		// make the plot fit the area
		((NumberAxis) fitPlotChart.getXAxis()).setUpperBound(xMax);
		((NumberAxis) resPlotChart.getXAxis()).setUpperBound(xMax);
	}

	/**
	 * Add a new data point to the chart.
	 * 
	 * @param list the series data
	 * @param idx  the index to insert into {@code list}
	 * @param x    the x value
	 * @param y    the y value
	 */
	private void setData(List<Data<Number, Number>> list, int idx, float x, float y) {
		if (idx >= list.size()) {
			list.add(new Data<>(x, y));
		} else {
			final Data<Number, Number> point = list.get(idx);
			point.setXValue(x);
			point.setYValue(y);
		}
	}
}
