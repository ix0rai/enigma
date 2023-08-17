package cuchaz.enigma.analysis.index;

import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassDefEntry;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldDefEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import cuchaz.enigma.translation.representation.entry.MethodDefEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Triplet;
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class EntryIndex implements JarIndexer {
	private final Map<String, ClassDefEntry> obfToClass = new HashMap<>();
	private final Map<Triplet<ClassEntry, String, TypeDescriptor>, FieldDefEntry> obfToField = new HashMap<>();
	private final Map<Triplet<ClassEntry, String, MethodDescriptor>, MethodDefEntry> obfToMethod = new HashMap<>();

	private final Map<ClassEntry, AccessFlags> classes = new HashMap<>();
	private final Map<FieldEntry, AccessFlags> fields = new HashMap<>();
	private final Map<MethodEntry, AccessFlags> methods = new HashMap<>();
	private final Map<ClassEntry, ClassDefEntry> definitions = new HashMap<>();

	@Override
	public void indexClass(ClassDefEntry classEntry) {
		this.definitions.put(classEntry, classEntry);
		this.classes.put(classEntry, classEntry.getAccess());
		this.obfToClass.put(classEntry.getObfName(), classEntry);
	}

	@Override
	public void indexMethod(MethodDefEntry methodEntry) {
		this.methods.put(methodEntry, methodEntry.getAccess());
		this.obfToMethod.put(new Triplet<>(methodEntry.getParent(), methodEntry.getObfName(), methodEntry.getDesc()), methodEntry);
	}

	@Override
	public void indexField(FieldDefEntry fieldEntry) {
		this.fields.put(fieldEntry, fieldEntry.getAccess());
		this.obfToField.put(new Triplet<>(fieldEntry.getParent(), fieldEntry.getObfName(), fieldEntry.getDesc()), fieldEntry);
	}

	public ClassDefEntry getClass(String obfName) {
		return this.obfToClass.get(obfName);
	}

	public MethodDefEntry getMethod(ClassEntry parent, String obfName, String descriptor) {
		return this.getMethod(parent, obfName, new MethodDescriptor(descriptor));
	}

	public MethodDefEntry getMethod(ClassEntry parent, String obfName, MethodDescriptor descriptor) {
		return this.obfToMethod.get(new Triplet<>(parent, obfName, descriptor));
	}

	public FieldDefEntry getField(ClassEntry parent, String obfName, String descriptor) {
		return this.obfToField.get(new Triplet<>(parent, obfName, new TypeDescriptor(descriptor)));
	}

	public FieldDefEntry getField(ClassEntry parent, String obfName, TypeDescriptor descriptor) {
		return this.obfToField.get(new Triplet<>(parent, obfName, descriptor));
	}

	public boolean hasClass(ClassEntry entry) {
		return this.classes.containsKey(entry);
	}

	public boolean hasMethod(MethodEntry entry) {
		return this.methods.containsKey(entry);
	}

	public boolean hasField(FieldEntry entry) {
		return this.fields.containsKey(entry);
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

	@Nullable
	public AccessFlags getMethodAccess(MethodEntry entry) {
		return this.methods.get(entry);
	}

	@Nullable
	public AccessFlags getFieldAccess(FieldEntry entry) {
		return this.fields.get(entry);
	}

	@Nullable
	public AccessFlags getClassAccess(ClassEntry entry) {
		return this.classes.get(entry);
	}

	@Nullable
	public AccessFlags getEntryAccess(Entry<?> entry) {
		if (entry instanceof MethodEntry methodEntry) {
			return this.getMethodAccess(methodEntry);
		} else if (entry instanceof FieldEntry fieldEntry) {
			return this.getFieldAccess(fieldEntry);
		} else if (entry instanceof LocalVariableEntry localVariableEntry) {
			return this.getMethodAccess(localVariableEntry.getParent());
		} else if (entry instanceof ClassEntry classEntry) {
			return this.getClassAccess(classEntry);
		}

		return null;
	}

	public ClassDefEntry getDefinition(ClassEntry entry) {
		return this.definitions.get(entry);
	}

	public Collection<ClassEntry> getClasses() {
		return this.classes.keySet();
	}

	public Collection<MethodEntry> getMethods() {
		return this.methods.keySet();
	}

	public Collection<FieldEntry> getFields() {
		return this.fields.keySet();
	}

	@Override
	public String getTranslationKey() {
		return "progress.jar.indexing.process.entries";
	}
}
