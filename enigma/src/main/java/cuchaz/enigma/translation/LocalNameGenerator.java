package cuchaz.enigma.translation;

import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.translation.mapping.IdentifierValidation;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import org.tinylog.Logger;

import java.util.Collection;
import java.util.Locale;

public class LocalNameGenerator {
	public static String generateArgumentName(EntryIndex entryIndex, int index, TypeDescriptor desc, Collection<TypeDescriptor> arguments) {
		boolean uniqueType = arguments.stream().filter(desc::equals).count() <= 1;
		String translatedName;
		int nameIndex = index + 1;
		StringBuilder nameBuilder = new StringBuilder(getTypeName(entryIndex, desc));
		if (!uniqueType || IdentifierValidation.isReservedMethodName(nameBuilder.toString())) {
			nameBuilder.append(nameIndex);
		}

		translatedName = nameBuilder.toString();
		return translatedName;
	}

	public static String generateLocalVariableName(EntryIndex entryIndex, int index, TypeDescriptor desc) {
		int nameIndex = index + 1;
		return getTypeName(entryIndex, desc) + nameIndex;
	}

	private static String getTypeName(EntryIndex index, TypeDescriptor desc) {
		// Unfortunately each of these have different name getters, so they have different code paths
		if (desc.isPrimitive()) {
			TypeDescriptor.Primitive argCls = desc.getPrimitive();
			return argCls.name().toLowerCase(Locale.ROOT);
		} else if (desc.isArray()) {
			// List types would require this whole block again, so just go with aListx
			return "arr";
		} else if (desc.isType()) {
			String typeName = desc.getTypeEntry(index).getSimpleName().replace("$", "");
			typeName = typeName.substring(0, 1).toLowerCase(Locale.ROOT) + typeName.substring(1);
			return typeName;
		} else {
			Logger.error("Encountered invalid argument type descriptor {}", desc);
			return "var";
		}
	}
}
