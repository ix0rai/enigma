package cuchaz.enigma.source.procyon;

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.Signature;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.translation.representation.entry.definition.ClassDefinition;

public class EntryParser {
	public static FieldEntry parse(FieldDefinition definition, EntryIndex index) {
		ClassEntry owner = parse(definition.getDeclaringType(), index);
		TypeDescriptor descriptor = new TypeDescriptor(definition.getErasedSignature());
		Signature signature = Signature.createTypedSignature(definition.getSignature());
		AccessFlags access = new AccessFlags(definition.getModifiers());
		return index.getField(owner, definition.getName(), descriptor, new cuchaz.enigma.translation.representation.entry.definition.FieldDefinition(access, signature));
	}

	public static ClassEntry parse(TypeDefinition def, EntryIndex index) {
		String name = def.getInternalName();
		Signature signature = Signature.createSignature(def.getSignature());
		AccessFlags access = new AccessFlags(def.getModifiers());
		ClassEntry superClass = def.getBaseType() != null ? parse(def.getBaseType(), index) : null;
		ClassEntry[] interfaces = def.getExplicitInterfaces().stream().map(reference -> parse(reference, index)).toArray(ClassEntry[]::new);
		return index.getClass(name, new ClassDefinition(access, signature, superClass, interfaces));
	}

	public static ClassEntry parse(TypeReference typeReference, EntryIndex index) {
		return index.getClass(typeReference.getInternalName());
	}

	public static MethodEntry parse(MethodDefinition definition, EntryIndex index) {
		ClassEntry classEntry = parse(definition.getDeclaringType(), index);
		MethodDescriptor descriptor = new MethodDescriptor(definition.getErasedSignature());
		Signature signature = Signature.createSignature(definition.getSignature());
		AccessFlags access = new AccessFlags(definition.getModifiers());
		return index.getMethod(classEntry, definition.getName(), descriptor, new cuchaz.enigma.translation.representation.entry.definition.MethodDefinition(access, signature));
	}

	public static TypeDescriptor parseTypeDescriptor(TypeReference type) {
		return new TypeDescriptor(type.getErasedSignature());
	}
}
