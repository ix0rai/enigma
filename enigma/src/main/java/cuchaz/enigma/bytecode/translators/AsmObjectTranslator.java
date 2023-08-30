package cuchaz.enigma.bytecode.translators;

import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class AsmObjectTranslator {
	public static Type translateType(EntryIndex index, Type type) {
		String descString = type.getDescriptor();
		switch (type.getSort()) {
			case Type.OBJECT -> {
				ClassEntry classEntry = index.getClass(type.getInternalName());
				return Type.getObjectType(classEntry.getFullName());
			}
			case Type.ARRAY -> {
				// todo translation
				TypeDescriptor descriptor = new TypeDescriptor(descString);
				return Type.getType(descriptor.toString());
			}
			case Type.METHOD -> {
				// todo translation
				MethodDescriptor descriptor = new MethodDescriptor(descString);
				return Type.getMethodType(descriptor.toString());
			}
		}

		return type;
	}

	public static Handle translateHandle(EntryIndex index, Handle handle) {
		final boolean isFieldHandle = handle.getTag() <= Opcodes.H_PUTSTATIC;
		return isFieldHandle ? translateFieldHandle(index, handle) : translateMethodHandle(index, handle);
	}

	private static Handle translateMethodHandle(EntryIndex index, Handle handle) {
		MethodEntry entry = index.getMethod(index.getClass(handle.getOwner()), handle.getName(), new MethodDescriptor(handle.getDesc()));
		ClassEntry ownerClass = entry.getParent();
		return new Handle(handle.getTag(), ownerClass.getFullName(), entry.getName(), entry.getDesc().toString(), handle.isInterface());
	}

	private static Handle translateFieldHandle(EntryIndex index, Handle handle) {
		FieldEntry entry = index.getField(index.getClass(handle.getOwner()), handle.getName(), new TypeDescriptor(handle.getDesc()));
		ClassEntry ownerClass = entry.getParent();
		return new Handle(handle.getTag(), ownerClass.getFullName(), entry.getName(), entry.getDesc().toString(), handle.isInterface());
	}

	public static Object translateValue(EntryIndex index, Object value) {
		if (value instanceof Type type) {
			return translateType(index, type);
		} else if (value instanceof Handle handle) {
			return translateHandle(index, handle);
		}

		return value;
	}
}
