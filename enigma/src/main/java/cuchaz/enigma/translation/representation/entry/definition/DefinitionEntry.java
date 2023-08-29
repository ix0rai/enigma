package cuchaz.enigma.translation.representation.entry.definition;

import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.Signature;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface DefinitionEntry<T extends Definition> extends Definition {
	@Nullable
	T getDefinition();

	void setDefinition(@Nonnull T definition);

	@Nullable
	default AccessFlags getAccess() {
		return this.getDefinition() == null ? null : this.getDefinition().getAccess();
	}

	@Nullable
	default Signature getSignature() {
		return this.getDefinition() == null ? null : this.getDefinition().getSignature();
	}
}
