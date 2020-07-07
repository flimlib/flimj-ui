package flimlib.flimj.ui.controller;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import net.imagej.Dataset;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;
import org.scijava.widget.FileWidget;

import flimlib.NoiseType;
import flimlib.flimj.FitParams;
import flimlib.flimj.FitResults;
import flimlib.flimj.ui.FitParamsPrompter;
import flimlib.flimj.ui.FitProcessor;
import flimlib.flimj.ui.FitProcessor.FitType;
import flimlib.flimj.ui.Utils;
import flimlib.flimj.ui.controls.NumericSpinner;
import flimlib.flimj.ui.controls.NumericTextField;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.StringConverter;

/**
 * The controller of the "Settings" tab.
 */
public class SettingsCtrl extends AbstractCtrl {

	@FXML
	private NumericSpinner binSizeSpinner, iThreshSpinner;

	@FXML
	private NumericTextField chisqTgtTextField;

	@FXML
	private TextField chisqTextField;

	@FXML
	private ChoiceBox<NoiseType> noiseChoiceBox;

	@FXML
	private ChoiceBox<FitType> algoChoiceBox;

	@FXML
	private ChoiceBox<String> irfChoiceBox;

	@FXML
	private ChoiceBox<Integer> nCompChoiceBox;

	@FXML
	private GridPane paramPane;

	@FXML
	private Button fitButton;

	@FXML
	private ProgressIndicator fittingBusyProgressIndicator, binningBusyProgressIndicator;

	/** The list of all parameter name labels */
	private List<Text> paramLabels;

	/** The list of all parameter value TextFields */
	private List<NumericTextField> paramValues;

	/** The list of all parameter fixing state CheckBox */
	private List<CheckBox> paramFixed;

	/** The list of all input parameter indices */
	private List<Integer> paramIndices;

	/** The list of dataset present under the current context */
	private HashMap<String, FitParams<FloatType>> presentDatasets;

	@Override
	public void initialize() {
		paramLabels = new ArrayList<>();
		paramValues = new ArrayList<>();
		paramFixed = new ArrayList<>();
		paramIndices = new ArrayList<>();
		presentDatasets = new HashMap<>();
		// keep only the table header (remove the preview parameters)
		paramPane.getChildren().removeIf(child -> GridPane.getRowIndex(child) > 0);

		// numerical fields
		// iThreshSpinner.setMin(0.0);
		iThreshSpinner.setStepSize(1.0);
		iThreshSpinner.getNumberProperty().addListener((obs, oldVal, newVal) -> {
			FitParams<FloatType> params = getParams();
			params.iThresh = newVal.floatValue();
			// turn off estimate based on percentage
			// otherwise user's setting iThresh = 0 triggers that
			params.iThreshPercent = params.iThresh >= 0 ? -1 : 5;

			requestUpdate();
		});

		HashMap<String, Double> kwMap = new HashMap<>();
		kwMap.put("FULL", -1.0);
		binSizeSpinner.setKwMap(kwMap);
		binSizeSpinner.setIntOnly(true);
		binSizeSpinner.setMin(0.0);
		binSizeSpinner.setMax(255.0);
		binSizeSpinner.setStepSize(1.0);
		binSizeSpinner.getNumberProperty().addListener((obs, oldVal, newVal) -> {
			binSizeSpinner.setDisable(true);
			binningBusyProgressIndicator.setVisible(true);
			// binning will freeze the JFX thread and not allow the +/- event to be consumed
			// which causes indefinite +/- and resulting calls to setBinning()
			fp.submitRunnable(() -> {
				fp.setBinning(newVal.intValue());

				// update of UI components should be run from JFX thread
				Platform.runLater(() -> {
					binSizeSpinner.setDisable(false);
					binningBusyProgressIndicator.setVisible(false);

					requestUpdate();
				});
			});
		});

		chisqTgtTextField.getNumberProperty().addListener((obs, oldVal, newVal) -> {
			getParams().chisq_target = newVal.floatValue();
			requestUpdate();
		});

		// CB's
		noiseChoiceBox.setConverter(new StringConverter<NoiseType>() {
			@Override
			public String toString(NoiseType noiseType) {
				switch (noiseType) {
					case NOISE_GAUSSIAN_FIT:
						return "Gaussian (Fit)";
					case NOISE_POISSON_FIT:
						return "Poisson (Fit)";
					case NOISE_POISSON_DATA:
						return "Poisson (Data)";
					case NOISE_MLE:
						return "MLE";
					default:
						return "";
				}
			}

			@Override
			public NoiseType fromString(String string) {
				switch (string) {
					case "Gaussian (Fit)":
						return NoiseType.NOISE_GAUSSIAN_FIT;
					case "Poisson (Fit)":
						return NoiseType.NOISE_POISSON_FIT;
					case "Poisson (Data)":
						return NoiseType.NOISE_POISSON_DATA;
					case "MLE":
						return NoiseType.NOISE_MLE;
					default:
						return null;
				}
			}
		});
		// kept separate from numerical fields because they don't have onChange
		ChangeListener<Object> paramPaneUpdateHandler = (obs, oldVal, newVal) -> {
			final Integer nComp = nCompChoiceBox.getValue();
			final FitType algo = algoChoiceBox.getValue();
			FitParams<FloatType> params = getParams();
			params.nComp = nComp;
			fp.setAlgo(algo);
			setupParams(algo, nComp);

			// https://github.com/flimlib/flimj-ui/issues/8
			// https://github.com/flimlib/flimj-ui/issues/9
			if (algo == FitType.Bayes) {
				nCompChoiceBox.setValue(1);
				nCompChoiceBox.setDisable(true);
				chisqTgtTextField.setDisable(true);
				noiseChoiceBox.setDisable(true);
			} else {
				nCompChoiceBox.setDisable(false);
				chisqTgtTextField.setDisable(false);
				noiseChoiceBox.setDisable(false);
			}

			// resize and initialize the two arrays
			int nParam = fp.getNParam();
			int fillStart = params.paramFree.length;
			params.param = Arrays.copyOf(params.param, nParam);
			params.paramMap = ArrayImgs.floats(params.param,
					FitProcessor.swapInLtAxis(new long[] {1, 1, nParam}, params.ltAxis));
			params.paramFree = Arrays.copyOf(params.paramFree, nParam);
			for (int i = fillStart; i < params.paramFree.length; i++)
				params.paramFree[i] = true;

			requestUpdate();
		};
		algoChoiceBox.valueProperty().addListener(paramPaneUpdateHandler);
		nCompChoiceBox.valueProperty().addListener(paramPaneUpdateHandler);

		noiseChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> {
			getParams().noise = noiseChoiceBox.getValue();
			requestUpdate();
		});

