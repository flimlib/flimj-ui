/*-
 * #%L
 * Fluorescence lifetime analysis in ImageJ.
 * %%
 * Copyright (C) 2019 - 2025 Board of Regents of the University of Wisconsin-Madison.
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.imagej.ops.OpService;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.FloatType;

import flimlib.flimj.FitParams;
import flimlib.flimj.FitResults;

/**
 * Support Plane Analysis.
 * <p>
 * Evaluates parameter confidence intervals and correlations by fixing one
 * parameter at a series of values above and below its best-fit value (and at the
 * original fitted value), refitting the decay curve with all other free
 * parameters allowed to vary, and recording the resulting minimum &chi;<sup>2</sup>.
 * </p>
 * <p>
 * Attributed to: <b>Lakowicz 2006 Section 4.10.3 p. 134</b>
 * </p>
 */
public class SupportPlaneAnalysis {

	public static final String FEATURE_NAME = "Support Plane Analysis";

	public static final String ATTRIBUTION = "Lakowicz 2006 Section 4.10.3 p. 134";

	/**
	 * Represents the outcome of refitting the decay curve with one parameter fixed.
	 */
	public static class PointResult {

		public final String paramName;
		public final int paramIndex;
		public final int step;
		public final double percentChange;
		public final float fixedValue;
		public final boolean isOriginalFit;
		public final float chisq;
		public final float chisqRatio;
		public final boolean converged;
		public final int retCode;
		public final float[] fittedParams;

		public PointResult(String paramName, int paramIndex, int step, double percentChange,
				float fixedValue, boolean isOriginalFit, float chisq, float chisqRatio,
				boolean converged, int retCode, float[] fittedParams) {
			this.paramName = paramName;
			this.paramIndex = paramIndex;
			this.step = step;
			this.percentChange = percentChange;
			this.fixedValue = fixedValue;
			this.isOriginalFit = isOriginalFit;
			this.chisq = chisq;
			this.chisqRatio = chisqRatio;
			this.converged = converged;
			this.retCode = retCode;
			this.fittedParams = fittedParams != null ? Arrays.copyOf(fittedParams, fittedParams.length) : new float[0];
		}
	}

	/**
	 * Encapsulates the entire results of a Support Plane Analysis.
	 */
	public static class AnalysisResult {

		public final String algorithm;
		public final int nComp;
		public final float origChisq;
		public final float[] origParams;
		public final String[] paramNames;
		public final List<PointResult> points;

		public AnalysisResult(String algorithm, int nComp, float origChisq, float[] origParams,
				String[] paramNames, List<PointResult> points) {
			this.algorithm = algorithm;
			this.nComp = nComp;
			this.origChisq = origChisq;
			this.origParams = origParams != null ? Arrays.copyOf(origParams, origParams.length) : new float[0];
			this.paramNames = paramNames != null ? Arrays.copyOf(paramNames, paramNames.length) : new String[0];
			this.points = points != null ? points : new ArrayList<>();
		}

		/**
		 * Formats the entire analysis results as a CSV string.
		 *
		 * @return the CSV string
		 */
		public String toCSV() {
			StringBuilder sb = new StringBuilder();
			sb.append("# Feature: ").append(FEATURE_NAME).append("\n");
			sb.append("# Attribution: ").append(ATTRIBUTION).append("\n");
			sb.append("# Algorithm: ").append(algorithm).append("\n");
			sb.append("# Number of Components: ").append(nComp).append("\n");
			sb.append(String.format(Locale.US, "# Original Minimum Reduced Chisq: %.6f\n", origChisq));
			sb.append("# Original Parameters: ");
			for (int i = 0; i < origParams.length; i++) {
				String pName = i < paramNames.length ? paramNames[i] : ("p" + i);
				sb.append(String.format(Locale.US, "%s=%.6f%s", pName, origParams[i],
						(i < origParams.length - 1 ? ", " : "\n")));
			}
			sb.append("#\n");

			// Header columns
			sb.append("Target_Parameter,Step,Percent_Change,Fixed_Value,Is_Original_Fit,Chisq,Chisq_Ratio,Converged,Ret_Code");
			for (int i = 0; i < paramNames.length; i++) {
				sb.append(",Fitted_").append(paramNames[i]);
			}
			sb.append("\n");

			// Data rows
			for (PointResult pt : points) {
				sb.append(String.format(Locale.US, "%s,%d,%.2f,%.6f,%b,%.6f,%.6f,%b,%d",
						pt.paramName, pt.step, pt.percentChange, pt.fixedValue, pt.isOriginalFit,
						pt.chisq, pt.chisqRatio, pt.converged, pt.retCode));
				for (int i = 0; i < paramNames.length; i++) {
					float val = (i < pt.fittedParams.length) ? pt.fittedParams[i] : Float.NaN;
					sb.append(String.format(Locale.US, ",%.6f", val));
				}
				sb.append("\n");
			}

			return sb.toString();
		}

