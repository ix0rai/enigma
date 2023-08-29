package cuchaz.enigma.translation.representation.entry.definition;

import com.google.common.base.Preconditions;
import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.Signature;

public class FieldDefinition implements Definition {
	private final AccessFlags access;
	private final Signature signature;

	public FieldDefinition(AccessFlags access, Signature signature) {
		this.access = access;
		this.signature = signature;

		Preconditions.checkNotNull(access, "Field access cannot be null");
		Preconditions.checkNotNull(signature, "Field signature cannot be null");
	}

	public AccessFlags getAccess() {
		return this.access;
	}

	public Signature getSignature() {
		return this.signature;
	}
}
