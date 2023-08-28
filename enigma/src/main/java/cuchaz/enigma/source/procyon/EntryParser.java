package cuchaz.enigma.source.procyon;

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.Signature;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassDefEntry;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldDefEntry;
import cuchaz.enigma.translation.representation.entry.MethodDefEntry;

public class EntryParser {
	public static FieldDefEntry parse(FieldDefinition definition, EntryIndex index) {
		ClassEntry owner = parse(definition.getDeclaringType(), index);
		TypeDescriptor descriptor = new TypeDescriptor(definition.getErasedSignature());
		Signature signature = Signature.createTypedSignature(definition.getSignature());
		AccessFlags access = new AccessFlags(definition.getModifiers());
		return index.getFieldDef(owner, definition.getName(), descriptor, signature, access);
	}

	public static ClassDefEntry parse(TypeDefinition def, EntryIndex index) {
		String name = def.getInternalName();
		Signature signature = Signature.createSignature(def.getSignature());
		AccessFlags access = new AccessFlags(def.getModifiers());
		ClassEntry superClass = def.getBaseType() != null ? parse(def.getBaseType(), index) : null;
		ClassEntry[] interfaces = def.getExplicitInterfaces().stream().map(reference -> parse(reference, index)).toArray(ClassEntry[]::new);
		return index.getClassDef(name, signature, access, superClass, interfaces);
	}

	public static ClassEntry parse(TypeReference typeReference, EntryIndex index) {
		return new ClassEntry(typeReference.getInternalName());
	}

	public static MethodDefEntry parse(MethodDefinition definition, EntryIndex index) {
		ClassEntry classEntry = parse(definition.getDeclaringType(), index);
		MethodDescriptor descriptor = new MethodDescriptor(definition.getErasedSignature());
		Signature signature = Signature.createSignature(definition.getSignature());
		AccessFlags access = new AccessFlags(definition.getModifiers());
		return index.getMethodDef(classEntry, definition.getName(), descriptor, signature, access);
	}

	public static TypeDescriptor parseTypeDescriptor(TypeReference type) {
		return new TypeDescriptor(type.getErasedSignature());
	}
}