		/**
		 * Saves the CSV content to a destination file.
		 *
		 * @param file destination file
		 * @throws IOException on I/O error
		 */
		public void saveToFile(File file) throws IOException {
			try (FileWriter writer = new FileWriter(file)) {
				writer.write(toCSV());
			}
		}
	}

	/**
	 * Executes Support Plane Analysis on the current decay curve fit.
	 *
	 * @param fp           the {@link FitProcessor} containing the dataset and fitted curve
	 * @param percentRange maximum percentage deviation to explore below and above (e.g. 20.0f)
	 * @param nSteps       number of steps to evaluate below and above (e.g. 5)
	 * @return the complete {@link AnalysisResult}
	 */
	public static AnalysisResult run(FitProcessor fp, float percentRange, int nSteps) {
		if (fp == null) {
			throw new IllegalArgumentException("FitProcessor cannot be null");
		}
		if (percentRange <= 0) {
			percentRange = 20.0f;
		}
		if (nSteps <= 0) {
			nSteps = 5;
		}

		// Ensure current fit results are available
		FitResults baseResults = fp.getResults();
		if (baseResults == null || baseResults.param == null) {
			fp.updateFit();
			baseResults = fp.getResults();
		}
		if (baseResults == null || baseResults.param == null) {
			throw new IllegalStateException("Unable to obtain initial fit results for Support Plane Analysis.");
		}

		final float origChisq = baseResults.chisq;
		final float[] origParams = Arrays.copyOf(baseResults.param, baseResults.param.length);
		final FitParams<FloatType> baseParams = fp.getParams();
		final OpService ops = fp.getService(OpService.class);

		String algo = fp.getAlgo();
		if (algo == null || "Global".equals(algo)) {
			// For a single decay curve, Global fit uses LMA
			algo = "LMA";
		}

		final int nParam = origParams.length;
		final String[] paramNames = getParameterNames(baseParams.nComp, nParam);

		// Calculate maximum intensity in transient for zero-value scaling fallback
		float transMax = 0f;
		if (baseParams.trans != null) {
			for (float val : baseParams.trans) {
				if (val > transMax) {
					transMax = val;
				}
			}
		}
		if (transMax <= 0f) {
			transMax = 100f;
		}

		List<PointResult> points = new ArrayList<>();

		// For one parameter at a time...
		for (int k = 0; k < nParam; k++) {
			final String targetParamName = paramNames[k];
			final float origVal = origParams[k];

			// Repeat for different values below and above the original fitted value,
			// explicitly including the original fitted value (step = 0)
			for (int s = -nSteps; s <= nSteps; s++) {
				final boolean isOriginalFit = (s == 0);
				final double percentChange = (s == 0) ? 0.0 : ((double) s * ((double) percentRange / (double) nSteps));
				float fixedVal;

				if (isOriginalFit) {
					fixedVal = origVal;
				} else {
					double frac = percentChange / 100.0;
					if (Math.abs(origVal) > 1e-6f) {
						fixedVal = (float) (origVal * (1.0 + frac));
					} else {
						float delta = Math.max(1.0f, transMax * 0.01f);
						fixedVal = (float) (s * delta * (percentRange / (100.0 * nSteps)));
					}
				}

				// Physical boundary checks (lifetimes and amplitudes should remain positive)
				if (k > 0) {
					boolean isTau = ((k - 1) % 2 == 1);
					boolean isA = ((k - 1) % 2 == 0);
					if (isTau && fixedVal <= 1e-4f) {
						fixedVal = 1e-4f;
					} else if (isA && fixedVal < 0f) {
						fixedVal = 0f;
					}
				}

				// Prepare isolated parameters for refitting
				FitParams<FloatType> runParams = baseParams.copy();
				runParams.trans = Arrays.copyOf(baseParams.trans, baseParams.trans.length);
				runParams.transMap = ArrayImgs.floats(runParams.trans,
						FitProcessor.swapInLtAxis(new long[] {1, 1, runParams.trans.length}, runParams.ltAxis));
				runParams.param = Arrays.copyOf(origParams, origParams.length);
				runParams.paramFree = new boolean[nParam];
				for (int j = 0; j < nParam; j++) {
					if (j == k) {
						runParams.paramFree[j] = false;
					} else if (baseParams.paramFree != null && j < baseParams.paramFree.length) {
						runParams.paramFree[j] = baseParams.paramFree[j];
					} else {
						runParams.paramFree[j] = true;
					}
					if (j != k) {
						runParams.param[j] = origParams[j];
					}
				}

				// Hold parameter k fixed at the target value
				runParams.param[k] = fixedVal;
				runParams.paramFree[k] = false;
				runParams.paramMap = ArrayImgs.floats(runParams.param,
						FitProcessor.swapInLtAxis(new long[] {1, 1, runParams.param.length}, runParams.ltAxis));

				float refitChisq;
				float chisqRatio;
				boolean converged;
				int retCode;
				float[] refitParams;

				if (isOriginalFit) {
					refitChisq = origChisq;
					chisqRatio = 1.0f;
					converged = (baseResults.retCode >= 0);
					retCode = baseResults.retCode;
					refitParams = origParams;
				} else {
					// Refit the decay curve with that parameter held fixed at the new value
					FitResults refitResults;
					try {
						refitResults = (FitResults) ops.run("flim.fit" + algo, runParams);
					} catch (Exception e) {
						// Fallback on error during fit
						refitResults = new FitResults();
						refitResults.retCode = -1;
						refitResults.chisq = Float.NaN;
						refitResults.param = Arrays.copyOf(runParams.param, runParams.param.length);
					}

					refitChisq = refitResults.chisq;
					chisqRatio = (origChisq > 0f && Float.isFinite(refitChisq)) ? (refitChisq / origChisq) : 1.0f;
					converged = (refitResults.retCode >= 0);
					retCode = refitResults.retCode;
					refitParams = refitResults.param != null ? refitResults.param : runParams.param;
				}

				points.add(new PointResult(targetParamName, k, s, percentChange, fixedVal, isOriginalFit,
						refitChisq, chisqRatio, converged, retCode, refitParams));
			}
		}

		return new AnalysisResult(algo, baseParams.nComp, origChisq, origParams, paramNames, points);
	}

	/**
	 * Generates human-readable parameter names according to FLIMJ conventions.
	 *
	 * @param nComp  number of exponential components
	 * @param nParam total number of parameters
	 * @return array of parameter names
	 */
	public static String[] getParameterNames(int nComp, int nParam) {
		String[] names = new String[nParam];
		if (nParam > 0) {
			names[0] = "z";
		}
		for (int i = 0; i < nComp; i++) {
			int aIdx = 2 * i + 1;
			int tauIdx = 2 * i + 2;
			String suffix = (nComp > 1) ? ("_" + (i + 1)) : "_1";
			if (aIdx < nParam) {
				names[aIdx] = "A" + suffix;
			}
			if (tauIdx < nParam) {
				names[tauIdx] = "tau" + suffix;
			}
		}
		// Fill any remaining with generic names if unexpected length
		for (int i = 0; i < nParam; i++) {
			if (names[i] == null) {
				names[i] = "p" + i;
			}
		}
		return names;
	}
}
