package cuchaz.enigma.analysis.index;

import com.strobel.assembler.metadata.TypeDefinition;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.Signature;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassDefEntry;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldDefEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.LocalVariableDefEntry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import cuchaz.enigma.translation.representation.entry.MethodDefEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Triplet;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

// todo: actually index some local variables
// todo: there should not be twelve maps here lol
public class EntryIndex implements JarIndexer {
	private final Map<String, ClassDefEntry> obfToClassDef = new HashMap<>();
	private final Map<Triplet<ClassEntry, String, TypeDescriptor>, FieldDefEntry> obfToFieldDef = new HashMap<>();
	private final Map<Triplet<ClassEntry, String, MethodDescriptor>, MethodDefEntry> obfToMethodDef = new HashMap<>();
	private final Map<Pair<MethodEntry, Integer>, LocalVariableDefEntry> obfToVariableDef = new HashMap<>();
	private final Map<String, ClassEntry> obfToClass = new HashMap<>();
	private final Map<Triplet<ClassEntry, String, TypeDescriptor>, FieldEntry> obfToField = new HashMap<>();
	private final Map<Triplet<ClassEntry, String, MethodDescriptor>, MethodEntry> obfToMethod = new HashMap<>();
	private final Map<Pair<MethodEntry, Integer>, LocalVariableEntry> obfToVariable = new HashMap<>();

	private final Map<ClassEntry, AccessFlags> classes = new HashMap<>();
	private final Map<FieldEntry, AccessFlags> fields = new HashMap<>();
	private final Map<MethodEntry, AccessFlags> methods = new HashMap<>();
	private final Map<ClassEntry, ClassDefEntry> definitions = new HashMap<>();

	@Override
	public void indexClass(ClassDefEntry classEntry) {
		this.definitions.put(classEntry, classEntry);
		this.classes.put(classEntry, classEntry.getAccess());
		this.obfToClassDef.put(classEntry.getObfName(), classEntry);
		this.indexRawClass(classEntry);
	}

	private void indexRawClass(ClassEntry classEntry) {
		this.obfToClass.put(classEntry.getObfName(), classEntry);
	}

	@Override
	public void indexMethod(MethodDefEntry methodEntry) {
		this.methods.put(methodEntry, methodEntry.getAccess());
		this.obfToMethodDef.put(new Triplet<>(methodEntry.getParent(), methodEntry.getObfName(), methodEntry.getDesc()), methodEntry);
		this.indexRawMethod(methodEntry);
	}

	private void indexRawMethod(MethodEntry methodEntry) {
		this.obfToMethod.put(new Triplet<>(methodEntry.getParent(), methodEntry.getObfName(), methodEntry.getDesc()), methodEntry);
	}

	@Override
	public void indexField(FieldDefEntry fieldEntry) {
		this.fields.put(fieldEntry, fieldEntry.getAccess());
		this.obfToFieldDef.put(new Triplet<>(fieldEntry.getParent(), fieldEntry.getObfName(), fieldEntry.getDesc()), fieldEntry);
		this.indexRawField(fieldEntry);
	}

	private void indexRawField(FieldEntry fieldEntry) {
		this.obfToField.put(new Triplet<>(fieldEntry.getParent(), fieldEntry.getObfName(), fieldEntry.getDesc()), fieldEntry);
	}

	public void indexLocalVariable(LocalVariableDefEntry variableEntry) {
		this.obfToVariableDef.put(new Pair<>(variableEntry.getParent(), variableEntry.getIndex()), variableEntry);
		this.indexRawLocalVariable(variableEntry);
	}

	private void indexRawLocalVariable(LocalVariableEntry variableEntry) {
		this.obfToVariable.put(new Pair<>(variableEntry.getParent(), variableEntry.getIndex()), variableEntry);
	}

	public ClassEntry getClass(String obfName) {
		ClassEntry indexed = this.getClassDefNullable(obfName);
		if (indexed == null) {
			indexed = this.getRawClass(obfName);
		}

		if (indexed == null) {
			ClassEntry entry = new ClassEntry(obfName);
			this.indexRawClass(entry);
			return entry;
		}

		return indexed;
	}

	public MethodEntry getMethod(ClassEntry parent, String obfName, String descriptor) {
		return this.getMethod(parent, obfName, new MethodDescriptor(descriptor));
	}

	public MethodEntry getMethod(ClassEntry parent, String obfName, MethodDescriptor descriptor) {
		MethodEntry indexed = this.getMethodDefNullable(parent, obfName, descriptor);
		if (indexed == null) {
			indexed = this.getRawMethodNullable(parent, obfName, descriptor);
		}

		if (indexed == null) {
			MethodEntry entry = new MethodEntry(parent, obfName, descriptor);
			this.indexRawMethod(entry);
			return entry;
		}

		return indexed;
	}

	public FieldEntry getField(ClassEntry parent, String obfName, String descriptor) {
		return this.getField(parent, obfName, new TypeDescriptor(descriptor));
	}

