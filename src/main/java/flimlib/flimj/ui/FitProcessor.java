package flimlib.flimj.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.imagej.ops.OpService;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.outofbounds.OutOfBoundsPeriodicFactory;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import org.scijava.Context;
import org.scijava.service.Service;

import flimlib.flimj.FitParams;
import flimlib.flimj.FitResults;
import flimlib.flimj.FlimOps;
import flimlib.flimj.ParamEstimator;
import flimlib.flimj.ui.controller.AbstractCtrl;

/**
 * ProcessingService
 */
public class FitProcessor {

	/** The collection of all fitting algorithms */
	public static enum FitType {
		LMA, Global, Bayes
		/** Phasor */
	}

	private final Context ctx;

	private final OpService ops;

	private final FitParams<FloatType> DEFAULT_IRF_INFO;

	private FitParams<FloatType> params, irfInfoParams;

	private FitResults results;

	private boolean isPickingIRF;

	private RandomAccessibleInterval<FloatType> origTrans, binnedTrans, origIntensity;

	private Img<FloatType> dispParams, irfIntensity;

	private String fitType;

	private List<String> contextualPreviewOptions, persistentPreviewOptions;

	private static final BiFunction<Float, float[], Float> MULTI_EXP;

	private BiFunction<Float, float[], Float> fitFunc;

	private int nParam, previewX, previewY, binRadius;

	private int axisOrder[];

	private float[] globalTrans;

	private AbstractCtrl[] controllers;

	private final ExecutorService executor;

	static {
		// z + sum[a_i * exp(-t / tau_i)]
		MULTI_EXP = (t, param) -> {
			float y = param[0];
			for (int i = 1; i < param.length - 1; i += 2) {
				y += param[i] * Math.exp(-t / param[i + 1]);
			}
			// Inf/NaN blows up the plot thread
			return Float.isFinite(y) ? y : 0f;
		};
	}

	public FitProcessor(final Context context, final FitParams<FloatType> params) {
		this.ctx = context;
		this.ops = getService(OpService.class);
		this.params = params;
		this.DEFAULT_IRF_INFO = new FitParams<>();
		this.irfInfoParams = DEFAULT_IRF_INFO;
		this.results = new FitResults();
		this.executor = Executors.newFixedThreadPool(1);
		// trigger setBinning() at start
		this.binRadius = -1;
		init();

		setBinning(0);

		setPreviewPos(0, 0, false);
	}

	private void init() {
		long[] perm = swapOutLtAxis(new long[] {0, 1, 2}, params.ltAxis);
		axisOrder = new int[] {(int) perm[0], (int) perm[1], (int) perm[2]};

		binnedTrans = origTrans = params.transMap;

		// allocate buffers
		params.trans = new float[(int) params.transMap.dimension(params.ltAxis)];
		params.transMap = ArrayImgs.floats(params.trans,
				swapInLtAxis(new long[] {1, 1, params.trans.length}, params.ltAxis));
		params.paramFree = new boolean[0];
		params.param = new float[0];
		params.paramMap =
				ArrayImgs.floats(params.param, swapInLtAxis(new long[] {1, 1, 0}, params.ltAxis));

		contextualPreviewOptions = new ArrayList<>();
		persistentPreviewOptions = new ArrayList<>();
		// this option is always present
		persistentPreviewOptions.add("Intensity");

		// calculate intensity and estimate start-end
		RandomAccessibleInterval<FloatType> tmpTransMap = params.transMap;
		params.transMap = origTrans;
		ParamEstimator<FloatType> estimator = new ParamEstimator<>(params);
		origIntensity = estimator.getIntensityMap();
		estimator.estimateStartEnd();
		params.transMap = tmpTransMap;
	}

	public void setControllers(AbstractCtrl... controllers) {
		this.controllers = controllers;
	}

	/**
	 * Refreshes all controllers. Must be called from UI thread.
	 */
	public void refreshControllers() {
		for (AbstractCtrl controller : controllers) {
			controller.requestRefresh();
		}
	}

	/**
	 * @return the params
	 */
	public FitParams<FloatType> getParams() {
		return params;
	}

	/**
	 * @return the IRF info capsule
	 */
	public FitParams<FloatType> getIRFInfo() {
		return irfInfoParams;
	}

