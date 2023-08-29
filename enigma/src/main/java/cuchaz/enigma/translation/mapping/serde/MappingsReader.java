package cuchaz.enigma.translation.mapping.serde;

import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.tree.EntryTree;

import java.io.IOException;
import java.nio.file.Path;

// todo: remove EntryTree, just add mappings to index
public interface MappingsReader {
	EntryTree<EntryMapping> read(Path path, EntryIndex index, ProgressListener progress) throws MappingParseException, IOException;

	default EntryTree<EntryMapping> read(Path path, EntryIndex index) throws MappingParseException, IOException {
		return this.read(path, index, ProgressListener.none());
	}
}
