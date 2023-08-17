package cuchaz.enigma.translation.representation.entry;

import com.google.common.base.Preconditions;
import cuchaz.enigma.source.RenamableTokenType;
import cuchaz.enigma.translation.TranslateResult;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.TypeDescriptor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class LocalVariableDefEntry extends LocalVariableEntry {
	protected final TypeDescriptor desc;

	public LocalVariableDefEntry(MethodEntry ownerEntry, int index, String name, String obfName, boolean parameter, TypeDescriptor desc, @Nullable EntryMapping mapping) {
		super(ownerEntry, index, name, obfName, parameter, mapping);
		Preconditions.checkNotNull(desc, "Variable desc cannot be null");

		this.desc = desc;
	}

	public TypeDescriptor getDesc() {
		return this.desc;
	}

	@Override
	protected TranslateResult<LocalVariableEntry> extendedTranslate(Translator translator, @Nonnull EntryMapping mapping) {
		TypeDescriptor translatedDesc = translator.translate(this.desc);
		String translatedName = mapping.targetName() != null ? mapping.targetName() : this.name;

		return TranslateResult.of(
				mapping.targetName() == null ? RenamableTokenType.OBFUSCATED : RenamableTokenType.DEOBFUSCATED,
				new LocalVariableDefEntry(this.parent, this.index, translatedName, this.obfName, this.parameter, translatedDesc, mapping)
		);
	}

	@Override
	public LocalVariableDefEntry withName(String name, RenamableTokenType tokenType) {
		return new LocalVariableDefEntry(this.parent, this.index, name, this.obfName, this.parameter, this.desc, new EntryMapping(name, this.getJavadocs(), tokenType));
	}

	@Override
	public LocalVariableDefEntry withParent(MethodEntry entry) {
		return new LocalVariableDefEntry(entry, this.index, this.name, this.obfName, this.parameter, this.desc, this.mapping);
	}

	@Override
	public String toString() {
		return this.parent + "(" + this.index + ":" + this.name + ":" + this.desc + ")";
	}
}