	/**
	 * @return the results
	 */
	public FitResults getResults() {
		return results;
	}

	/**
	 * @return the original trans data
	 */
	public RandomAccessibleInterval<FloatType> getOrigTrans() {
		return origTrans;
	}

	/**
	 * @param <S> Service type
	 * @param c   the service class
	 * @return the SciJava service
	 */
	public <S extends Service> S getService(final Class<S> c) {
		return ctx.service(c);
	}

	public void updateFit() {
		updateFit(true);
	}

	public void updateFit(boolean preview) {
		// global estimate of taus
		float[] globalParams = null;
		if ("Global".equals(fitType) && preview) {
			for (int i = 0; i < params.param.length; i++) {
				// trigger rld for free parameters and taus
				if (params.paramFree[i] || (i - 1) % 2 == 1) {
					params.paramFree[i] = true;
					params.param[i] = Float.POSITIVE_INFINITY;
				}
			}
			float[] pixTrans = Arrays.copyOf(params.trans, params.trans.length);
			for (int i = 0; i < params.trans.length; i++) {
				params.trans[i] = globalTrans[i];
			}
			globalParams = ((FitResults) ops.run("flim.fitLMA", params)).param;
			for (int i = 0; i < params.trans.length; i++) {
				params.trans[i] = pixTrans[i];
			}
		}

		// wipe out initial values for free params and fix taus in global mode
		for (int i = 0; i < params.param.length; i++) {
			if ("Global".equals(fitType) && (i - 1) % 2 == 1 && preview) {
				params.paramFree[i] = false;
				params.param[i] = globalParams[i];
			} else if (params.paramFree[i])
				params.param[i] = Float.POSITIVE_INFINITY;
		}

		FitResults fr;
		if ("Global".equals(fitType) && preview)
			fr = (FitResults) ops.run("flim.fitLMA", params);
		else
			fr = (FitResults) ops.run("flim.fit" + fitType, params);

		fr.intensityMap = this.results.intensityMap;
		this.results = fr;
	}

	public void setBinning(int size) {
		boolean allMask = false;
		if (size == -1) {
			// FIXME: divide by 2 after https://github.com/imagej/imagej-ops/issues/628 is fixed
			size = (int) Math.max(origIntensity.dimension(axisOrder[0]),
					origIntensity.dimension(axisOrder[1]));
			allMask = true;
		}
		if (size != binRadius) {
			// recalculate threshold to equalize per-pixel threshold
			params.iThresh = Math.round((double) params.iThresh //
					/ ((2 * binRadius + 1) * (2 * binRadius + 1))
					* ((2 * size + 1) * (2 * size + 1)));
			// invalidate cached
			binnedTrans = null;
			binRadius = size;
			if (size > 0) {
				Img<DoubleType> kernel = FlimOps.makeSquareKernel(size * 2 + 1);
				results.intensityMap = (Img<FloatType>) (allMask
						? ops.filter().convolve(origIntensity, kernel,
								new OutOfBoundsPeriodicFactory<>())
						: ops.filter().convolve(origIntensity, kernel));
			} else
				results.intensityMap = (Img<FloatType>) origIntensity;
		}
		// load trans after binning
		setPreviewPos(previewX, previewY, false);
	}

	public void setAlgo(FitType algo) {
		switch (algo) {
			case LMA:
				fitType = "LMA";
				fitFunc = MULTI_EXP;
				nParam = 2 * params.nComp + 1;
				break;

			case Global:
				fitType = "Global";
				fitFunc = MULTI_EXP;
				nParam = 2 * params.nComp + 1;

				// one-time loading of binned trans
				if (globalTrans == null) {
					globalTrans = new float[params.trans.length];
					fillTrans(origTrans, globalTrans, 0, 0, axisOrder,
							(int) origTrans.dimension(0));
				}
				break;

			case Bayes:
				fitType = "Bayes";
				fitFunc = MULTI_EXP;
				nParam = 2 * params.nComp + 1;
				break;
		}
	}

	public void setIsPickingIRF(boolean isPickingIRF) {
		this.isPickingIRF = isPickingIRF;
	}

	public boolean isPickingIRF() {
		return isPickingIRF;
	}

