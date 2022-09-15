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

import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.regex.Pattern;
import org.scijava.util.ColorRGB;
import net.imglib2.display.ColorTable8;

/**
 * Utils
 */
public class Utils {
/** The LUT for colorizing image (same as the one in TRI2) */
	public static final ColorTable8 LIFETIME_LUT = makeLifetimeLUT();

	private static final DecimalFormat sciDf = new DecimalFormat("0.#####E0");
	private static final DecimalFormat percentDf = new DecimalFormat("0.##%");
	private static final DecimalFormat normalDf = new DecimalFormat("0.#####");

	private static final Pattern NUMBER_PATTERN =
			Pattern.compile("[-+]?(([0-9]*.?[0-9]+(([eE][-+]?[0-9]+)|%)?)|∞)");

	/**
	 * Determines if the string matches the number format.
	 * 
	 * @param str the input string
	 * @return {@code true} if the string matches the number format
	 */
	public static boolean matchesNumber(String str) {
		return NUMBER_PATTERN.matcher(str).matches();
	}

	/**
	 * Nicely format the input value to integer/float with at most 5 decimal places or scientific
	 * notion with at most 5 decimal places if the number exeeds 10000
	 * 
	 * @param val the value to format
	 * @return the string representation of the number
	 */
	public static String prettyFmt(Number val) {
		double abs = Math.abs(val.doubleValue());
		if (Double.isNaN(abs))
			return "-";
		DecimalFormat df = abs > 1e4 || (abs != 0 && abs < 1e-4) ? sciDf : normalDf;
		return df.format(val);
	}

	/**
	 * Formats the value with the default scientific format.
	 * 
	 * @param val the value to format
	 * @return the formated value
	 */
	public static String percentFmt(Number val) {
		return percentDf.format(val.doubleValue());
	}

	/**
	 * Parses the decimal string.
	 * 
	 * @param str the string to parse
	 * @return the parsed number
	 */
	public static double parseDec(String str) {
		try {
			return sciDf.parse(str.toUpperCase()).doubleValue();
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Generates the lifetime LUT.
	 * 
	 * @return the LUT
	 */
	private static ColorTable8 makeLifetimeLUT() {
		final byte[] r = new byte[256], g = new byte[256], b = new byte[256];
		for (int i = 0; i < 256; i++) {
			final ColorRGB c = ColorRGB.fromHSVColor((i / 255d * 200d + 20) / 360d, 1d, 1d);
			r[i] = (byte) c.getRed();
			g[i] = (byte) c.getGreen();
			b[i] = (byte) c.getBlue();
		}
		return new ColorTable8(r, g, b);
	}
}
