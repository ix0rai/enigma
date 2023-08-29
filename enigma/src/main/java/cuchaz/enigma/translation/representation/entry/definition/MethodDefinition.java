package cuchaz.enigma.translation.representation.entry.definition;

import com.google.common.base.Preconditions;
import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.Signature;

public class MethodDefinition implements Definition {
	private final AccessFlags access;
	private final Signature signature;

	public MethodDefinition(AccessFlags access, Signature signature) {
		this.access = access;
		this.signature = signature;

		Preconditions.checkNotNull(access, "Method access cannot be null");
		Preconditions.checkNotNull(signature, "Method signature cannot be null");
	}

	public AccessFlags getAccess() {
		return this.access;
	}

	public Signature getSignature() {
		return this.signature;
	}
}
