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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import net.imagej.ImageJ;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.FloatType;

import flimlib.NoiseType;
import flimlib.flimj.FitParams;

/**
 * Automated tests for {@link SupportPlaneAnalysis}.
 */
public class SupportPlaneAnalysisTest {

	private static ImageJ ij;

	@BeforeClass
	public static void setUp() {
		ij = new ImageJ();
	}

	@AfterClass
	public static void tearDown() {
		if (ij != null) {
			ij.context().dispose();
		}
	}

	@Test
	public void testSupportPlaneAnalysis() throws Exception {
		// 1. Synthesize mono-exponential decay curve
		final int nBins = 64;
		final float xInc = 0.2f;
		final float zTrue = 2.0f;
		final float aTrue = 1000.0f;
		final float tauTrue = 2.5f;

		float[] decayData = new float[nBins];
		for (int t = 0; t < nBins; t++) {
			float time = t * xInc;
			decayData[t] = zTrue + (float) (aTrue * Math.exp(-time / tauTrue));
		}

		// 2. Set up FitParams matching FLIMJ requirements (3D transMap with ltAxis=2)
		FitParams<FloatType> params = new FitParams<>();
		params.ltAxis = 2;
		params.xInc = xInc;
		params.nComp = 1;
		params.noise = NoiseType.NOISE_GAUSSIAN_FIT;
		params.iThresh = 0;
		params.transMap = ArrayImgs.floats(decayData, 1, 1, nBins);

		FitProcessor fp = new FitProcessor(ij.context(), params);
		fp.setAlgo(FitProcessor.FitType.LMA);

		// Perform initial fit
		fp.updateFit();

		assertNotNull("Fit results must not be null", fp.getResults());
		assertNotNull("Fitted parameters must not be null", fp.getResults().param);
		assertTrue("Fitted parameters length must be 3 for mono-exponential", fp.getResults().param.length == 3);

		float origChisq = fp.getResults().chisq;
		float origZ = fp.getResults().param[0];
		float origA = fp.getResults().param[1];
		float origTau = fp.getResults().param[2];

		// 3. Execute Support Plane Analysis: range=20%, steps=5
		final float percentRange = 20.0f;
		final int nSteps = 5;
		SupportPlaneAnalysis.AnalysisResult result = SupportPlaneAnalysis.run(fp, percentRange, nSteps);

		assertNotNull("AnalysisResult must not be null", result);
		assertEquals("Algorithm should be LMA", "LMA", result.algorithm);
		assertEquals("nComp should be 1", 1, result.nComp);
		assertEquals(3, result.origParams.length);
		assertEquals("z", result.paramNames[0]);
		assertEquals("A_1", result.paramNames[1]);
		assertEquals("tau_1", result.paramNames[2]);

		// For 3 parameters and 5 steps below and above:
		// 3 parameters * (2 * 5 + 1 steps) = 33 points
		final int expectedPointsPerParam = 2 * nSteps + 1; // 11
		final int expectedTotalPoints = 3 * expectedPointsPerParam; // 33
		assertEquals(expectedTotalPoints, result.points.size());

		// 4. Verify that each target parameter has the original fitted value included
		for (int pIdx = 0; pIdx < 3; pIdx++) {
			final int start = pIdx * expectedPointsPerParam;
			final int end = start + expectedPointsPerParam;
			List<SupportPlaneAnalysis.PointResult> paramPoints = result.points.subList(start, end);

			boolean foundOriginal = false;
			for (SupportPlaneAnalysis.PointResult pt : paramPoints) {
				assertEquals(pIdx, pt.paramIndex);
				if (pt.step == 0) {
					foundOriginal = true;
					assertTrue("Step 0 must be marked as isOriginalFit", pt.isOriginalFit);
					assertEquals(0.0, pt.percentChange, 1e-5);
					assertEquals(result.origParams[pIdx], pt.fixedValue, 1e-4);
					// Chisq ratio at step 0 should be approximately 1.0
					assertEquals(1.0f, pt.chisqRatio, 0.05f);
				} else if (pt.step < 0) {
					assertFalse("Step < 0 must not be isOriginalFit", pt.isOriginalFit);
					assertTrue("Step < 0 must have negative percentChange", pt.percentChange < 0);
					assertTrue("Step < 0 must have fixedValue < origParam", pt.fixedValue <= result.origParams[pIdx]);
				} else {
					assertFalse("Step > 0 must not be isOriginalFit", pt.isOriginalFit);
					assertTrue("Step > 0 must have positive percentChange", pt.percentChange > 0);
					assertTrue("Step > 0 must have fixedValue > origParam", pt.fixedValue >= result.origParams[pIdx]);
				}
			}
			assertTrue("Original fitted value must be included in set of fixed values for param " + pIdx, foundOriginal);
		}

		// 5. Test CSV generation and content
		String csv = result.toCSV();
		assertNotNull(csv);
		assertTrue("CSV must contain Feature name", csv.contains("Feature: " + SupportPlaneAnalysis.FEATURE_NAME));
		assertTrue("CSV must contain exact attribution", csv.contains("Attribution: " + SupportPlaneAnalysis.ATTRIBUTION));
		assertTrue("CSV must contain Is_Original_Fit column", csv.contains("Is_Original_Fit"));
		assertTrue("CSV must contain Target_Parameter header", csv.contains("Target_Parameter,Step,Percent_Change,Fixed_Value"));
		assertTrue("CSV must contain parameter columns", csv.contains("Fitted_z,Fitted_A_1,Fitted_tau_1"));

		// 6. Test CSV file saving
		File tempFile = File.createTempFile("support_plane_test", ".csv");
		try {
			result.saveToFile(tempFile);
			assertTrue("Saved CSV file must exist", tempFile.exists());
			assertTrue("Saved CSV file must not be empty", tempFile.length() > 0);

			String fileContent = new String(Files.readAllBytes(tempFile.toPath()));
			assertEquals("File content should match toCSV() output", csv, fileContent);
		} finally {
			tempFile.delete();
		}
	}
}
