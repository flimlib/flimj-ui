package flimlib.flimj.ui;

import java.awt.EventQueue;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

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

	/** The title of the application */
	private static final String TITLE = "FLIMJ";

	/** The path to the logo image */
	private static final String ICON_PATH = "img/logo.png";

	@Parameter
	private DatasetView datasetView;

	private FitProcessor fp;

	@Override
	public void run() {
		final JFXPanel fxPanel = new JFXPanel();

		// this setting keeps JFX services alive so that we can launch the app again
		Platform.setImplicitExit(false);
		final boolean[] initSuccessful = {false};
		runAndWait(() -> {
			try {
				initSuccessful[0] = initFX(fxPanel);
			} catch (UIException e) {
				log().error(e);
			} catch (Exception e) {
				log().error(e);
				throw new RuntimeException(e);
			}
		});
		if (initSuccessful[0])
			EventQueue.invokeLater(() -> {
				initSwing(fxPanel);
			});
		
		if (!initSuccessful[0])
			log().warn("FLIMJ: UI init failed or aborted by user. Exiting.");
	}

	/**
	 * Runs the specified {@link Runnable} on the
	 * JavaFX application thread and waits for completion.
	 * <p>
	 * Credit:
	 * <a href="https://news.kynosarges.org/2014/05/01/simulating-platform-runandwait/">Christoph Nahr</a>
	 * </p>
	 *
	 * @param action the {@link Runnable} to run
	 * @throws NullPointerException if {@code action} is {@code null}
	 */
	private void runAndWait(final Runnable action) {
		// run synchronously on JavaFX thread
		if (Platform.isFxApplicationThread()) {
			action.run();
			return;
		}

		// queue on JavaFX thread and wait for completion
		final CountDownLatch doneLatch = new CountDownLatch(1);
		Platform.runLater(() -> {
			try {
				action.run();
			}
			finally {
				doneLatch.countDown();
			}
		});

		try {
			doneLatch.await();
		}
		catch (final InterruptedException e) {
			log().error(e);
		}
	}

	private Logger log() {
		return datasetView.context().service(LogService.class);
	}

	/**
	 * Initializes the GUI frame.
	 * 
	 * @param fxPanel the embeded channel
	 * @throws IOException if the fxml is not found
	 * @return <code>true</code> - if the operation is successful
	 * @see <a href=
	 *      "https://docs.oracle.com/javase/8/javafx/interoperability-tutorial/swing-fx-interoperability.htm">oracle
	 *      doc</a>
	 */
	private boolean initFX(final JFXPanel fxPanel) throws IOException {
		// load scene
		final FXMLLoader loader = AbstractCtrl.getFXMLLoader("plugin-layout");
		final Scene scene = loader.<Scene>load();
		fxPanel.setScene(scene);

		// init fitting worker
		final FitParams<FloatType> params = new FitParams<>();
		if (!FitParamsPrompter.populate(params, datasetView.getData(), datasetView)) return false;
		fp = new FitProcessor(datasetView.context(), params);

		// init controllers
		final MainCtrl mainCtrl = loader.<MainCtrl>getController();
		mainCtrl.setFitProcessor(fp);

		fp.refreshControllers();
		fp.updateFit();

		return true;
	}

	private void initSwing(final JFXPanel fxPanel) {
		final JFrame frame = new JFrame(TITLE);
		frame.add(fxPanel);
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		// set title and icon
		frame.setTitle(TITLE);
		frame.setIconImages(getIcons(FLIMJCommand.class.getClassLoader().getResource(ICON_PATH)));

		frame.pack();
		// HACK: add extra margins to correctly dispay the whole scene
		frame.setSize(frame.getWidth() + 20, frame.getHeight() + 50);
		// center window
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

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
	private List<Image> getIcons(final URL url) {
		final Image img = new ImageIcon(url).getImage();
		final List<Image> imgs = new ArrayList<>();
		for (int size : new int[] {16, 20, 32, 40}) {
			imgs.add(img.getScaledInstance(size, size, Image.SCALE_SMOOTH));
		}
		return imgs;
	}
}
