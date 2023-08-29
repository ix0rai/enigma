package cuchaz.enigma.translation.representation.entry;

import com.google.common.base.Preconditions;
import cuchaz.enigma.source.RenamableTokenType;
import cuchaz.enigma.translation.TranslateResult;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.definition.Definition;
import cuchaz.enigma.translation.representation.entry.definition.MethodDefinition;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class LocalVariableEntry extends ParentedEntry<MethodEntry> implements Comparable<LocalVariableEntry> {
	protected final int index;
	protected final boolean parameter;
	protected @Nullable TypeDescriptor desc;

	public LocalVariableEntry(MethodEntry parent, int index, String obfName, boolean parameter, EntryMapping mapping) {
		this(parent, index, obfName, parameter, null, mapping);
	}

	public LocalVariableEntry(MethodEntry parent, int index, String obfName, boolean parameter, @Nullable TypeDescriptor desc, EntryMapping mapping) {
		super(parent, obfName, mapping);

		Preconditions.checkNotNull(parent, "Variable owner cannot be null");
		Preconditions.checkArgument(index >= 0, "Index must be positive");

		this.index = index;
		this.parameter = parameter;
		this.desc = desc;
	}

	@Override
	public Class<MethodEntry> getParentType() {
		return MethodEntry.class;
	}

	public boolean isParameter() {
		return this.parameter;
	}

	public int getIndex() {
		return this.index;
	}

	@Nullable
	public TypeDescriptor getDesc() {
		return this.desc;
	}

	public void setDesc(TypeDescriptor desc) {
		this.desc = desc;
	}

	@Override
	protected TranslateResult<LocalVariableEntry> extendedTranslate(Translator translator, @Nonnull EntryMapping mapping) {

		return TranslateResult.of(
				mapping.targetName() == null ? RenamableTokenType.OBFUSCATED : RenamableTokenType.DEOBFUSCATED,
				new LocalVariableEntry(this.parent, this.index, this.obfName, this.parameter, mapping)
		);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.parent, this.index);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof LocalVariableEntry localVariableEntry && this.equals(localVariableEntry);
	}

	public boolean equals(LocalVariableEntry other) {
		return this.parent.equals(other.parent) && this.index == other.index;
	}

	@Override
	public boolean canConflictWith(Entry<?> entry) {
		return entry instanceof LocalVariableEntry localVariableEntry && localVariableEntry.parent.equals(this.parent);
	}

	@Override
	public boolean canShadow(Entry<?> entry) {
		return false;
	}

	@Override
	public String toString() {
		return this.parent + "(" + this.index + ":" + this.getName() + ")";
	}

	@Override
	public int compareTo(LocalVariableEntry entry) {
		return Integer.compare(this.index, entry.index);
	}
}