		irfChoiceBox.getItems().add("From file");
		irfChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> {
			// this happens when the list items are changed
			// if no item is now selected, select the previously selected item
			if (newVal == null) {
				irfChoiceBox.setValue(oldVal);
				return;
			}
			// this happens when we recover from the above situation
			if (oldVal == null) {
				return;
			}

			FitParams<FloatType> chosenIRF;
			if ("From file".equals(newVal)) {
				// the name of selected dataset
				String currentSelection = oldVal;
				// choose from file
				File irfFile = getUIs().chooseFile("Choose IRF transient file", null,
						FileWidget.OPEN_STYLE);
				if (irfFile != null) {
					// not cancelled
					String irfPath = irfFile.getPath();
					if (irfPath.endsWith("asc") || getDss().canOpen(irfPath)) {
						try {
							chosenIRF = IRFDatasetFromFile(irfPath);
							// throw away if canceled by user
							if (chosenIRF != null) {
								// use file name as irf name
								currentSelection =
										irfPath.substring(irfPath.lastIndexOf(File.separator) + 1);
								// add to options if not present ([0] = "None")
								if (!presentDatasets.containsKey(currentSelection)) {
									irfChoiceBox.getItems().add(1, currentSelection);
								}
								// remember/update IRF
								presentDatasets.put(currentSelection, chosenIRF);
							}
						} catch (IOException e) {
							e.printStackTrace();
							getUIs().showDialog(
									String.format(
											"Error occurred during opening %s\nSee Console log.",
											irfPath),
									"FLIMJ", MessageType.ERROR_MESSAGE,
									OptionType.OK_CANCEL_OPTION);
						}
					} else
						getUIs().showDialog(
								String.format(
										"%s cannot be opened as a Dataset or an ASCII number list.",
										irfPath),
								"FLIMJ", MessageType.ERROR_MESSAGE, OptionType.OK_CANCEL_OPTION);
				}
				// either set to the new, valid dataset or stick to the old one
				irfChoiceBox.setValue(currentSelection);
				return;
			} else if (!"None".equals(newVal)) {
				// locate the chosen dataset
				chosenIRF = presentDatasets.get(newVal);
			} else
				chosenIRF = null;

			// update IRF information and notify fp
			fp.setIRF(chosenIRF);
			requestUpdate();
		});

		fitButton.setOnAction(event -> {
			fitButton.setDisable(true);
			fittingBusyProgressIndicator.setVisible(true);

			// do heavy lifting on a separate thread
			fp.submitRunnable(() -> {
				fp.fitDataset();

				// update UI when done
				Platform.runLater(() -> {
					fitButton.setDisable(false);
					fittingBusyProgressIndicator.setVisible(false);

					// set new options
					List<String> previewOptions = new ArrayList<>();
					for (Text label : paramLabels)
						previewOptions.add(label.getText());
					previewOptions.add("τₘ");
					fp.setPreviewOptions(previewOptions);

					requestUpdate();
				});
			});
		});
	}

	@Override
	public void refresh(FitParams<FloatType> params, FitResults results) {
		iThreshSpinner.setMax(getOps().stats().max(results.intensityMap).getRealDouble());
		iThreshSpinner.getNumberProperty().setValue((double) params.iThresh);
		chisqTgtTextField.setText(Utils.prettyFmt(params.chisq_target));
		noiseChoiceBox.setValue(params.noise);
		nCompChoiceBox.setValue(params.nComp);
		chisqTextField.setText(Utils.prettyFmt(results.chisq));

		if (results.param != null) {
			for (int i = 0; i < results.param.length; i++) {
				final int paramIndex = paramIndices.get(i);
				paramValues.get(paramIndex).getNumberProperty().set((double) results.param[i]);
				paramFixed.get(i).selectedProperty().set(!params.paramFree[i]);
			}
		}
	}

	/**
	 * Adjust the parameter pane to make the parameter labels agree with the algorithm and the
	 * number of components.
	 * 
	 * @param algo  the algorithm used to perform fitting
	 * @param nComp the number of components (available only for LMA and global)
	 */
	private void setupParams(FitType algo, int nComp) {
		List<String> paramNames = new ArrayList<>();
		List<Boolean> paramIsInput = new ArrayList<>();
		paramIndices.clear();
		switch (algo) {
			case LMA:
			case Global:
			case Bayes:
				final String[] subScripts = {"₁", "₂", "₃", "ᵢ"};

				paramNames.add("z");
				paramIsInput.add(true);

				for (int i = 0; i < nComp; i++) {
					String subscript = nComp > 1
							? subScripts[i >= subScripts.length ? subScripts.length - 1 : i]
							: "";

					paramNames.add("A" + subscript);
					paramIsInput.add(true);

					if (nComp > 1) {
						paramNames.add("A" + subscript + " %");
						paramIsInput.add(false);
					}

					paramNames.add("τ" + subscript);
					paramIsInput.add(true);
				}
				break;

			default:
				break;
		}

		for (int i = 0; i < paramIsInput.size(); i++) {
			if (paramIsInput.get(i)) {
				paramIndices.add(i);
			}
		}

		setParams(paramNames, paramIsInput);

		switch (algo) {
			case LMA:
			case Global:
			case Bayes:
				// bind sum(A_i) to A_i's
				if (nComp > 1) {
					ObjectProperty<Double> sumA = new SimpleObjectProperty<>();
					sumA.set(0.0);
					for (int i = 0; i < nComp; i++) {
						paramValues.get(paramIndices.get(i * 2 + 1)).getNumberProperty()
								.addListener((obs, oldVal, newVal) -> {
									Double newSum = 0.0;
									for (int j = 0; j < nComp; j++) {
										int a_iIndex = paramIndices.get(j * 2 + 1);
										newSum +=
												paramValues.get(a_iIndex).getNumberProperty().get();
									}
									sumA.set(newSum);
								});
					}
					// if sum is updated, update each A_i%
					sumA.addListener((obs, oldVal, newVal) -> {
						for (int i = 0; i < nComp; i++) {
							int a_iIndex = paramIndices.get(i * 2 + 1);
							Double a_i = paramValues.get(a_iIndex).getNumberProperty().get();
							NumericTextField aiPercentTF = ((NumericTextField) paramPane
									.lookup("#display" + (2 * i + 1) + "_display"));
							Double newPercentage = a_i / newVal * 100;
							aiPercentTF.getNumberProperty()
									.set(Double.isFinite(newPercentage) ? newPercentage : 0.0);
						}
					});
				}

				break;

			default:
				break;
		}
	}

	/**
	 * Set the parameter labels. Add and remove entries if necessary.
	 * 
	 * @param paramNames the list of all labels
	 */
	private void setParams(List<String> paramNames, List<Boolean> paramIsInputs) {
		// trim table
		// paramPane.getChildren().removeIf(child -> GridPane.getRowIndex(child) >=
		// paramNames.size());
		paramPane.getChildren().clear();
		paramLabels.removeIf(element -> element.getParent() != paramPane);
		paramValues.removeIf(element -> element.getParent() != paramPane);
		paramFixed.removeIf(element -> element.getParent() != paramPane);
		int indesShift = 0;
		// change row labels (add new ones if required)
		for (int i = 0; i < paramNames.size(); i++) {
			final String paramName = paramNames.get(i);
			final boolean paramIsInput = paramIsInputs.get(i);
			if (!paramIsInput)
				indesShift++;
			addParamRow(paramName, paramIsInput, i - indesShift);
		}
	}

	/**
	 * Create a parameter entry that includes the label, the input TextField and the "Fix" CheckBox.
	 * 
	 * @param name     the parameter label
	 * @param isInput  true if the parameter can have a "Fix" checkbox and mutable value
	 * @param paramIdx the index in params.param[] or params.paramFree[]
	 */
	private void addParamRow(String name, boolean isInput, int paramIdx) {
		String paramId = (isInput ? "param" : "display") + paramIdx;

		// the name label
		Text paramNameText = new Text(name);
		paramNameText.setFont(new Font("Cambria", 13));
		paramNameText.setId(paramId + "_name");
		paramLabels.add(paramNameText);

		// the input text field
		NumericTextField paramTF = new NumericTextField();
		paramTF.setId(paramId + (isInput ? "_input" : "_display"));
		paramValues.add(paramTF);
		paramTF.setEditable(isInput);

		final int rowIndex = paramLabels.size() + 1;

		if (isInput) {
			CheckBox paramCB = new CheckBox();
			paramCB.setId(paramId + "_fixed");

			// "Fix" checkbox, automatically selected on user input to the text filed
			final ObservableList<String> paramTFSC = paramTF.getStyleClass();
			paramCB.selectedProperty().addListener((obs, oldVal, newVal) -> {
				if (paramCB.isSelected()) {
					if (!paramTFSC.contains("param-fiexd")) {
						paramTFSC.add("param-fiexd");
					}
				} else {
					paramTFSC.remove("param-fiexd");
				}
				FitParams<FloatType> params = getParams();
				params.paramFree[paramIdx] = !newVal;
				// make sure fixed params don't get inf accidentally
				if (!params.paramFree[paramIdx]) {
					NumericTextField paramInput =
							(NumericTextField) paramPane.lookup("#" + paramId + "_input");
					params.param[paramIdx] = paramInput.getNumberProperty().get().floatValue();
				}

				requestUpdate();
			});
			paramFixed.add(paramCB);

			EventHandler<ActionEvent> oldOnAction = paramTF.getOnAction();
			paramTF.setOnAction(event -> {
				oldOnAction.handle(event);
				// on input: set param fixed
				paramCB.setSelected(true);
			});
			paramTF.getNumberProperty().addListener((obs, oldValue, newValue) -> {
				FitParams<FloatType> params = getParams();
				params.param[paramIdx] = newValue.floatValue();

				// update when an already fixed param is changed
				if (!params.paramFree[paramIdx]) {
					requestUpdate();
				}
			});

			paramPane.addRow(rowIndex, paramNameText, paramTF, paramCB);
		} else
			paramPane.addRow(rowIndex, paramNameText, paramTF);
	}

	private FitParams<FloatType> IRFDatasetFromFile(String irfPath) throws IOException {
		FitParams<FloatType> irfParams = new FitParams<>();
		if (irfPath.endsWith(".asc")) {
			// read whitespace separated data
			ArrayList<Float> irfTrans = new ArrayList<>();
			Scanner sc = new Scanner(new File(irfPath));
			while (sc.hasNextBigDecimal())
				irfTrans.add(sc.nextBigDecimal().floatValue());
			sc.close();

			// get origTrans' size and ltAxis
			int ltAxis = fp.getParams().ltAxis;
			long[] transDim = new long[3];
			fp.getOrigTrans().dimensions(transDim);

			float[] irfTransArr = new float[(int) transDim[ltAxis]];
			// place the data at the end so that the leading 0's can be used for shifting
			for (int i = Math.max(irfTransArr.length - irfTrans.size(), 0), j =
					0; i < irfTransArr.length; i++, j++)
				irfTransArr[i] = irfTrans.get(j);

			// create a fake image out of a single array along the ltAxis
			irfParams.transMap = Views.interval(
					Views.extendBorder(
							ArrayImgs.floats(irfTransArr, new long[] {1, 1, irfTransArr.length})),
					new long[] {0, 0, 0},
					new long[] {transDim[0] - 1, transDim[1] - 1, transDim[2] - 1});
			irfParams.ltAxis = ltAxis;
		} else if (getDss().canOpen(irfPath)) {
			final Dataset dataset = getDss().open(irfPath);
			final Localizable pos = new Point(Intervals.minAsLongArray(dataset));
			if (!FitParamsPrompter.populate(irfParams, dataset, pos))
				irfParams = null;
		}

		return irfParams;
	}

	@Override
	public void destroy() {
		super.destroy();
		presentDatasets = null;
	}
}
