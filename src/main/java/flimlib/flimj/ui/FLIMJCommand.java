package flimlib.flimj.ui;

import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

import net.imagej.display.DatasetView;
import net.imglib2.type.numeric.real.FloatType;

import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.log.Logger;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import flimlib.flimj.FitParams;
import flimlib.flimj.ui.controller.AbstractCtrl;
import flimlib.flimj.ui.controller.MainCtrl;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;

@Plugin(type = Command.class, menuPath = "Analyze>Lifetime>FLIMJ")
public class FLIMJCommand implements Command {

	@Parameter
	private DatasetView datasetView;

	/** The title of the application */
	private static final String TITLE = "FLIMJ";

	/** The path to the logo image */
	private static final String ICON_PATH = "img/logo.png";

	@Override
	public void run() {
		JFrame frame = new JFrame(TITLE);
		JFXPanel fxPanel = new JFXPanel();
		frame.add(fxPanel);
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		// this setting keeps JFX services alive so that we can launch the app again
		Platform.setImplicitExit(false);
		Platform.runLater(() -> {
			try {
				initFX(frame, fxPanel);
				frame.setVisible(true);
			} catch (UIException e) {
				log().error(e);
			} catch (Exception e) {
				log().error(e);
				throw new RuntimeException(e);
			}
		});
	}

	private Logger log() {
		return datasetView.context().service(LogService.class);
	}

	/**
	 * Initializes the GUI frame.
	 * 
	 * @param frame   the application frame
	 * @param fxPanel the embeded channel
	 * @throws IOException if the fxml is not found
	 * @see <a href=
	 *      "https://docs.oracle.com/javase/8/javafx/interoperability-tutorial/swing-fx-interoperability.htm">oracle
	 *      doc</a>
	 */
	private void initFX(JFrame frame, JFXPanel fxPanel) throws IOException {
		ClassLoader cl = getClass().getClassLoader();

		// set title and icon
		frame.setTitle(TITLE);
		frame.setIconImages(getIcons(cl.getResource(ICON_PATH)));

		// load scene
		FXMLLoader loader = AbstractCtrl.getFXMLLoader("plugin-layout");
		Scene scene = loader.<Scene>load();

		// init fitting worker
		FitParams<FloatType> params = new FitParams<>();
		FitParamsPrompter.populate(params, datasetView.getData(), datasetView);
		FitProcessor fp = new FitProcessor(datasetView.context(), params);

		// init controllers
		MainCtrl mainCtrl = loader.<MainCtrl>getController();
		mainCtrl.setFitProcessor(fp);

		// in case any operation crashes the main thread
		fp.submitRunnable(() -> {
			fp.refreshControllers();
			fp.updateFit();
		});

		fxPanel.setScene(scene);
		// not sure why need extra margins to correctly dispay the whole scene
		frame.setSize((int) scene.getWidth() + 20, (int) scene.getHeight() + 50);

		// release resources when done
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				// TODO there is still a lot of memory leaks caused by listeners,
				// remove them from each controller
				fp.destroy();
				datasetView = null;
			}
		});
	}

	/**
	 * Loads the icon image specified by url in different resolutions.
	 * 
	 * @param url the URL of the icon image
	 * @return the image in 16x16, 20x20, 32x32 and 40x40
	 */
	private List<Image> getIcons(URL url) {
		Image img = new ImageIcon(url).getImage();
		List<Image> imgs = new ArrayList<>();
		for (int size : new int[] {16, 20, 32, 40}) {
			imgs.add(img.getScaledInstance(size, size, Image.SCALE_SMOOTH));
		}
		return imgs;
	}
}
