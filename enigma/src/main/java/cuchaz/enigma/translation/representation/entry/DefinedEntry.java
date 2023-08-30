package cuchaz.enigma.translation.representation.entry;

import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.Signature;
import cuchaz.enigma.translation.representation.entry.definition.Definition;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A {@link ParentedEntry} that also supports a {@link Definition}.
 */
public abstract class DefinedEntry<E extends Entry<?>, D extends Definition> extends ParentedEntry<E> implements Definition {
	protected @Nullable D definition;

	protected DefinedEntry(E parent, String obfName,  @Nullable D definition, EntryMapping mapping) {
		super(parent, obfName, mapping);
		this.definition = definition;
	}

	@Nullable
	public D getDefinition() {
		return this.definition;
	}

	public void setDefinition(@Nonnull D definition) {
		this.definition = definition;
	}

	@Nullable
	public AccessFlags getAccess() {
		return this.getDefinition() == null ? null : this.getDefinition().getAccess();
	}

	@Nullable
	public Signature getSignature() {
		return this.getDefinition() == null ? null : this.getDefinition().getSignature();
	}
}
