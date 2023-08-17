package cuchaz.enigma.source;

import java.util.HashMap;

public enum RenamableTokenType {
	OBFUSCATED(0),
	DEOBFUSCATED(1),
	PROPOSED(2),
	DEBUG(3);

	private static final HashMap<Integer, RenamableTokenType> MAP = new HashMap<>();
	static {
		for (RenamableTokenType tokenType : values()) {
			MAP.put(tokenType.id, tokenType);
		}
	}

	private final int id;

	RenamableTokenType(int id) {
		this.id = id;
	}

	public static RenamableTokenType get(int id) {
		return MAP.get(id);
	}
}
