/*
 * Based on SampleStringTranslationPlugin (IP: GHIDRA)
 */
package argosstringtranslationplugin;

import static ghidra.program.model.data.TranslationSettingsDefinition.TRANSLATION;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

import ghidra.MiscellaneousPluginPackage;
import ghidra.app.plugin.PluginCategoryNames;
import ghidra.app.services.StringTranslationService;
import ghidra.framework.options.OptionType;
import ghidra.framework.options.OptionsChangeListener;
import ghidra.framework.options.ToolOptions;
import ghidra.framework.plugintool.Plugin;
import ghidra.framework.plugintool.PluginInfo;
import ghidra.framework.plugintool.PluginTool;
import ghidra.framework.plugintool.util.PluginStatus;
import ghidra.program.model.data.DataUtilities;
import ghidra.program.model.data.StringDataInstance;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.Program;
import ghidra.program.util.ProgramLocation;
import ghidra.util.HelpLocation;
import ghidra.util.Msg;
import ghidra.util.task.TaskLauncher;

//@formatter:off
@PluginInfo(
    status = PluginStatus.RELEASED,
    packageName = MiscellaneousPluginPackage.NAME,
    category = PluginCategoryNames.COMMON,
    shortDescription = "Argos Translation",
    description = "Offline String Translation Plugin using Argos.",
    servicesProvided = { StringTranslationService.class }
)
//@formatter:on

public class ArgosStringTranslationPlugin extends Plugin implements StringTranslationService, OptionsChangeListener {

	private static final String OPTIONS_TITLE = "Argos Translation";
	private static final String SOURCE_LANG_OPTION = "Source Language";
	private static final String TARGET_LANG_OPTION = "Target Language";
	private static final String ARGOS_PATH_OPTION = "Argos Path";

	private String sourceLang = "ko";
	private String targetLang = "en";
	private String argosPath = "/usr/bin/argos-translate";

	public ArgosStringTranslationPlugin(PluginTool tool) {
		super(tool);
	}

	@Override
	public void init() {
		super.init();
		initializeOptions();
	}

	@Override
	public String getTranslationServiceName() {
		return "Argos String Translation";
	}

	@Override
	public void translate(Program program, List<ProgramLocation> stringLocations, TranslateOptions options) {
        TaskLauncher.launchModal("Translate strings", monitor -> {
            int id = program.startTransaction("Translate strings");
            try {
                for (ProgramLocation progLoc : stringLocations) {
                    Data data = DataUtilities.getDataAtLocation(progLoc);
                    StringDataInstance str = StringDataInstance.getStringDataInstance(data);
                    String s = str.getStringValue();

					if (s != null) {
						ProcessBuilder processBuilder = new ProcessBuilder(argosPath, "--from", sourceLang, "--to", targetLang, s);
						Map<String, String> environment = processBuilder.environment();
						environment.put("ARGOS_DEVICE_TYPE", "auto");
						Process process = processBuilder.start();
						BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
						String translatedValue = reader.readLine();
						process.waitFor();
						Msg.info("Original:" + s + ", Translated:" + translatedValue, this);

						if (translatedValue != null && !translatedValue.isEmpty()) {
							TRANSLATION.setTranslatedValue(data, translatedValue);
							TRANSLATION.setShowTranslated(data, true);
						}
					}
				}
			} catch (Exception e) {
				Msg.error(this, "Error during translation", e);
			} finally {
				program.endTransaction(id, true);
			}
		});
	}

	private void initializeOptions() {
		ToolOptions opt = tool.getOptions(OPTIONS_TITLE);
		HelpLocation help = new HelpLocation("ArgosStringTranslationPlugin", "Translation_Options");
		opt.registerOption(SOURCE_LANG_OPTION, OptionType.STRING_TYPE, "ko", help, "Source language (e.g. ko for Korean)");
		opt.registerOption(TARGET_LANG_OPTION, OptionType.STRING_TYPE, "en", help, "Target language (e.g. en for English)");
		opt.registerOption(ARGOS_PATH_OPTION, OptionType.STRING_TYPE, "argos-translate", help, "Path to the Argos Translate executable");

		sourceLang = opt.getString(SOURCE_LANG_OPTION, "ko");
		targetLang = opt.getString(TARGET_LANG_OPTION, "en");
		argosPath = opt.getString(ARGOS_PATH_OPTION, "argos-translate");

		opt.addOptionsChangeListener(this);
	}

	@Override
	public void optionsChanged(ToolOptions options, String optionName, Object oldValue, Object newValue) {
		if (SOURCE_LANG_OPTION.equals(optionName)) {
			sourceLang = (String) newValue;
		} else if (TARGET_LANG_OPTION.equals(optionName)) {
			targetLang = (String) newValue;
		} else if (ARGOS_PATH_OPTION.equals(optionName)) {
			argosPath = (String) newValue;
		}
	}
}