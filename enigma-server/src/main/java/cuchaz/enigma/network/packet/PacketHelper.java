package cuchaz.enigma.network.packet;

import cuchaz.enigma.source.RenamableTokenType;
import cuchaz.enigma.translation.mapping.EntryChange;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.TristateChange;

import javax.annotation.Nonnull;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class PacketHelper {
	private static final int ENTRY_CLASS = 0;
	private static final int ENTRY_FIELD = 1;
	private static final int ENTRY_METHOD = 2;
	private static final int ENTRY_LOCAL_VAR = 3;
	private static final int MAX_STRING_LENGTH = 65535;

	public static Entry<?> readEntry(DataInput input) throws IOException {
		return readEntry(input, null, true);
	}

	public static Entry<?> readEntry(DataInput input, Entry<?> parent, boolean includeParent) throws IOException {
		int type = input.readUnsignedByte();

		if (includeParent && input.readBoolean()) {
			parent = readEntry(input, null, true);
		}

		String obfName = readString(input);

		EntryMapping mapping = null;
		if (input.readBoolean()) {
			mapping = readEntryMapping(input);
		}

		switch (type) {
			case ENTRY_CLASS -> {
				if (parent != null && !(parent instanceof ClassEntry)) {
					throw new IOException("Class requires class parent");
				}

				return new ClassEntry((ClassEntry) parent, obfName, mapping);
			}
			case ENTRY_FIELD -> {
				if (!(parent instanceof ClassEntry parentClass)) {
					throw new IOException("Field requires class parent");
				}

				TypeDescriptor desc = new TypeDescriptor(readString(input));
				return new FieldEntry(parentClass, obfName, desc, mapping);
			}
			case ENTRY_METHOD -> {
				if (!(parent instanceof ClassEntry parentClass)) {
					throw new IOException("Method requires class parent");
				}

				MethodDescriptor desc = new MethodDescriptor(readString(input));
				return new MethodEntry(parentClass, obfName, desc, mapping);
			}
			case ENTRY_LOCAL_VAR -> {
				if (!(parent instanceof MethodEntry parentMethod)) {
					throw new IOException("Local variable requires method parent");
				}

				int index = input.readUnsignedShort();
				boolean parameter = input.readBoolean();
				return new LocalVariableEntry(parentMethod, index, obfName, parameter, mapping);
			}
			default -> throw new IOException("Received unknown entry type " + type);
		}
	}

	public static void writeEntry(DataOutput output, Entry<?> entry) throws IOException {
		writeEntry(output, entry, true);
	}

	public static void writeEntry(DataOutput output, Entry<?> entry, boolean includeParent) throws IOException {
		// type
		if (entry instanceof ClassEntry) {
			output.writeByte(ENTRY_CLASS);
		} else if (entry instanceof FieldEntry) {
			output.writeByte(ENTRY_FIELD);
		} else if (entry instanceof MethodEntry) {
			output.writeByte(ENTRY_METHOD);
		} else if (entry instanceof LocalVariableEntry) {
			output.writeByte(ENTRY_LOCAL_VAR);
		} else {
			throw new IOException("Don't know how to serialize entry of type " + entry.getClass().getSimpleName());
		}

		// parent
		if (includeParent) {
			output.writeBoolean(entry.getParent() != null);
			if (entry.getParent() != null) {
				writeEntry(output, entry.getParent(), true);
			}
		}

		// name
		writeString(output, entry.getObfName());

		// mapping
		output.writeBoolean(entry.getMapping() != null);
		if (entry.getMapping() != null) {
			writeEntryMapping(output, entry.getMapping());
		}

		// type-specific stuff
		if (entry instanceof FieldEntry fieldEntry) {
			writeString(output, fieldEntry.getDesc().toString());
		} else if (entry instanceof MethodEntry methodEntry) {
			writeString(output, methodEntry.getDesc().toString());
		} else if (entry instanceof LocalVariableEntry localVar) {
			output.writeShort(localVar.getIndex());
			output.writeBoolean(localVar.isParameter());
		}
	}

	public static void writeEntryMapping(DataOutput output, @Nonnull EntryMapping mapping) throws IOException {
		writeString(output, mapping.targetName() == null ? "" : mapping.targetName());
		writeString(output, mapping.javadoc() == null ? "" : mapping.javadoc());

		if (mapping.tokenType() == null) {
			throw new IOException("attempted to write invalid mapping! (null token type): " + mapping);
		} else {
			output.writeShort(mapping.tokenType().ordinal());
		}
	}

	public static EntryMapping readEntryMapping(DataInput input) throws IOException {
		String targetName = readString(input);
		String javadoc = readString(input);
		short tokenType = input.readShort();

		if (tokenType == -1) {
			throw new IOException("attempted to read invalid mapping! (no token type)");
		}

		return new EntryMapping(targetName, javadoc, RenamableTokenType.get(tokenType));
	}

	public static String readString(DataInput input) throws IOException {
		int length = input.readUnsignedShort();
		byte[] bytes = new byte[length];
		input.readFully(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	public static void writeString(DataOutput output, String str) throws IOException {
		byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
		if (bytes.length > MAX_STRING_LENGTH) {
			throw new IOException("String too long, was " + bytes.length + " bytes, max " + MAX_STRING_LENGTH + " allowed");
		}

		output.writeShort(bytes.length);
		output.write(bytes);
	}

	public static EntryChange<?> readEntryChange(DataInput input) throws IOException {
		Entry<?> e = readEntry(input);
		EntryChange<?> change = EntryChange.modify(e);

		int flags = input.readUnsignedByte();
		TristateChange.Type deobfNameT = TristateChange.Type.values()[flags & 0x3];
		TristateChange.Type javadocT = TristateChange.Type.values()[flags >> 2 & 0x3];

		switch (deobfNameT) {
			case RESET -> change = change.clearDeobfName();
			case SET -> change = change.withDeobfName(readString(input));
		}

		change = switch (javadocT) {
			case RESET -> change.clearJavadoc();
			case SET -> change.withJavadoc(readString(input));
			default -> change;
		};

		return change;
	}

	public static void writeEntryChange(DataOutput output, EntryChange<?> change) throws IOException {
		writeEntry(output, change.getTarget());
		int flags = change.getDeobfName().getType().ordinal()
				| change.getJavadoc().getType().ordinal() << 2;

		output.writeByte(flags);

		if (change.getDeobfName().isSet()) {
			writeString(output, change.getDeobfName().getNewValue());
		}

		if (change.getJavadoc().isSet()) {
			writeString(output, change.getJavadoc().getNewValue());
		}
	}
}
