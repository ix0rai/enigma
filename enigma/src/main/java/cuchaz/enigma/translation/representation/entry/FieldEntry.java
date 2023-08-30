package cuchaz.enigma.translation.representation.entry;

import com.google.common.base.Preconditions;
import cuchaz.enigma.source.RenamableTokenType;
import cuchaz.enigma.translation.TranslateResult;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.Signature;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.definition.ClassDefinition;
import cuchaz.enigma.translation.representation.entry.definition.FieldDefinition;
import cuchaz.enigma.translation.representation.entry.definition.MethodDefinition;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class FieldEntry extends DefinedEntry<ClassEntry, FieldDefinition> implements Comparable<FieldEntry> {
	private final TypeDescriptor desc;

	public FieldEntry(ClassEntry parent, String obfName, TypeDescriptor desc) {
		this(parent, obfName, desc, null, EntryMapping.DEFAULT);
	}

	public FieldEntry(ClassEntry parent, String obfName, TypeDescriptor desc, @Nullable FieldDefinition def) {
		this(parent, obfName, desc, def, EntryMapping.DEFAULT);
	}

	public FieldEntry(ClassEntry parent, String obfName, TypeDescriptor desc, @Nullable FieldDefinition definition, EntryMapping mapping) {
		super(parent, obfName, definition, mapping);

		Preconditions.checkNotNull(parent, "Owner cannot be null");
		Preconditions.checkNotNull(desc, "Field descriptor cannot be null");

		this.desc = desc;
	}

	public static FieldEntry parse(ClassEntry owner, int access, String obfName, String desc, String signature) {
		return new FieldEntry(owner, obfName, new TypeDescriptor(desc), new FieldDefinition(new AccessFlags(access), Signature.createTypedSignature(signature)), EntryMapping.DEFAULT);
	}

	@Override
	public Class<ClassEntry> getParentType() {
		return ClassEntry.class;
	}

	public TypeDescriptor getDesc() {
		return this.desc;
	}

	@Override
	protected TranslateResult<FieldEntry> extendedTranslate(Translator translator, @Nonnull EntryMapping mapping) {
		return TranslateResult.of(
				mapping.targetName() == null ? RenamableTokenType.OBFUSCATED : RenamableTokenType.DEOBFUSCATED,
				new FieldEntry(this.parent, this.obfName, translator.translate(this.desc), this.definition, mapping)
		);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.parent, this.getObfName(), this.desc);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof FieldEntry fieldEntry && this.equals(fieldEntry);
	}

	public boolean equals(FieldEntry other) {
		return this.parent.equals(other.parent) && this.getObfName().equals(other.getObfName()) && this.desc.equals(other.desc);
	}

	@Override
	public boolean canConflictWith(Entry<?> entry) {
		return entry instanceof FieldEntry field && this.getParent().equals(field.getParent());
	}

	@Override
	public boolean canShadow(Entry<?> entry) {
		return entry instanceof FieldEntry;
	}

	@Override
	public String toString() {
		return this.getFullName() + ":" + this.desc;
	}

	@Override
	public int compareTo(FieldEntry entry) {
		return (this.getName() + this.desc.toString()).compareTo(entry.getName() + entry.desc.toString());
	}
}