	public void setIRF(FitParams<FloatType> newIRF) {
		if (newIRF != null) {
			irfInfoParams = newIRF;

			// allocate room for copying IRF into
			if (irfInfoParams.trans == null)
				irfInfoParams.trans =
						new float[(int) irfInfoParams.transMap.dimension(irfInfoParams.ltAxis)];

			ParamEstimator<FloatType> est = new ParamEstimator<>(irfInfoParams);
			est.estimateStartEnd();
			irfIntensity = est.getIntensityMap();

			if (!persistentPreviewOptions.contains("IRF Intensity"))
				persistentPreviewOptions.add("IRF Intensity");

			updateIRFRange();
		} else {
			irfInfoParams = this.DEFAULT_IRF_INFO;
			params.instr = null;
			persistentPreviewOptions.remove("IRF Intensity");
			// if is currently in picking mode, exit immediately
			isPickingIRF = false;
		}
	}

	public void updateIRFRange() {
		// test DEFAULT or unadjusted irf
		if (irfInfoParams.fitStart == -1 || irfInfoParams.fitEnd == -1)
			return;
		params.instr = getNormalizedIRF(irfInfoParams);
	}

	private float[] getNormalizedIRF(FitParams<FloatType> irf) {
		float[] normalizedIRF = Arrays.copyOfRange(irf.trans, irf.fitStart, irf.fitEnd);

		float sum = 0;
		for (int i = 0; i < normalizedIRF.length; i++)
			sum += normalizedIRF[i];
		// if there is no photons present, use trivial IRF
		if (sum == 0)
			normalizedIRF = new float[] {1};
		else {
			for (int i = 0; i < normalizedIRF.length; i++)
				normalizedIRF[i] /= sum;
		}

		return normalizedIRF;
	}

	public BiFunction<Float, float[], Float> getFitFunc() {
		return fitFunc != null ? fitFunc : (t, param) -> 0f;
	}

	public int getNParam() {
		return nParam;
	}

	public void setPreviewPos(final int x, final int y, final boolean irf) {
		if (irf) {
			fillTrans(irfInfoParams.transMap, irfInfoParams.trans, x, y, axisOrder, 0);
			updateIRFRange();
		} else {
			previewX = x;
			previewY = y;
			fillTrans(origTrans, params.trans, x, y, axisOrder, binRadius);
		}
	}

	private static void fillTrans(RandomAccessibleInterval<FloatType> transMap, float[] transArr,
			int x, int y, final int[] axisOrder, int binRadius) {

		final RandomAccess<FloatType> ra = Views.extendZero(transMap).randomAccess();
		final int[] coord = new int[3];
		final int X = axisOrder[0];
		final int Y = axisOrder[1];
		coord[X] = x - binRadius;
		coord[Y] = y - binRadius;
		ra.setPosition(coord);
		for (int t = 0; t < transArr.length; t++)
			transArr[t] = 0;
		for (int i = 0; i < 2 * binRadius + 1; i++, ra.fwd(X)) {
			// reset y
			ra.setPosition(coord[Y], Y);
			for (int j = 0; j < 2 * binRadius + 1; j++, ra.fwd(Y)) {
				// reset t
				ra.setPosition(0, axisOrder[2]);
				for (int t = 0; t < transArr.length; t++, ra.fwd(axisOrder[2]))
					transArr[t] += ra.get().getRealFloat();

			}
		}
	}

	public void fitDataset() {
		// use cached trans if available
		if (binnedTrans == null) {
			binnedTrans = binRadius > 0
					? ops.filter().convolve(origTrans, FlimOps.makeSquareKernel(binRadius * 2 + 1))
					: origTrans;

			// convolve may spit out small negative values that causes problem in e.g. log() in
			// GCI_marquardt_compute_fn()
			for (FloatType f : Views.iterable(binnedTrans))
				f.set(Math.max(f.get(), 0));
		}
		// temporarily save trans and param maps for preview
		RandomAccessibleInterval<FloatType> previewTransMap, previewParamMap;
		previewTransMap = params.transMap;
		params.transMap = binnedTrans;
		previewParamMap = params.paramMap;
		// tirgger RLD for free parameters and global taus
		params.paramMap = null;
		for (int i = 0; i < params.param.length; i++) {
			if (params.paramFree[i] || ("Global".equals(fitType) && (i - 1) % 2 == 1)) {
				params.paramFree[i] = true;
				params.param[i] = Float.POSITIVE_INFINITY;
			}
		}

		updateFit(false);

		params.paramMap = previewParamMap;
		params.transMap = previewTransMap;

		dispParams = results.paramMap;
	}

