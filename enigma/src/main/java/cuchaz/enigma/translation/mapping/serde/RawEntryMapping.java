package cuchaz.enigma.translation.mapping.serde;

import cuchaz.enigma.source.RenamableTokenType;
import cuchaz.enigma.translation.mapping.EntryMapping;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class RawEntryMapping {
	private final String targetName;
	private final RenamableTokenType tokenType;
	private final List<String> javadocs = new ArrayList<>();

	public RawEntryMapping(@Nullable String targetName, @Nullable RenamableTokenType tokenType) {
		this.targetName = targetName != null && !targetName.equals("-") ? targetName : null;
		this.tokenType = tokenType == null ? RenamableTokenType.OBFUSCATED : tokenType;
	}

	public void addJavadocLine(String line) {
		this.javadocs.add(line);
	}

	public EntryMapping bake() {
		return new EntryMapping(this.targetName, this.javadocs.isEmpty() ? null : String.join("\n", this.javadocs), tokenType);
	}
}
