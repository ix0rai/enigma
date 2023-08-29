package cuchaz.enigma.translation.representation.entry;

import cuchaz.enigma.source.RenamableTokenType;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.entry.definition.Definition;
import cuchaz.enigma.translation.representation.entry.definition.DefinitionEntry;

/**
 * A {@link ParentedEntry} that also supports a {@link Definition}.
 */
public abstract class DefinedEntry<E extends Entry<?>, D extends Definition> extends ParentedEntry<E> implements DefinitionEntry<D> {
	protected DefinedEntry(E parent, String obfName, EntryMapping mapping) {
		super(parent, obfName, mapping);
	}

	@Override
	public DefinedEntry<E, D> withParent(E parent) {
		super.withParent(parent);
		return this;
	}

	@Override
	public DefinedEntry<E, D> withName(String name, RenamableTokenType tokenType) {
		super.withName(name, tokenType);
		return this;
	};
}
