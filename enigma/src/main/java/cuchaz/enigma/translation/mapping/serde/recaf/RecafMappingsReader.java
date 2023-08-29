package cuchaz.enigma.translation.mapping.serde.recaf;

import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.serde.MappingParseException;
import cuchaz.enigma.translation.mapping.serde.MappingsReader;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.mapping.tree.HashEntryTree;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecafMappingsReader implements MappingsReader {
	public static final RecafMappingsReader INSTANCE = new RecafMappingsReader();
	private static final Pattern METHOD_PATTERN = Pattern.compile("(.*?)\\.(.*?)(\\(.*?) (.*)");
	private static final Pattern FIELD_PATTERN = Pattern.compile("(.*?)\\.(.*?) (.*?) (.*)");
	private static final Pattern CLASS_PATTERN = Pattern.compile("(.*?) (.*)");

	@Override
	public EntryTree<EntryMapping> read(Path path, EntryIndex index, ProgressListener progress) throws MappingParseException, IOException {
		EntryTree<EntryMapping> mappings = new HashEntryTree<>();
		List<String> lines = Files.readAllLines(path);

		// todo everything is a class lol
		for (String line : lines) {
			Matcher methodMatcher = METHOD_PATTERN.matcher(line);
			if (methodMatcher.find()) {
				ClassEntry owner = index.getClass(methodMatcher.group(1));
				String name = methodMatcher.group(2);
				MethodDescriptor desc = new MethodDescriptor(methodMatcher.group(3));
				MethodEntry method = index.getMethod(owner, name, desc);
				EntryMapping mapping = new EntryMapping(methodMatcher.group(4));
				mappings.insert(method, mapping);
				method.setMapping(mapping);
				continue;
			}

			Matcher fieldMatcher = FIELD_PATTERN.matcher(line);
			if (fieldMatcher.find()) {
				ClassEntry owner = index.getClass(fieldMatcher.group(1));
				String name = fieldMatcher.group(2);
				TypeDescriptor desc = new TypeDescriptor(fieldMatcher.group(3));
				FieldEntry field = index.getField(owner, name, desc);
				EntryMapping mapping = new EntryMapping(fieldMatcher.group(4));
				mappings.insert(field, mapping);
				field.setMapping(mapping);
				continue;
			}

			Matcher classMatcher = CLASS_PATTERN.matcher(line);
			if (classMatcher.find()) {
				ClassEntry entry = index.getClass(classMatcher.group(1));
				EntryMapping mapping = new EntryMapping(classMatcher.group(2));
				mappings.insert(entry, mapping);
				entry.setMapping(mapping);
			}
		}

		return mappings;
	}
}
