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
		return index.getField(owner, definition.getName(), descriptor);
	}

	public static ClassDefEntry parse(TypeDefinition def, EntryIndex index) {
		String name = def.getInternalName();
		return index.getClass(name);
	}

	public static ClassEntry parse(TypeReference typeReference, EntryIndex index) {
		return index.getClass(typeReference.getInternalName());
	}

	public static MethodDefEntry parse(MethodDefinition definition, EntryIndex index) {
		ClassEntry classEntry = parse(definition.getDeclaringType(), index);
		MethodDescriptor descriptor = new MethodDescriptor(definition.getErasedSignature());
		return index.getMethod(classEntry, definition.getName(), descriptor);
	}

	public static TypeDescriptor parseTypeDescriptor(TypeReference type) {
		return new TypeDescriptor(type.getErasedSignature());
	}
}
