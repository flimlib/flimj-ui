package flimlib.flimj.ui;

import java.util.concurrent.Future;
import org.scijava.cache.CacheService;
import org.scijava.script.ScriptModule;
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