	/**
	 * @return the previewOptions
	 */
	public List<String> getPreviewOptions() {
		return Stream.of(persistentPreviewOptions, contextualPreviewOptions)
				.flatMap(Collection::stream).collect(Collectors.toList());
	}

	/**
	 * @param options the previewOptions to set
	 */
	public void setPreviewOptions(List<String> options) {
		// remove non-persistent options
		contextualPreviewOptions = options;
	}

	@SuppressWarnings("unchecked")
	public RandomAccessibleInterval<FloatType> getPreviewImg(String option) {
		// immediately available after param population
		if (option.equals("Intensity"))
			return Views.hyperSlice(results.intensityMap, params.ltAxis, 0);
		else if (option.equals("IRF Intensity"))
			return Views.hyperSlice(irfIntensity, params.ltAxis, 0);

		int optionIdx = -1;
		switch (fitType) {
			case "LMA":
			case "Global":
			case "Bayes":
				FitResults rslt = results.copy();
				rslt.paramMap = dispParams;
				if (option.contains("%")) {
					switch (option) {
						case "A₁ %": optionIdx = 0; break;
						case "A₂ %": optionIdx = 1; break;
						case "A₃ %": optionIdx = 2; break;
						case "Aᵢ %": optionIdx = 3; break;
					}
					return (Img<FloatType>) ops.run("flim.calcAPercent", rslt, optionIdx);
				} else if (option.equals("τₘ")) {
					Img<FloatType> tauM = (Img<FloatType>) ops.run("flim.calcTauMean", rslt);
					return tauM;
				} else {
					switch (option) {
						case "z": optionIdx = 0; break;
						case "A":
						case "A₁": optionIdx = 1; break;
						case "τ":
						case "τ₁": optionIdx = 2; break;
						case "A₂": optionIdx = 3; break;
						case "τ₂": optionIdx = 4; break;
						case "A₃": optionIdx = 5; break;
						case "τ₃": optionIdx = 6; break;
						case "Aᵢ": optionIdx = 7; break;
						case "τᵢ": optionIdx = 8; break;
					}
					// get the ith param beyond persistent options
					return Views.hyperSlice(dispParams, params.ltAxis, optionIdx);
				}

			default:
				break;
		}
		return null;
	}

	/**
	 * Permute the coordinates from ltDimension-last to ltDimension-at-ltAxis.
	 * 
	 * @param coordinates  the coordinates in ltDimension-last order
	 * @param lifetimeAxis the index of the lifetime axis
	 * @return the coordinates in ltDimension-at-ltAxis order
	 */
	public static long[] swapInLtAxis(long[] coordinates, int lifetimeAxis) {
		for (int i = coordinates.length - 1; i > lifetimeAxis; i--) {
			long tmp = coordinates[i];
			coordinates[i] = coordinates[i - 1];
			coordinates[i - 1] = tmp;
		}
		return coordinates;
	}

	/**
	 * Permute the coordinates from ltDimension-at-ltAxis to ltDimension-last.
	 * 
	 * @param coordinates  the coordinates in ltDimension-at-ltAxis order
	 * @param lifetimeAxis the index of the lifetime axis
	 * @return the coordinates in ltDimension-last order
	 */
	public static long[] swapOutLtAxis(long[] coordinates, int lifetimeAxis) {
		for (int i = lifetimeAxis; i < coordinates.length - 1; i++) {
			long tmp = coordinates[i];
			coordinates[i] = coordinates[i + 1];
			coordinates[i + 1] = tmp;
		}
		return coordinates;
	}

	public void submitRunnable(Runnable runnable) {
		executor.submit(runnable);
	}

	public void destroy() {
		for (AbstractCtrl controller : controllers) {
			controller.destroy();
		}
		executor.shutdownNow();
	}
}
