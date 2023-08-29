package cuchaz.enigma.translation.representation.entry;

import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.source.RenamableTokenType;
import cuchaz.enigma.translation.TranslateResult;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.IdentifierValidation;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.definition.ClassDefinition;
import cuchaz.enigma.utils.validation.ValidationContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public class ClassEntry extends DefinedEntry<ClassEntry, ClassDefinition> implements Comparable<ClassEntry> {
	private final String fullName;
	private @Nullable ClassDefinition def;

	public ClassEntry(EntryIndex index, String obfName) {
		this(index, obfName, null);
	}


	public ClassEntry(EntryIndex index, String obfName, @Nullable ClassDefinition def) {
		this(getOuterClass(index, obfName), getInnerName(obfName), def, EntryMapping.DEFAULT);
	}

	public ClassEntry(@Nullable ClassEntry parent, String obfName, @Nullable ClassDefinition def) {
		this(parent, obfName, def, EntryMapping.DEFAULT);
	}

	public ClassEntry(@Nullable ClassEntry parent, String obfName, @Nullable ClassDefinition def, EntryMapping mapping) {
		super(parent, obfName, mapping);
		this.def = def;
		if (parent != null) {
			this.fullName = parent.getFullName() + "$" + this.getName();
		} else {
			this.fullName = this.getName();
		}

		if (parent == null && obfName.indexOf('.') >= 0) {
			throw new IllegalArgumentException("Class name must be in JVM format. ie, path/to/package/class$inner : " + obfName);
		}
	}

	@Override
	public Class<ClassEntry> getParentType() {
		return ClassEntry.class;
	}

	@Override
	public String getSimpleName() {
		String name = this.getName();
		int packagePos = name.lastIndexOf('/');
		if (packagePos > 0) {
			return name.substring(packagePos + 1);
		}

		return name;
	}

	@Override
	public String getFullName() {
		return this.fullName;
	}

	@Override
	public String getSourceRemapName() {
		return this.getSimpleName();
	}

	@Override
	public String getContextualName() {
		if (this.isInnerClass()) {
			return this.parent.getSimpleName() + "$" + this.getName();
		}

		return this.getSimpleName();
	}

	public ClassDefinition getDefinition() {
		return this.def;
	}

	@Override
	public TranslateResult<? extends ClassEntry> extendedTranslate(Translator translator, @Nonnull EntryMapping mapping) {
		String name = this.getName();
		if (name.charAt(0) == '[') {
			TranslateResult<TypeDescriptor> translatedName = translator.extendedTranslate(new TypeDescriptor(name));
			return translatedName.map(desc -> new ClassEntry(this.parent, this.obfName, this.def, new EntryMapping(desc.toString())));
		}

		return TranslateResult.of(
				mapping.targetName() == null ? RenamableTokenType.OBFUSCATED : RenamableTokenType.DEOBFUSCATED,
				new ClassEntry(this.parent, this.obfName, this.def, mapping)
		);
	}

	@Override
	public ClassEntry getContainingClass() {
		return this;
	}

	@Override
	public int hashCode() {
		return this.obfName.hashCode();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof ClassEntry entry && this.equals(entry);
	}

	public boolean equals(ClassEntry other) {
		return other != null && Objects.equals(this.parent, other.parent) && this.getObfName().equals(other.getObfName());
	}

	@Override
	public boolean canConflictWith(Entry<?> entry) {
		return entry instanceof ClassEntry;
	}

	@Override
	public boolean canShadow(Entry<?> entry) {
		return false;
	}

	@Override
	public void validateName(ValidationContext vc, String name) {
		IdentifierValidation.validateClassName(vc, name, this.isInnerClass());
	}

	@Override
	public String toString() {
		return this.getFullName();
	}

	public String getPackageName() {
		return getParentPackage(this.fullName);
	}

	/**
	 * Returns whether this class entry has a parent, and therefore is an inner class.
	 */
	public boolean isInnerClass() {
		return this.parent != null;
	}

	@Nullable
	public ClassEntry getOuterClass() {
		return this.parent;
	}

	@Nonnull
	public ClassEntry getOutermostClass() {
		if (this.parent == null) {
			return this;
		}

		return this.parent.getOutermostClass();
	}

	public boolean isJre() {
		String packageName = this.getPackageName();
		return packageName != null && (packageName.startsWith("java/") || packageName.startsWith("javax/"));
	}

	public static String getParentPackage(String name) {
		int pos = name.lastIndexOf('/');
		if (pos > 0) {
			return name.substring(0, pos);
		}

		return null;
	}

	public static String getNameInPackage(String name) {
		int pos = name.lastIndexOf('/');

		if (pos == name.length() - 1) {
			return "(empty)";
		}

		if (pos > 0) {
			return name.substring(pos + 1);
		}

		return name;
	}

	@Nullable
	public static ClassEntry getOuterClass(EntryIndex index, String obfName) {
		String outerObfName = getOuterClassName(obfName);

		if (outerObfName != null) {
			return index.getClass(outerObfName);
		}

		return null;
	}

	private static String getOuterClassName(String name) {
		if (name != null) {
			if (name.charAt(0) == '[') {
				return null;
			}

			int index = name.lastIndexOf('$');
			if (index >= 0) {
				return name.substring(0, index);
			}
		}

		return null;
	}

	public static String getInnerName(String name) {
		if (name.charAt(0) == '[') {
			return name;
		}

		int innerClassPos = name.lastIndexOf('$');
		if (innerClassPos > 0) {
			return name.substring(innerClassPos + 1);
		}

		return name;
	}

	@Override
	public int compareTo(ClassEntry entry) {
		String name = this.getFullName();
		String otherFullName = entry.getFullName();

		if (name.length() != otherFullName.length()) {
			return name.length() - otherFullName.length();
		}

		return name.compareTo(otherFullName);
	}

	@Override
	public void setDefinition(@Nonnull ClassDefinition definition) {
		this.def = definition;
	}
}
