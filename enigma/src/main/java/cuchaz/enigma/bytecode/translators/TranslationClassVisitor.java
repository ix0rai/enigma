package cuchaz.enigma.bytecode.translators;

import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.translation.representation.entry.definition.ClassDefinition;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.TypePath;

import java.util.Arrays;
import java.util.Objects;

public class TranslationClassVisitor extends ClassVisitor {
	private final Translator translator;
	private ClassEntry entry;
	private final EntryIndex index;

	public TranslationClassVisitor(Translator translator, EntryIndex index, int api, ClassVisitor cv) {
		super(api, cv);
		this.translator = translator;
		this.index = index;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.entry = this.index.getClass(name, ClassDefinition.parse(this.index, access, signature, superName, interfaces));
		ClassDefinition definition = Objects.requireNonNull(this.entry.getDefinition());
		String translatedSuper = definition.getSuperClass() != null ? definition.getSuperClass().getFullName() : null;
		String[] translatedInterfaces = Arrays.stream(definition.getInterfaces()).map(ClassEntry::getFullName).toArray(String[]::new);

		super.visit(version, this.entry.getAccess().getFlags(), this.entry.getFullName(), definition.getSignature().toString(), translatedSuper, translatedInterfaces);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
		FieldEntry entry = FieldEntry.parse(this.entry, access, name, desc, signature);
		FieldEntry translatedEntry = this.translator.translate(entry);
		FieldVisitor fv = super.visitField(translatedEntry.getAccess().getFlags(), translatedEntry.getName(), translatedEntry.getDesc().toString(), translatedEntry.getSignature().toString(), value);
		return new TranslationFieldVisitor(this.translator, this.index, translatedEntry, this.api, fv);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		MethodEntry entry = MethodEntry.parse(this.entry, access, name, desc, signature);
		MethodEntry translatedEntry = this.translator.translate(entry);
		String[] translatedExceptions = new String[exceptions.length];
		for (int i = 0; i < exceptions.length; i++) {
			translatedExceptions[i] = this.index.getClass(exceptions[i]).getFullName();
		}

		MethodVisitor mv = super.visitMethod(translatedEntry.getAccess().getFlags(), translatedEntry.getName(), translatedEntry.getDesc().toString(), translatedEntry.getSignature().toString(), translatedExceptions);
		return new TranslationMethodVisitor(this.translator, index, this.entry, entry, this.api, mv);
	}

	@Override
	public void visitInnerClass(String name, String outerName, String innerName, int access) {
		ClassDefinition definition = ClassDefinition.parse(this.index, access, this.entry.getSignature().toString(), null, new String[0]);
		ClassEntry entry = this.index.getClass(name, definition);
		ClassEntry translatedEntry = this.translator.translate(entry);
		ClassEntry translatedOuterClass = translatedEntry.getOuterClass();
		if (translatedOuterClass == null) {
			throw new IllegalStateException("Translated inner class did not have outer class");
		}

		// Anonymous classes do not specify an outer or inner name. As we do not translate from the given parameter, ignore if the input is null
		String translatedName = translatedEntry.getFullName();
		String translatedOuterName = outerName != null ? translatedOuterClass.getFullName() : null;
		String translatedInnerName = innerName != null ? translatedEntry.getName() : null;
		super.visitInnerClass(translatedName, translatedOuterName, translatedInnerName, translatedEntry.getAccess().getFlags());
	}

	@Override
	public void visitOuterClass(String owner, String name, String desc) {
		if (desc != null) {
			MethodEntry translatedEntry = this.translator.translate(index.getMethod(index.getClass(owner), name, new MethodDescriptor(desc)));
			super.visitOuterClass(translatedEntry.getParent().getFullName(), translatedEntry.getName(), translatedEntry.getDesc().toString());
		} else {
			super.visitOuterClass(owner, name, desc);
		}
	}

	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		TypeDescriptor translatedDesc = this.translator.translate(new TypeDescriptor(desc));
		AnnotationVisitor av = super.visitAnnotation(translatedDesc.toString(), visible);
		return new TranslationAnnotationVisitor(this.translator, index, translatedDesc.getTypeEntry(this.index), this.api, av);
	}

	@Override
	public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
		TypeDescriptor translatedDesc = this.translator.translate(new TypeDescriptor(desc));
		AnnotationVisitor av = super.visitTypeAnnotation(typeRef, typePath, translatedDesc.toString(), visible);
		return new TranslationAnnotationVisitor(this.translator, index, translatedDesc.getTypeEntry(index), this.api, av);
	}

	@Override
	public RecordComponentVisitor visitRecordComponent(String name, String desc, String signature) {
		// Record component names are remapped via the field mapping.
		FieldEntry entry = FieldEntry.parse(this.entry, 0, name, desc, signature);
		FieldEntry translatedEntry = this.translator.translate(entry);
		RecordComponentVisitor fv = super.visitRecordComponent(translatedEntry.getName(), translatedEntry.getDesc().toString(), translatedEntry.getSignature().toString());
		return new TranslationRecordComponentVisitor(this.translator, index, this.api, fv);
	}
}
