package cuchaz.enigma.analysis.index;

import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.translation.representation.entry.definition.ClassDefinition;
import cuchaz.enigma.translation.representation.entry.definition.FieldDefinition;
import cuchaz.enigma.translation.representation.entry.definition.MethodDefinition;
import cuchaz.enigma.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Triplet;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntryIndex implements JarIndexer {
	private final Map<String, ClassEntry> obfToClass = new HashMap<>();
	private final Map<Triplet<ClassEntry, String, TypeDescriptor>, FieldEntry> obfToField = new HashMap<>();
	private final Map<Triplet<ClassEntry, String, MethodDescriptor>, MethodEntry> obfToMethod = new HashMap<>();
	private final Map<Pair<MethodEntry, Integer>, LocalVariableEntry> obfToVariable = new HashMap<>();

	private final List<ClassEntry> classes = new ArrayList<>();
	private final List<MethodEntry> methods = new ArrayList<>();
	private final List<FieldEntry> fields = new ArrayList<>();

	@Override
	public void indexClass(ClassEntry classEntry) {
		if (!classEntry.isJre() && !this.classes.contains(classEntry)) {
			this.obfToClass.put(classEntry.getObfName(), classEntry);
			this.classes.add(classEntry);
		}
	}

	@Override
	public void indexMethod(MethodEntry methodEntry) {
		if (!this.methods.contains(methodEntry)) {
			this.obfToMethod.put(new Triplet<>(methodEntry.getParent(), methodEntry.getObfName(), methodEntry.getDesc()), methodEntry);
			this.methods.add(methodEntry);
		}
	}

	@Override
	public void indexField(FieldEntry fieldEntry) {
		if (!this.fields.contains(fieldEntry)) {
			this.obfToField.put(new Triplet<>(fieldEntry.getParent(), fieldEntry.getObfName(), fieldEntry.getDesc()), fieldEntry);
			this.fields.add(fieldEntry);
		}
	}

	public void indexLocalVariable(LocalVariableEntry variableEntry) {
		if (!this.obfToVariable.containsValue(variableEntry)) {
			this.obfToVariable.put(new Pair<>(variableEntry.getParent(), variableEntry.getIndex()), variableEntry);
		}
	}

	public ClassEntry getClass(String obfName) {
		return this.getClass(obfName, null);
	}

	public ClassEntry getClass(String obfName, ClassDefinition definition) {
		ClassEntry indexed = this.getClassNullable(obfName);

		if (indexed == null) {
			ClassEntry entry = new ClassEntry(this, obfName, definition);
			this.indexClass(entry);
			return entry;
		}

		if (indexed.getDefinition() == null) {
			indexed.setDefinition(definition);
		}

		return indexed;
	}

	public MethodEntry getMethod(ClassEntry parent, String obfName, String descriptor) {
		return this.getMethod(parent, obfName, new MethodDescriptor(descriptor));
	}

	public MethodEntry getMethod(ClassEntry parent, String obfName, MethodDescriptor descriptor) {
		return this.getMethod(parent, obfName, descriptor, null);
	}

	public MethodEntry getMethod(ClassEntry parent, String obfName, MethodDescriptor descriptor, MethodDefinition definition) {
		MethodEntry indexed = this.getMethodNullable(parent, obfName, descriptor);

		if (indexed == null) {
			MethodEntry entry = new MethodEntry(parent, obfName, descriptor, definition);
			this.indexMethod(entry);
			return entry;
		}

		return indexed;
	}

	public FieldEntry getField(ClassEntry parent, String obfName, String descriptor) {
		return this.getField(parent, obfName, new TypeDescriptor(descriptor));
	}

	public FieldEntry getField(ClassEntry parent, String obfName, TypeDescriptor descriptor) {
		return this.getField(parent, obfName, descriptor, null);
	}

	public FieldEntry getField(ClassEntry parent, String obfName, TypeDescriptor descriptor, FieldDefinition definition) {
		FieldEntry indexed = this.getFieldNullable(parent, obfName, descriptor);

		if (indexed == null) {
			FieldEntry entry = new FieldEntry(parent, obfName, descriptor, definition);
			this.indexField(entry);
			return entry;
		}

		return indexed;
	}

	public LocalVariableEntry getLocalVariable(MethodEntry parent, int index, String obfName, boolean parameter) {
		LocalVariableEntry indexed = this.getLocalVariableNullable(parent, index);

		if (indexed == null) {
			LocalVariableEntry entry = new LocalVariableEntry(parent, index, obfName, parameter, EntryMapping.DEFAULT);
			this.indexLocalVariable(entry);
			return entry;
		}

		return indexed;
	}

	@Nullable
	public ClassEntry getClassNullable(String obfName) {
		return this.obfToClass.get(obfName);
	}

	@Nullable
	public MethodEntry getMethodNullable(ClassEntry parent, String obfName, MethodDescriptor descriptor) {
		return this.obfToMethod.get(new Triplet<>(parent, obfName, descriptor));
	}

	@Nullable
	public FieldEntry getFieldNullable(ClassEntry parent, String obfName, TypeDescriptor descriptor) {
		return this.obfToField.get(new Triplet<>(parent, obfName, descriptor));
	}

	@Nullable
	public LocalVariableEntry getLocalVariableNullable(MethodEntry parent, int index) {
		return this.obfToVariable.get(new Pair<>(parent, index));
	}

	public boolean hasClass(ClassEntry entry) {
		return this.classes.contains(entry);
	}

	public boolean hasMethod(MethodEntry entry) {
		return this.methods.contains(entry);
	}

	public boolean hasField(FieldEntry entry) {
		return this.fields.contains(entry);
	}

	public boolean hasEntry(Entry<?> entry) {
		if (entry instanceof ClassEntry classEntry) {
			return this.hasClass(classEntry);
		} else if (entry instanceof MethodEntry methodEntry) {
			return this.hasMethod(methodEntry);
		} else if (entry instanceof FieldEntry fieldEntry) {
			return this.hasField(fieldEntry);
		} else if (entry instanceof LocalVariableEntry localVariableEntry) {
			return this.hasMethod(localVariableEntry.getParent());
		}

		return false;
	}

	public Collection<ClassEntry> getClasses() {
		return this.classes;
	}

	public Collection<MethodEntry> getMethods() {
		return this.methods;
	}

	public Collection<FieldEntry> getFields() {
		return this.fields;
	}

	@Override
	public String getTranslationKey() {
		return "progress.jar.indexing.process.entries";
	}
}
