package org.quiltmc.enigma.command;

import org.quiltmc.enigma.api.Enigma;
import org.quiltmc.enigma.api.EnigmaProfile;
import org.quiltmc.enigma.api.EnigmaProject;
import org.quiltmc.enigma.api.ProgressListener;
import org.quiltmc.enigma.api.analysis.index.EntryIndex;
import org.quiltmc.enigma.api.EnigmaPlugin;
import org.quiltmc.enigma.api.service.NameProposalService;
import org.quiltmc.enigma.api.translation.ProposingTranslator;
import org.quiltmc.enigma.api.translation.Translator;
import org.quiltmc.enigma.api.translation.mapping.EntryMapping;
import org.quiltmc.enigma.api.translation.mapping.EntryRemapper;
import org.quiltmc.enigma.api.translation.mapping.serde.MappingSaveParameters;
import org.quiltmc.enigma.api.translation.mapping.serde.MappingsWriter;
import org.quiltmc.enigma.api.translation.mapping.tree.DeltaTrackingTree;
import org.quiltmc.enigma.api.translation.mapping.tree.EntryTree;
import org.quiltmc.enigma.api.translation.mapping.tree.HashEntryTree;
import org.quiltmc.enigma.api.translation.representation.TypeDescriptor;
import org.quiltmc.enigma.api.translation.representation.entry.ClassEntry;
import org.quiltmc.enigma.api.translation.representation.entry.Entry;
import org.quiltmc.enigma.api.translation.representation.entry.FieldEntry;
import org.quiltmc.enigma.api.translation.representation.entry.LocalVariableEntry;
import org.quiltmc.enigma.api.translation.representation.entry.MethodEntry;
import org.quiltmc.enigma.util.Utils;
import org.tinylog.Logger;

import java.nio.file.Path;
import javax.annotation.Nullable;

public class InsertProposedMappingsCommand extends Command {
	public InsertProposedMappingsCommand() {
		super(Argument.INPUT_JAR.required(),
				Argument.INPUT_MAPPINGS.required(),
				Argument.MAPPING_OUTPUT.required(),
				Argument.OUTPUT_MAPPING_FORMAT.required(),
				Argument.ENIGMA_PROFILE.optional());
	}

	@Override
	public void run(String... args) throws Exception {
		Path inJar = getReadablePath(this.getArg(args, 0));
		Path source = getReadablePath(this.getArg(args, 1));
		Path output = getWritablePath(this.getArg(args, 2));
		String resultFormat = this.getArg(args, 3);
		Path profilePath = getReadablePath(this.getArg(args, 4));

		run(inJar, source, output, resultFormat, profilePath, null);
	}

	@Override
	public String getName() {
		return "insert-proposed-mappings";
	}

	@Override
	public String getDescription() {
		return "Adds all mappings proposed by the plugins on the classpath and declared in the profile into the given mappings.";
	}

	public static void run(Path inJar, Path source, Path output, String resultFormat, @Nullable Path profilePath, @Nullable Iterable<EnigmaPlugin> plugins) throws Exception {
		EnigmaProfile profile = EnigmaProfile.read(profilePath);
		Enigma enigma = createEnigma(profile, plugins);

		run(inJar, source, output, resultFormat, enigma);
	}

	public static void run(Path inJar, Path source, Path output, String resultFormat, Enigma enigma) throws Exception {
		boolean debug = shouldDebug(new InsertProposedMappingsCommand().getName());
		NameProposalService[] nameProposalServices = enigma.getServices().get(NameProposalService.TYPE).toArray(new NameProposalService[0]);
		if (nameProposalServices.length == 0) {
			Logger.error("No name proposal service found");
			return;
		}

		EnigmaProject project = openProject(inJar, source, enigma);
		EntryTree<EntryMapping> mappings = exec(nameProposalServices, project, debug);

		Utils.delete(output);
		MappingSaveParameters saveParameters = enigma.getProfile().getMappingSaveParameters();
		MappingsWriter writer = MappingCommandsUtil.getWriter(resultFormat);
		writer.write(mappings, output, ProgressListener.none(), saveParameters);

		if (debug) {
			writeDebugDelta((DeltaTrackingTree<EntryMapping>) mappings, output);
		}
	}

	public static EntryTree<EntryMapping> exec(NameProposalService[] nameProposalServices, EnigmaProject project, boolean trackDelta) {
		EntryTree<EntryMapping> mappings = new HashEntryTree<>(project.getMapper().getObfToDeobf());

		if (trackDelta) {
			mappings = new DeltaTrackingTree<>(mappings);
		}

		EntryRemapper mapper = project.getMapper();
		Translator translator = new ProposingTranslator(mapper, nameProposalServices);
		EntryIndex index = project.getJarIndex().getEntryIndex();

		Logger.info("Proposing class names...");
		int classes = 0;
		for (ClassEntry clazz : index.getClasses()) {
			if (insertMapping(clazz, mappings, mapper, translator)) {
				classes++;
			}
		}

		Logger.info("Proposing field names...");
		int fields = 0;
		for (FieldEntry field : index.getFields()) {
			if (insertMapping(field, mappings, mapper, translator)) {
				fields++;
			}
		}

		Logger.info("Proposing method and parameter names...");
		int methods = 0;
		int parameters = 0;
		for (MethodEntry method : index.getMethods()) {
			if (insertMapping(method, mappings, mapper, translator)) {
				methods++;
			}

			int p = index.getMethodAccess(method).isStatic() ? 0 : 1;
			for (TypeDescriptor paramDesc : method.getDesc().getArgumentDescs()) {
				LocalVariableEntry param = new LocalVariableEntry(method, p, "", true, null);
				if (insertMapping(param, mappings, mapper, translator)) {
					parameters++;
				}

				p += paramDesc.getSize();
			}
		}

		Logger.info("Proposed names for {} classes, {} fields, {} methods, {} parameters!", classes, fields, methods, parameters);
		return mappings;
	}

	private static <T extends Entry<?>> boolean insertMapping(T entry, EntryTree<EntryMapping> mappings, EntryRemapper mapper, Translator translator) {
		T deobf = mapper.extendedDeobfuscate(entry).getValue();
		String name = translator.extendedTranslate(entry).getValue().getName();
		if (!deobf.getName().equals(name) && !entry.getName().equals(name)) {
			String javadoc = deobf.getJavadocs();
			EntryMapping mapping = javadoc != null && !javadoc.isEmpty() ? new EntryMapping(name, javadoc) : new EntryMapping(name);
			mappings.insert(entry, mapping);
			return true;
		}

		return false;
	}
}