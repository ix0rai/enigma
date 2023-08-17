package cuchaz.enigma.translation.mapping;

import cuchaz.enigma.source.RenamableTokenType;

import javax.annotation.Nullable;

public record EntryMapping(
	@Nullable String targetName,
	@Nullable String javadoc,
	RenamableTokenType tokenType
	) {
	public static final EntryMapping DEFAULT = new EntryMapping(null, null, RenamableTokenType.OBFUSCATED);

	public EntryMapping(@Nullable String targetName) {
		this(targetName, null, targetName == null ? RenamableTokenType.OBFUSCATED : RenamableTokenType.DEOBFUSCATED);
	}

	public EntryMapping withDeobfName(String newName) {
		return new EntryMapping(newName, this.javadoc, RenamableTokenType.DEOBFUSCATED);
	}

	public EntryMapping withProposedName(String newName) {
		return new EntryMapping(newName, this.javadoc, RenamableTokenType.PROPOSED);
	}

	public EntryMapping withDocs(String newDocs) {
		return new EntryMapping(this.targetName, newDocs, this.tokenType);
	}
}
