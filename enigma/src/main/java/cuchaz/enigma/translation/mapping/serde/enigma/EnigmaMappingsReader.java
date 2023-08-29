package cuchaz.enigma.translation.mapping.serde.enigma;

import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.source.RenamableTokenType;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.MappingPair;
import cuchaz.enigma.translation.mapping.serde.MappingHelper;
import cuchaz.enigma.translation.mapping.serde.MappingParseException;
import cuchaz.enigma.translation.mapping.serde.MappingsReader;
import cuchaz.enigma.translation.mapping.serde.RawEntryMapping;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.mapping.tree.HashEntryTree;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.I18n;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public enum EnigmaMappingsReader implements MappingsReader {
	FILE {
		@Override
		public EntryTree<EntryMapping> read(Path path, EntryIndex index, ProgressListener progress) throws IOException, MappingParseException {
			progress.init(1, I18n.translate("progress.mappings.enigma_file.loading"));

			EntryTree<EntryMapping> mappings = new HashEntryTree<>();
			readFile(index, path, mappings);

			progress.step(1, I18n.translate("progress.mappings.enigma_file.done"));

			return mappings;
		}
	},
	DIRECTORY {
		@Override
		public EntryTree<EntryMapping> read(Path root, EntryIndex index, ProgressListener progress) throws IOException, MappingParseException {
			if (!Files.isDirectory(root)) {
				throw new NotDirectoryException(root.toString());
			}

			EntryTree<EntryMapping> mappings = new HashEntryTree<>();

			List<Path> files;
			try (Stream<Path> fileStream = Files.walk(root)) {
				files = fileStream
					.filter(f -> !Files.isDirectory(f))
					.filter(f -> f.toString().endsWith(".mapping"))
					.toList();
			}

			progress.init(files.size(), I18n.translate("progress.mappings.enigma_directory.loading"));
			int step = 0;

			for (Path file : files) {
				progress.step(step++, root.relativize(file).toString());
				if (Files.isHidden(file)) {
					continue;
				}

				readFile(index, file, mappings);
			}

			return mappings;
		}
	},
	ZIP {
		@Override
		public EntryTree<EntryMapping> read(Path zip, EntryIndex index, ProgressListener progress) throws MappingParseException, IOException {
			try (FileSystem fs = FileSystems.newFileSystem(zip, (ClassLoader) null)) {
				return DIRECTORY.read(fs.getPath("/"), index, progress);
			}
		}
	};

	private static void readFile(EntryIndex index, Path path, EntryTree<EntryMapping> mappings) throws IOException, MappingParseException {
		List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		Deque<MappingPair<?, RawEntryMapping>> mappingStack = new ArrayDeque<>();

		for (int lineNumber = 0; lineNumber < lines.size(); lineNumber++) {
			String line = lines.get(lineNumber);
			int indentation = countIndentation(line);

			line = formatLine(line);
			if (line == null) {
				continue;
			}

			cleanMappingStack(indentation, mappingStack, mappings);

			try {
				MappingPair<?, RawEntryMapping> pair = parseLine(index, mappingStack.peek(), line);
				if (pair != null) {
					mappingStack.push(pair);
				}
			} catch (Exception e) {
				throw new MappingParseException(path, lineNumber, e);
			}
		}

		// Clean up rest
		cleanMappingStack(0, mappingStack, mappings);
	}

	private static void cleanMappingStack(int indentation, Deque<MappingPair<?, RawEntryMapping>> mappingStack, EntryTree<EntryMapping> mappings) {
		while (indentation < mappingStack.size()) {
			MappingPair<?, RawEntryMapping> pair = mappingStack.pop();
			if (pair.getMapping() != null) {
				mappings.insert(pair.getEntry(), pair.getMapping().bake());
				pair.getEntry().setMapping(pair.getMapping().bake());
			}
		}
	}

	@Nullable
	private static String formatLine(String line) {
		line = stripComment(line);
		line = line.trim();

		if (line.isEmpty()) {
			return null;
		}

		return line;
	}

	private static String stripComment(String line) {
		//Dont support comments on javadoc lines
		if (line.trim().startsWith(EnigmaFormat.COMMENT)) {
			return line;
		}

		int commentPos = line.indexOf('#');
		if (commentPos >= 0) {
			return line.substring(0, commentPos);
		}

		return line;
	}

	private static int countIndentation(String line) {
		int indent = 0;
		for (int i = 0; i < line.length(); i++) {
			if (line.charAt(i) != '\t') {
				break;
			}

			indent++;
		}

		return indent;
	}

	private static MappingPair<?, RawEntryMapping> parseLine(EntryIndex index, @Nullable MappingPair<?, RawEntryMapping> parent, String line) {
		String[] tokens = line.trim().split("\\s");
		String keyToken = tokens[0].toUpperCase(Locale.ROOT);
		Entry<?> parentEntry = parent == null ? null : parent.getEntry();

		switch (keyToken) {
			case EnigmaFormat.CLASS -> {
				return parseClass(index, parentEntry, tokens);
			}
			case EnigmaFormat.FIELD -> {
				return parseField(index, parentEntry, tokens);
			}
			case EnigmaFormat.METHOD -> {
				return parseMethod(index, parentEntry, tokens);
			}
			case EnigmaFormat.PARAMETER -> {
				return parseArgument(index, parentEntry, tokens);
			}
			case EnigmaFormat.COMMENT -> {
				readJavadoc(parent, tokens);
				return null;
			}
			default -> {
				if (keyToken.equals("V1")) {
					throw new RuntimeException("Unknown token '" + keyToken + "' (wrong mappings format?)");
				} else {
					throw new RuntimeException("Unknown token '" + keyToken + "'");
				}
			}
		}
	}

	private static void readJavadoc(MappingPair<?, RawEntryMapping> parent, String[] tokens) {
		if (parent == null) {
			throw new IllegalStateException("Javadoc has no parent!");
		}

		// Empty string to concat
		String jdLine = tokens.length > 1 ? String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length)) : "";
		if (parent.getMapping() == null) {
			parent.setMapping(new RawEntryMapping(parent.getEntry().getName(), RenamableTokenType.DEOBFUSCATED));
		}

		parent.getMapping().addJavadocLine(MappingHelper.unescape(jdLine));
	}

	private static MappingPair<ClassEntry, RawEntryMapping> parseClass(EntryIndex index, @Nullable Entry<?> parent, String[] tokens) {
		String obfuscatedName = ClassEntry.getInnerName(tokens[1]);
		ClassEntry obfuscatedEntry = index.getClass(obfuscatedName);

		String mapping = null;
		if (tokens.length == 3) {
			mapping = tokens[2];
		} else if (tokens.length != 2) {
			throw new RuntimeException("invalid class declaration: not enough tokens (" + tokens.length + " found, 2 needed)!");
		}

		return new MappingPair<>(obfuscatedEntry, new RawEntryMapping(mapping, RenamableTokenType.DEOBFUSCATED));
	}

	private static MappingPair<FieldEntry, RawEntryMapping> parseField(EntryIndex index, @Nullable Entry<?> parent, String[] tokens) {
		if (!(parent instanceof ClassEntry ownerEntry)) {
			throw new RuntimeException("Field must be a child of a class!");
		}

		String obfuscatedName = tokens[1];
		String mapping = null;
		TypeDescriptor descriptor;

		if (tokens.length == 3) {
			descriptor = new TypeDescriptor(tokens[2]);
		} else if (tokens.length == 4) {
			mapping = tokens[2];
			descriptor = new TypeDescriptor(tokens[3]);
		} else if (tokens.length == 5) {
			mapping = tokens[2];
			descriptor = new TypeDescriptor(tokens[4]);
		} else {
			throw new RuntimeException("Invalid field declaration");
		}

		FieldEntry obfuscatedEntry = index.getField(ownerEntry, obfuscatedName, descriptor);
		return new MappingPair<>(obfuscatedEntry, new RawEntryMapping(mapping, RenamableTokenType.DEOBFUSCATED));
	}

	private static MappingPair<MethodEntry, RawEntryMapping> parseMethod(EntryIndex index, @Nullable Entry<?> parent, String[] tokens) {
		if (!(parent instanceof ClassEntry ownerEntry)) {
			throw new RuntimeException("Method must be a child of a class!");
		}

		String obfuscatedName = tokens[1];
		String mapping = null;
		MethodDescriptor descriptor;

		if (tokens.length == 3) {
			descriptor = new MethodDescriptor(tokens[2]);
		} else if (tokens.length == 4 || tokens.length == 5) {
			mapping = tokens[2];
			descriptor = new MethodDescriptor(tokens[3]);
		} else {
			throw new RuntimeException("Invalid method declaration");
		}

		MethodEntry obfuscatedEntry = index.getMethod(ownerEntry, obfuscatedName, descriptor);
		return new MappingPair<>(obfuscatedEntry, new RawEntryMapping(mapping, RenamableTokenType.DEOBFUSCATED));
	}

	private static MappingPair<LocalVariableEntry, RawEntryMapping> parseArgument(EntryIndex index, @Nullable Entry<?> parent, String[] tokens) {
		if (!(parent instanceof MethodEntry ownerEntry)) {
			throw new RuntimeException("Method arg must be a child of a method!");
		}

		LocalVariableEntry obfuscatedEntry = index.getLocalVariable(ownerEntry, Integer.parseInt(tokens[1]), "", true);
		String mapping = tokens[2];

		return new MappingPair<>(obfuscatedEntry, new RawEntryMapping(mapping, RenamableTokenType.DEOBFUSCATED));
	}
}
