package flimlib.flimj.ui;

import net.imagej.Dataset;
import net.imagej.ImageJ;

public class GUITestDrive {

	public static void main(String[] args) throws Exception {
		ImageJ ij = new ImageJ();
		ij.ui().showUI();
		Dataset trans = ij.scifio().datasetIO().open("test_files/sample1.sdt");
		ij.ui().show(trans);
		ij.command().run(FLIMJCommand.class, true);
	}
}
