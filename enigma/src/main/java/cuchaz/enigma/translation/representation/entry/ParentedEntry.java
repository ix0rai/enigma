package cuchaz.enigma.translation.representation.entry;

import com.google.common.base.Preconditions;
import cuchaz.enigma.source.RenamableTokenType;
import cuchaz.enigma.translation.TranslateResult;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.mapping.EntryMap;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.EntryResolver;
import cuchaz.enigma.translation.mapping.ResolutionStrategy;
import cuchaz.enigma.translation.representation.entry.definition.ClassDefinition;
import cuchaz.enigma.translation.representation.entry.definition.Definition;
import cuchaz.enigma.translation.representation.entry.definition.DefinitionEntry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class ParentedEntry<P extends Entry<?>> implements Entry<P> {
	protected P parent;
	protected final String obfName;
	protected EntryMapping mapping;

	protected ParentedEntry(P parent, String obfName, EntryMapping mapping) {
		this.parent = parent;
		this.mapping = mapping;
		this.obfName = obfName;

		Preconditions.checkNotNull(obfName, "Obf name be null arr I'm a pirate");
		Preconditions.checkNotNull(mapping, "Mapping cannot be null (use EntryMapping.DEFAULT when no mapping exists!)");
	}

	@Override
	public ParentedEntry<P> withParent(P parent) {
		this.parent = parent;
		return this;
	}

	@Override
	public ParentedEntry<P> withName(String name, RenamableTokenType tokenType) {
		this.setMapping(new EntryMapping(name, this.getJavadocs(), tokenType));
		return this;
	};

	protected abstract TranslateResult<? extends ParentedEntry<P>> extendedTranslate(Translator translator, @Nonnull EntryMapping mapping);

	@Override
	public String getObfName() {
		return this.obfName;
	}

	@Override
	public String getFullName() {
		return this.parent.getFullName() + "." + this.getName();
	}

	@Override
	public String getContextualName() {
		return this.parent.getContextualName() + "." + this.getName();
	}

	@Override
	@Nullable
	public P getParent() {
		return this.parent;
	}

	@Override
	public EntryMapping getMapping() {
		return this.mapping;
	}

	@Override
	public void setMapping(@Nonnull EntryMapping mapping) {
		this.mapping = mapping;
	}

	@Nullable
	@Override
	public RenamableTokenType getTokenType() {
		return this.mapping == null ? null : this.mapping.tokenType();
	}

	@Override
	public TranslateResult<? extends ParentedEntry<P>> extendedTranslate(Translator translator, EntryResolver resolver, EntryMap<EntryMapping> mappings) {
		EntryMapping mapping = this.resolveMapping(resolver, mappings);
		// todo remove
		if (mapping == null) {
			throw new RuntimeException();
		}
		if (this.getParent() == null) {
			return this.extendedTranslate(translator, mapping);
		}

		P translatedParent = translator.translate(this.getParent());
		return this.withParent(translatedParent).extendedTranslate(translator, mapping);
	}

	private EntryMapping resolveMapping(EntryResolver resolver, EntryMap<EntryMapping> mappings) {
		for (ParentedEntry<P> entry : resolver.resolveEntry(this, ResolutionStrategy.RESOLVE_ROOT)) {
			EntryMapping mapping = mappings.get(entry);
			if (mapping != null) {
				return mapping;
			}
		}

		return EntryMapping.DEFAULT;
	}
}
