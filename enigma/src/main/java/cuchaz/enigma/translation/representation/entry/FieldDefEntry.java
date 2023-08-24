package cuchaz.enigma.translation.representation.entry;

import com.google.common.base.Preconditions;
import cuchaz.enigma.source.RenamableTokenType;
import cuchaz.enigma.translation.TranslateResult;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.Signature;
import cuchaz.enigma.translation.representation.TypeDescriptor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class FieldDefEntry extends FieldEntry implements DefEntry<ClassEntry> {
	private final AccessFlags access;
	private final Signature signature;

	public FieldDefEntry(ClassEntry owner, String name, String obfName, TypeDescriptor desc, Signature signature, AccessFlags access) {
		this(owner, name, obfName, desc, signature, access, EntryMapping.DEFAULT);
	}

	public FieldDefEntry(ClassEntry owner, String name, String obfName, TypeDescriptor desc, Signature signature, AccessFlags access, EntryMapping mapping) {
		super(owner, name, obfName, desc, mapping);
		Preconditions.checkNotNull(access, "Field access cannot be null");
		Preconditions.checkNotNull(signature, "Field signature cannot be null");
		this.access = access;
		this.signature = signature;
	}

	public static FieldDefEntry parse(ClassEntry owner, int access, String obfName, String desc, String signature) {
		return new FieldDefEntry(owner, obfName, obfName, new TypeDescriptor(desc), Signature.createTypedSignature(signature), new AccessFlags(access), EntryMapping.DEFAULT);
	}

	@Override
	public AccessFlags getAccess() {
		return this.access;
	}

	public Signature getSignature() {
		return this.signature;
	}

	@Override
	protected TranslateResult<FieldEntry> extendedTranslate(Translator translator, @Nonnull EntryMapping mapping) {
		TypeDescriptor translatedDesc = translator.translate(this.desc);
		Signature translatedSignature = translator.translate(this.signature);
		String translatedName = mapping.targetName() != null ? mapping.targetName() : this.name;

		return TranslateResult.of(
				mapping.targetName() == null ? RenamableTokenType.OBFUSCATED : RenamableTokenType.DEOBFUSCATED,
				new FieldDefEntry(this.parent, translatedName, this.obfName, translatedDesc, translatedSignature, this.access, mapping)
		);
	}

	@Override
	public FieldDefEntry withName(String name, RenamableTokenType tokenType) {
		return new FieldDefEntry(this.parent, name, this.obfName, this.desc, this.signature, this.access, new EntryMapping(name, this.getJavadocs(), tokenType));
	}

	@Override
	public FieldDefEntry withParent(ClassEntry owner) {
		return new FieldDefEntry(owner, this.name, this.obfName, this.desc, this.signature, this.access, this.mapping);
	}
}
