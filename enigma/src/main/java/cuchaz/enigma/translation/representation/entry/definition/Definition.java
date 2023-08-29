package cuchaz.enigma.translation.representation.entry.definition;

import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.Signature;

import javax.annotation.Nullable;

public interface Definition {
	@Nullable
	Signature getSignature();

	@Nullable
	AccessFlags getAccess();
}
