package org.quiltmc.enigma.impl.plugin.mapping_io;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.VisitOrder;
import net.fabricmc.mappingio.tree.VisitableMappingTree;
import org.quiltmc.enigma.api.EnigmaPluginContext;
import org.quiltmc.enigma.api.ProgressListener;
import org.quiltmc.enigma.api.analysis.index.jar.JarIndex;
import org.quiltmc.enigma.api.service.ReadWriteService;
import org.quiltmc.enigma.api.translation.mapping.EntryMapping;
import org.quiltmc.enigma.api.translation.mapping.MappingDelta;
import org.quiltmc.enigma.api.translation.mapping.serde.FileType;
import org.quiltmc.enigma.api.translation.mapping.serde.MappingParseException;
import org.quiltmc.enigma.api.translation.mapping.serde.MappingSaveParameters;
import org.quiltmc.enigma.api.translation.mapping.tree.EntryTree;
import org.quiltmc.enigma.util.I18n;
import org.quiltmc.enigma.util.MappingOperations;

import java.io.IOException;
import java.nio.file.Path;

public class MappingIoFormats {
	private static final String PROGUARD_ID = "enigma:mapping_io/proguard";

	public static void register(EnigmaPluginContext ctx) {
		FileType.File enigmaMapping = new FileType.File("mapping", "mappings");

		ctx.registerService(ReadWriteService.TYPE,
			ctx1 -> create(MappingFormat.ENIGMA_FILE, true, enigmaMapping, "enigma:mapping_io/tiny_file")
		);
	}

	static ReadWriteService create(MappingFormat format, boolean supportsWriting, FileType fileType, String id) {
		return new ReadWriteService() {
			@Override
			public void write(EntryTree<EntryMapping> mappings, MappingDelta<EntryMapping> delta, Path path, ProgressListener progress, MappingSaveParameters saveParameters) {
				try {
					MappingWriter writer = MappingWriter.create(path, format);

					if (writer == null) {
						throw new UnsupportedOperationException("This service does not support writing!");
					}

					VisitableMappingTree tree = MappingIoConverter.toMappingIo(mappings, progress);
					progress.init(1, I18n.translate("progress.mappings.writing"));
					progress.step(1, null); // Reset message

					tree.accept(writer, VisitOrder.createByName());
					progress.step(1, I18n.translate("progress.done"));
				} catch (IOException e) {

				}
			}

			@Override
			public EntryTree<EntryMapping> read(Path path, ProgressListener progress, JarIndex index) throws MappingParseException, IOException {
				String loadingMessage;

				if (format.hasSingleFile()) {
					loadingMessage = I18n.translate("progress.mappings.loading_file");
				} else {
					loadingMessage = I18n.translate("progress.mappings.loading_directory");
				}

				progress.init(1, loadingMessage);

				VisitableMappingTree mappingTree = new MemoryMappingTree();
				MappingReader.read(path, format, mappingTree);
				EntryTree<EntryMapping> mappings = MappingIoConverter.fromMappingIo(mappingTree, progress, index);

				return this.getId().equals(PROGUARD_ID) ? MappingOperations.invert(mappings) : mappings;
			}

			@Override
			public boolean supportsReading() {
				return true;
			}

			@Override
			public boolean supportsWriting() {
				return supportsWriting;
			}

			@Override
			public String getId() {
				return id;
			}

			@Override
			public FileType getFileType() {
				return fileType;
			}
		};
	}
}
