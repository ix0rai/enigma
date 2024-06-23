package org.quiltmc.enigma.impl.plugin.mapping_io;

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.VisitableMappingTree;
import org.quiltmc.enigma.api.ProgressListener;
import org.quiltmc.enigma.api.analysis.index.jar.EntryIndex;
import org.quiltmc.enigma.api.analysis.index.jar.JarIndex;
import org.quiltmc.enigma.api.translation.mapping.EntryMap;
import org.quiltmc.enigma.api.translation.mapping.EntryMapping;
import org.quiltmc.enigma.api.translation.mapping.tree.EntryTree;
import org.quiltmc.enigma.api.translation.mapping.tree.EntryTreeNode;
import org.quiltmc.enigma.api.translation.mapping.tree.HashEntryTree;
import org.quiltmc.enigma.api.translation.representation.MethodDescriptor;
import org.quiltmc.enigma.api.translation.representation.TypeDescriptor;
import org.quiltmc.enigma.api.translation.representation.entry.ClassEntry;
import org.quiltmc.enigma.api.translation.representation.entry.Entry;
import org.quiltmc.enigma.api.translation.representation.entry.FieldEntry;
import org.quiltmc.enigma.api.translation.representation.entry.LocalVariableEntry;
import org.quiltmc.enigma.api.translation.representation.entry.MethodEntry;
import org.quiltmc.enigma.util.I18n;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.StreamSupport;

public class MappingIoConverter {
	public static VisitableMappingTree toMappingIo(EntryTree<EntryMapping> mappings, ProgressListener progress) {
		return toMappingIo(mappings, progress, "intermediary", "named");
	}

