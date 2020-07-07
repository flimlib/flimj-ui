package flimlib.flimj.ui;

import java.io.File;

import net.imagej.Dataset;
import net.imagej.ImageJ;

import org.scijava.widget.FileWidget;

public class GUITestDrive {

	public static void main(String[] args) throws Exception {
		ImageJ ij = new ImageJ();
		ij.ui().showUI();

		final File file = ij.ui().chooseFile(null, FileWidget.OPEN_STYLE);
		if (file == null) return;
		final Dataset d = ij.scifio().datasetIO().open(file.getAbsolutePath());
		ij.ui().show(d);

		ij.command().run(FLIMJCommand.class, true);
	}
}
