package cuchaz.enigma.translation.representation.entry;

import com.google.common.base.Preconditions;
import cuchaz.enigma.source.RenamableTokenType;
import cuchaz.enigma.translation.TranslateResult;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.Signature;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class MethodDefEntry extends MethodEntry implements DefEntry<ClassEntry> {
	private final AccessFlags access;
	private final Signature signature;

	public MethodDefEntry(ClassEntry owner, String name, String obfName, MethodDescriptor descriptor, Signature signature, AccessFlags access) {
		this(owner, name, obfName, descriptor, signature, access, EntryMapping.DEFAULT);
	}

	public MethodDefEntry(ClassEntry owner, String name, String obfName, MethodDescriptor descriptor, Signature signature, AccessFlags access, EntryMapping mapping) {
		super(owner, name, obfName, descriptor, mapping);
		Preconditions.checkNotNull(access, "Method access cannot be null");
		Preconditions.checkNotNull(signature, "Method signature cannot be null");
		this.access = access;
		this.signature = signature;
	}

	public static MethodDefEntry parse(ClassEntry owner, int access, String obfName, String desc, String signature) {
		return new MethodDefEntry(owner, obfName, obfName, new MethodDescriptor(desc), Signature.createSignature(signature), new AccessFlags(access), EntryMapping.DEFAULT);
	}

	@Override
	public AccessFlags getAccess() {
		return this.access;
	}

	public Signature getSignature() {
		return this.signature;
	}

	@Override
	protected TranslateResult<MethodDefEntry> extendedTranslate(Translator translator, @Nonnull EntryMapping mapping) {
		MethodDescriptor translatedDesc = translator.translate(this.descriptor);
		Signature translatedSignature = translator.translate(this.signature);
		String translatedName = mapping.targetName() != null ? mapping.targetName() : this.name;

		return TranslateResult.of(
				mapping.targetName() == null ? RenamableTokenType.OBFUSCATED : RenamableTokenType.DEOBFUSCATED,
				new MethodDefEntry(this.parent, translatedName, this.obfName, translatedDesc, translatedSignature, this.access, mapping)
		);
	}

	@Override
	public MethodDefEntry withName(String name, RenamableTokenType tokenType) {
		return new MethodDefEntry(this.parent, name, this.obfName, this.descriptor, this.signature, this.access, new EntryMapping(name, this.getJavadocs(), tokenType));
	}

	@Override
	public MethodDefEntry withParent(ClassEntry parent) {
		return new MethodDefEntry(parent, this.name, this.obfName, this.descriptor, this.signature, this.access, this.mapping);
	}
}