	public FieldEntry getField(ClassEntry parent, String obfName, TypeDescriptor descriptor) {
		FieldEntry indexed = this.getFieldDefNullable(parent, obfName, descriptor);
		if (indexed == null) {
			indexed = this.getRawFieldNullable(parent, obfName, descriptor);
		}

		if (indexed == null) {
			FieldEntry entry = new FieldEntry(parent, obfName, descriptor);
			this.indexRawField(entry);
			return entry;
		}

		return indexed;
	}

	public LocalVariableEntry getLocalVariable(MethodEntry parent, int index, String obfName, boolean parameter) {
		LocalVariableEntry indexed = this.getLocalVariableDefNullable(parent, index);
		if (indexed == null) {
			indexed = this.getRawLocalVariableNullable(parent, index);
		}

		if (indexed == null) {
			LocalVariableEntry entry = new LocalVariableEntry(parent, index, obfName, parameter, EntryMapping.DEFAULT);
			this.indexRawLocalVariable(entry);
			return entry;
		}

		return indexed;
	}

	public ClassDefEntry getClassDef(String obfName, Signature signature, AccessFlags access, ClassEntry superClass, ClassEntry[] interfaces) {
		ClassDefEntry indexed = this.getClassDefNullable(obfName);
		if (indexed == null) {
			ClassDefEntry entry = new ClassDefEntry(obfName, signature, access, superClass, interfaces);
			this.indexClass(entry);
			return entry;
		}

		return indexed;
	}

	public MethodDefEntry getMethodDef(ClassEntry parent, String obfName, MethodDescriptor descriptor, Signature signature, AccessFlags accessFlags) {
		MethodDefEntry indexed = this.getMethodDefNullable(parent, obfName, descriptor);
		if (indexed == null) {
			MethodDefEntry entry = new MethodDefEntry(parent, obfName, descriptor, signature, accessFlags, EntryMapping.DEFAULT);
			this.indexMethod(entry);
			return entry;
		}

		return indexed;
	}

	public FieldDefEntry getFieldDef(ClassEntry owner, String obfName, TypeDescriptor descriptor, Signature signature, AccessFlags accessFlags) {
		FieldDefEntry indexed = this.getFieldDefNullable(owner, obfName, descriptor);
		if (indexed == null) {
			FieldDefEntry entry = new FieldDefEntry(owner, obfName, descriptor, signature, accessFlags);
			this.indexField(entry);
			return entry;
		}

		return indexed;
	}

	public LocalVariableDefEntry getLocalVariableDef(MethodEntry parent, int index, String obfName, boolean parameter, TypeDescriptor descriptor) {
		LocalVariableDefEntry indexed = this.getLocalVariableDefNullable(parent, index);
		if (indexed == null) {
			LocalVariableDefEntry entry = new LocalVariableDefEntry(parent, index, obfName, parameter, descriptor, EntryMapping.DEFAULT);
			this.indexLocalVariable(entry);
			return entry;
		}

		return indexed;
	}

	@Nullable
	public ClassDefEntry getClassDefNullable(String obfName) {
		return this.obfToClassDef.get(obfName);
	}

	@Nullable
	public ClassEntry getRawClass(String obfName) {
		return this.obfToClass.get(obfName);
	}

	@Nullable
	public MethodDefEntry getMethodDefNullable(ClassEntry parent, String obfName, String descriptor) {
		return this.getMethodDefNullable(parent, obfName, new MethodDescriptor(descriptor));
	}

	@Nullable
	public MethodDefEntry getMethodDefNullable(ClassEntry parent, String obfName, MethodDescriptor descriptor) {
		return this.obfToMethodDef.get(new Triplet<>(parent, obfName, descriptor));
	}

	@Nullable
	public MethodEntry getRawMethodNullable(ClassEntry parent, String obfName, MethodDescriptor descriptor) {
		return this.obfToMethod.get(new Triplet<>(parent, obfName, descriptor));
	}

	@Nullable
	public FieldDefEntry getFieldDefNullable(ClassEntry parent, String obfName, String descriptor) {
		return this.obfToFieldDef.get(new Triplet<>(parent, obfName, new TypeDescriptor(descriptor)));
	}

	@Nullable
	public FieldDefEntry getFieldDefNullable(ClassEntry parent, String obfName, TypeDescriptor descriptor) {
		return this.obfToFieldDef.get(new Triplet<>(parent, obfName, descriptor));
	}

	@Nullable
	public FieldEntry getRawFieldNullable(ClassEntry parent, String obfName, TypeDescriptor descriptor) {
		return this.obfToField.get(new Triplet<>(parent, obfName, descriptor));
	}

	@Nullable
	public LocalVariableDefEntry getLocalVariableDefNullable(MethodEntry parent, int index) {
		return this.obfToVariableDef.get(new Pair<>(parent, index));
	}

	@Nullable
	public LocalVariableEntry getRawLocalVariableNullable(MethodEntry parent, int index) {
		return this.obfToVariable.get(new Pair<>(parent, index));
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

	public Collection<LocalVariableDefEntry> getLocalVariables() {
		return this.obfToVariableDef.values();
	}

	@Override
	public String getTranslationKey() {
		return "progress.jar.indexing.process.entries";
	}
}
