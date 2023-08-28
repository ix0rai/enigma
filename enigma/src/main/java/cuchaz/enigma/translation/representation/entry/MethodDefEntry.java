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

public class MethodDefEntry extends MethodEntry implements DefEntry<ClassEntry> {
	private final AccessFlags access;
	private final Signature signature;

	public MethodDefEntry(ClassEntry owner, String obfName, MethodDescriptor descriptor, Signature signature, AccessFlags access) {
		this(owner, obfName, descriptor, signature, access, EntryMapping.DEFAULT);
	}

	public MethodDefEntry(ClassEntry owner, String obfName, MethodDescriptor descriptor, Signature signature, AccessFlags access, EntryMapping mapping) {
		super(owner, obfName, descriptor, mapping);
		Preconditions.checkNotNull(access, "Method access cannot be null");
		Preconditions.checkNotNull(signature, "Method signature cannot be null");
		this.access = access;
		this.signature = signature;
	}

	public static MethodDefEntry parse(ClassEntry owner, int access, String obfName, String desc, String signature) {
		return new MethodDefEntry(owner, obfName, new MethodDescriptor(desc), Signature.createSignature(signature), new AccessFlags(access), EntryMapping.DEFAULT);
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

		return TranslateResult.of(
			mapping.targetName() == null ? RenamableTokenType.OBFUSCATED : RenamableTokenType.DEOBFUSCATED,
			new MethodDefEntry(this.parent, this.obfName, translatedDesc, translatedSignature, this.access, mapping)
		);
	}
}