	// todo: respect mapping save parameters
	public static VisitableMappingTree toMappingIo(EntryTree<EntryMapping> mappings, ProgressListener progress, String fromNs, String toNs) {
		try {
			List<EntryTreeNode<EntryMapping>> classes = StreamSupport.stream(mappings.spliterator(), false)
				.filter(node -> node.getEntry() instanceof ClassEntry)
				.toList();

			progress.init(classes.size(), I18n.translate("progress.mappings.converting.to_mappingio"));
			int stepsDone = 0;

			MemoryMappingTree mappingTree = new MemoryMappingTree();
			mappingTree.visitNamespaces(fromNs, List.of(toNs));

			for (EntryTreeNode<EntryMapping> classNode : classes) {
				progress.step(++stepsDone, classNode.getEntry().getFullName());
				writeClass(classNode, mappings, mappingTree);
			}

			mappingTree.visitEnd();
			return mappingTree;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void writeClass(EntryTreeNode<EntryMapping> classNode, EntryMap<EntryMapping> oldMappingTree, VisitableMappingTree newMappingTree) throws IOException {
		ClassEntry classEntry = (ClassEntry) classNode.getEntry();
		EntryMapping mapping = oldMappingTree.get(classEntry);
		Deque<String> parts = new LinkedList<>();

		newMappingTree.visitClass(classEntry.getFullName());

		if (mapping != null) {
			newMappingTree.visitComment(MappedElementKind.CLASS, mapping.javadoc());
		}

		do {
			mapping = oldMappingTree.get(classEntry);

			if (mapping != null && mapping.targetName() != null) {
				parts.addFirst(mapping.targetName());
			} else {
				parts.addFirst(classEntry.getName());
			}

			classEntry = classEntry.getOuterClass();
		} while (classEntry != null);

		String mappedName = String.join("$", parts);
		newMappingTree.visitDstName(MappedElementKind.CLASS, 0, mappedName);

		for (EntryTreeNode<EntryMapping> child : classNode.getChildNodes()) {
			Entry<?> entry = child.getEntry();

			if (entry instanceof FieldEntry) {
				writeField(child, newMappingTree);
			} else if (entry instanceof MethodEntry) {
				writeMethod(child, newMappingTree);
			}
		}
	}

	private static void writeField(EntryTreeNode<EntryMapping> fieldNode, VisitableMappingTree mappingTree) throws IOException {
		if (fieldNode.getValue() == null || fieldNode.getValue().equals(EntryMapping.DEFAULT)) {
			return; // Shortcut
		}

		FieldEntry fieldEntry = ((FieldEntry) fieldNode.getEntry());
		mappingTree.visitField(fieldEntry.getName(), fieldEntry.getDesc().toString());

		EntryMapping fieldMapping = fieldNode.getValue();

		if (fieldMapping == null) {
			fieldMapping = EntryMapping.DEFAULT;
		}

		mappingTree.visitDstName(MappedElementKind.FIELD, 0, fieldMapping.targetName());
		mappingTree.visitComment(MappedElementKind.FIELD, fieldMapping.javadoc());
	}

	private static void writeMethod(EntryTreeNode<EntryMapping> methodNode, VisitableMappingTree mappingTree) throws IOException {
		MethodEntry methodEntry = ((MethodEntry) methodNode.getEntry());
		mappingTree.visitMethod(methodEntry.getName(), methodEntry.getDesc().toString());

		EntryMapping methodMapping = methodNode.getValue();

		if (methodMapping == null) {
			methodMapping = EntryMapping.DEFAULT;
		}

		mappingTree.visitDstName(MappedElementKind.METHOD, 0, methodMapping.targetName());
		mappingTree.visitComment(MappedElementKind.METHOD, methodMapping.javadoc());

		for (EntryTreeNode<EntryMapping> child : methodNode.getChildNodes()) {
			Entry<?> entry = child.getEntry();

			if (entry instanceof LocalVariableEntry local) {
				if (local.isArgument()) {
					writeMethodArg(child, mappingTree);
				} else {
					writeMethodVar(child, mappingTree);
				}
			}
		}
	}

	private static void writeMethodArg(EntryTreeNode<EntryMapping> argNode, VisitableMappingTree mappingTree) throws IOException {
		if (argNode.getValue() == null || argNode.getValue().equals(EntryMapping.DEFAULT)) {
			return; // Shortcut
		}

		LocalVariableEntry argEntry = ((LocalVariableEntry) argNode.getEntry());
		mappingTree.visitMethodArg(-1, argEntry.getIndex(), argEntry.getName());

		EntryMapping argMapping = argNode.getValue();

		if (argMapping == null) {
			argMapping = EntryMapping.DEFAULT;
		}

		mappingTree.visitDstName(MappedElementKind.METHOD_ARG, 0, argMapping.targetName());
		mappingTree.visitComment(MappedElementKind.METHOD_ARG, argMapping.javadoc());
	}

	private static void writeMethodVar(EntryTreeNode<EntryMapping> varNode, VisitableMappingTree mappingTree) throws IOException {
		if (varNode.getValue() == null || varNode.getValue().equals(EntryMapping.DEFAULT)) {
			return; // Shortcut
		}

		LocalVariableEntry varEntry = ((LocalVariableEntry) varNode.getEntry());
		mappingTree.visitMethodVar(-1, varEntry.getIndex(), -1, -1, varEntry.getName());

		EntryMapping varMapping = varNode.getValue();

		if (varMapping == null) {
			varMapping = EntryMapping.DEFAULT;
		}

		mappingTree.visitDstName(MappedElementKind.METHOD_VAR, 0, varMapping.targetName());
		mappingTree.visitComment(MappedElementKind.METHOD_VAR, varMapping.javadoc());
	}

	public static EntryTree<EntryMapping> fromMappingIo(VisitableMappingTree mappingTree, ProgressListener progress, @Nullable JarIndex index) {
		EntryTree<EntryMapping> dstMappingTree = new HashEntryTree<>();
		progress.init(mappingTree.getClasses().size(), I18n.translate("progress.mappings.converting.from_mappingio"));
		int steps = 0;

		for (MappingTree.ClassMapping classMapping : mappingTree.getClasses()) {
			progress.step(steps++, classMapping.getDstName(0) != null ? classMapping.getDstName(0) : classMapping.getSrcName());
			readClass(classMapping, dstMappingTree, index);
		}

		return dstMappingTree;
	}

	private static void readClass(MappingTree.ClassMapping classMapping, EntryTree<EntryMapping> mappingTree, JarIndex index) {
		ClassEntry currentClass = new ClassEntry(classMapping.getSrcName());
		String dstName = classMapping.getDstName(0);

		if (dstName != null) {
			dstName = dstName.substring(dstName.lastIndexOf('$') + 1);
		}

		mappingTree.insert(currentClass, new EntryMapping(dstName, classMapping.getComment()));

		for (MappingTree.FieldMapping fieldMapping : classMapping.getFields()) {
			readField(fieldMapping, currentClass, mappingTree, index);
		}

		for (MappingTree.MethodMapping methodMapping : classMapping.getMethods()) {
			readMethod(methodMapping, currentClass, mappingTree);
		}
	}

	private static void readField(MappingTree.FieldMapping fieldMapping, ClassEntry parent, EntryTree<EntryMapping> mappingTree, JarIndex index) {
		String srcDesc = fieldMapping.getSrcDesc();
		FieldEntry[] fieldEntries;

		if (srcDesc != null) {
			fieldEntries = new FieldEntry[] { new FieldEntry(parent, fieldMapping.getSrcName(), new TypeDescriptor(fieldMapping.getSrcDesc())) };
		} else {
			if (index == null) return; // Enigma requires source descriptors, and without an index we can't look them up

			fieldEntries = index.getChildrenByClass().get(parent).stream()
				.filter(entry -> entry instanceof FieldEntry)
				.filter(entry -> entry.getName().equals(fieldMapping.getSrcName()))
				.toArray(FieldEntry[]::new);

			if (fieldEntries.length == 0) { // slow path for synthetics
				fieldEntries = index.getIndex(EntryIndex.class).getFields().stream()
					.filter(entry -> entry.getParent().getFullName().equals(parent.getFullName()))
					.filter(entry -> {
						if (entry.getName().equals(fieldMapping.getSrcName())) {
							return true;
						} else {
							System.out.println("Entry name: " + entry.getName() + ", mapping name: " + fieldMapping.getSrcName());
							return false;
						}
					})
					.toArray(FieldEntry[]::new);
			}

			if (fieldEntries.length == 0) return; // No target found, invalid mapping
		}

		for (FieldEntry fieldEntry : fieldEntries) {
			mappingTree.insert(fieldEntry, new EntryMapping(fieldMapping.getDstName(0), fieldMapping.getComment()));
		}
	}

	private static void readMethod(MappingTree.MethodMapping methodMapping, ClassEntry parent, EntryTree<EntryMapping> mappingTree) {
		MethodEntry currentMethod;
		mappingTree.insert(currentMethod = new MethodEntry(parent, methodMapping.getSrcName(), new MethodDescriptor(methodMapping.getSrcDesc())),
			new EntryMapping(methodMapping.getDstName(0), methodMapping.getComment()));

		for (MappingTree.MethodArgMapping argMapping : methodMapping.getArgs()) {
			readMethodArg(argMapping, currentMethod, mappingTree);
		}

		for (MappingTree.MethodVarMapping varMapping : methodMapping.getVars()) {
			readMethodVar(varMapping, currentMethod, mappingTree);
		}
	}

	private static void readMethodArg(MappingTree.MethodArgMapping argMapping, MethodEntry parent, EntryTree<EntryMapping> mappingTree) {
		String srcName = argMapping.getSrcName() != null ? argMapping.getSrcName() : "";

		mappingTree.insert(
			new LocalVariableEntry(parent, argMapping.getLvIndex(), srcName, true, null),
			new EntryMapping(argMapping.getDstName(0), argMapping.getComment()));
	}

	private static void readMethodVar(MappingTree.MethodVarMapping varMapping, MethodEntry parent, EntryTree<EntryMapping> mappingTree) {
		String srcName = varMapping.getSrcName() != null ? varMapping.getSrcName() : "";

		mappingTree.insert(
			new LocalVariableEntry(parent, varMapping.getLvIndex(), srcName, false, null),
			new EntryMapping(varMapping.getDstName(0), varMapping.getComment()));
	}
}
