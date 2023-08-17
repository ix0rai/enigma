package cuchaz.enigma.translation.representation.entry;

import com.google.common.base.Preconditions;
import cuchaz.enigma.source.RenamableTokenType;
import cuchaz.enigma.translation.TranslateResult;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.Signature;

import java.util.Arrays;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ClassDefEntry extends ClassEntry implements DefEntry<ClassEntry> {
	private final AccessFlags access;
	private final Signature signature;
	@Nullable
	private final ClassEntry superClass;
	private final ClassEntry[] interfaces;

	public ClassDefEntry(String className, String obfClassName, Signature signature, AccessFlags access, @Nullable ClassEntry superClass, ClassEntry[] interfaces) {
		this(getOuterClass(className, obfClassName), getInnerName(className), obfClassName, signature, access, superClass, interfaces, null);
	}

	public ClassDefEntry(ClassEntry parent, String className, String obfClassName, Signature signature, AccessFlags access, @Nullable ClassEntry superClass, ClassEntry[] interfaces) {
		this(parent, className, obfClassName, signature, access, superClass, interfaces, null);
	}

	public ClassDefEntry(ClassEntry parent, String className, String obfClassName, Signature signature, AccessFlags access, @Nullable ClassEntry superClass, ClassEntry[] interfaces, @Nullable EntryMapping mapping) {
		super(parent, className, obfClassName, mapping);
		Preconditions.checkNotNull(signature, "Class signature cannot be null");
		Preconditions.checkNotNull(access, "Class access cannot be null");

		this.signature = signature;
		this.access = access;
		this.superClass = superClass;
		this.interfaces = interfaces != null ? interfaces : new ClassEntry[0];
	}

	public static ClassDefEntry parse(int access, String obfName, String signature, String superName, String[] interfaces) {
		ClassEntry superClass = superName != null ? new ClassEntry(superName, superName) : null;
		ClassEntry[] interfaceClasses = Arrays.stream(interfaces).map(s -> new ClassEntry(s, s)).toArray(ClassEntry[]::new);
		return new ClassDefEntry(obfName, obfName, Signature.createSignature(signature), new AccessFlags(access), superClass, interfaceClasses);
	}

	public Signature getSignature() {
		return this.signature;
	}

	@Override
	public AccessFlags getAccess() {
		return this.access;
	}

	@Nullable
	public ClassEntry getSuperClass() {
		return this.superClass;
	}

	public ClassEntry[] getInterfaces() {
		return this.interfaces;
	}

	public boolean isEnum() {
		return this.superClass != null && this.superClass.getName().equals("java/lang/Enum");
	}

	public boolean isRecord() {
		return this.superClass != null && this.superClass.getName().equals("java/lang/Record");
	}

	@Override
	public TranslateResult<ClassDefEntry> extendedTranslate(Translator translator, @Nonnull EntryMapping mapping) {
		Signature translatedSignature = translator.translate(this.signature);
		String translatedName = mapping.targetName() != null ? mapping.targetName() : this.name;
		ClassEntry translatedSuper = translator.translate(this.superClass);
		ClassEntry[] translatedInterfaces = Arrays.stream(this.interfaces).map(translator::translate).toArray(ClassEntry[]::new);

		return TranslateResult.of(
				mapping.targetName() == null ? RenamableTokenType.OBFUSCATED : RenamableTokenType.DEOBFUSCATED,
				new ClassDefEntry(this.parent, translatedName, this.obfName, translatedSignature, this.access, translatedSuper, translatedInterfaces, mapping)
		);
	}

	@Override
	public ClassDefEntry withName(String name, RenamableTokenType tokenType) {
		return new ClassDefEntry(this.parent, name, this.obfName, this.signature, this.access, this.superClass, this.interfaces, new EntryMapping(name, this.getJavadocs(), tokenType));
	}

	@Override
	public ClassDefEntry withParent(ClassEntry parent) {
		return new ClassDefEntry(parent, this.name, this.obfName, this.signature, this.access, this.superClass, this.interfaces, this.mapping);
	}
}
