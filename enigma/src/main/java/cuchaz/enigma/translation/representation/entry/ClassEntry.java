package cuchaz.enigma.translation.representation.entry;

import cuchaz.enigma.source.RenamableTokenType;
import cuchaz.enigma.translation.TranslateResult;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.IdentifierValidation;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.utils.validation.ValidationContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public class ClassEntry extends ParentedEntry<ClassEntry> implements Comparable<ClassEntry> {
	private final String fullName;

	public ClassEntry(String className, String obfName) {
		this(getOuterClass(className, obfName), getInnerName(className), obfName, null);
	}

	public ClassEntry(@Nullable ClassEntry parent, String className, String obfName) {
		this(parent, className, obfName, null);
	}

	public ClassEntry(@Nullable ClassEntry parent, String className, String obfName, @Nullable EntryMapping mapping) {
		super(parent, className, obfName, mapping);
		if (parent != null) {
			this.fullName = parent.getFullName() + "$" + this.name;
		} else {
			this.fullName = this.name;
		}

		if (parent == null && className.indexOf('.') >= 0) {
			throw new IllegalArgumentException("Class name must be in JVM format. ie, path/to/package/class$inner : " + className);
		}
	}

	@Override
	public Class<ClassEntry> getParentType() {
		return ClassEntry.class;
	}

	@Override
	public String getSimpleName() {
		int packagePos = this.name.lastIndexOf('/');
		if (packagePos > 0) {
			return this.name.substring(packagePos + 1);
		}

		return this.name;
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
			return this.parent.getSimpleName() + "$" + this.name;
		}

		return this.getSimpleName();
	}

	@Override
	public TranslateResult<? extends ClassEntry> extendedTranslate(Translator translator, @Nonnull EntryMapping mapping) {
		if (this.name.charAt(0) == '[') {
			TranslateResult<TypeDescriptor> translatedName = translator.extendedTranslate(new TypeDescriptor(this.name));
			return translatedName.map(desc -> new ClassEntry(this.parent, this.obfName, desc.toString()));
		}

		String translatedName = mapping.targetName() != null ? mapping.targetName() : this.name;
		return TranslateResult.of(
				mapping.targetName() == null ? RenamableTokenType.OBFUSCATED : RenamableTokenType.DEOBFUSCATED,
				new ClassEntry(this.parent, translatedName, this.obfName, mapping)
		);
	}

	@Override
	public ClassEntry getContainingClass() {
		return this;
	}

	@Override
	public int hashCode() {
		return this.fullName.hashCode();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof ClassEntry entry && this.equals(entry);
	}

	public boolean equals(ClassEntry other) {
		return other != null && Objects.equals(this.parent, other.parent) && this.name.equals(other.name);
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
	public ClassEntry withName(String name, RenamableTokenType tokenType) {
		return new ClassEntry(this.parent, name, this.obfName, new EntryMapping(name, this.getJavadocs(), tokenType));
	}

	@Override
	public ClassEntry withParent(ClassEntry parent) {
		return new ClassEntry(parent, this.name, this.obfName, this.getMapping());
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
	public static ClassEntry getOuterClass(String name, String obfName) {
		String outerName = getOuterClassName(name);
		String outerObfName = getOuterClassName(obfName);

		if (outerName != null && outerObfName != null) {
			return new ClassEntry(outerName, outerObfName);
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
}
