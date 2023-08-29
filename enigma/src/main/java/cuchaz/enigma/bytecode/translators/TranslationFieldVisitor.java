package cuchaz.enigma.bytecode.translators;

import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.TypePath;

public class TranslationFieldVisitor extends FieldVisitor {
	private final FieldEntry fieldEntry;
	private final Translator translator;
	private final EntryIndex index;

	public TranslationFieldVisitor(Translator translator, EntryIndex index, FieldEntry fieldEntry, int api, FieldVisitor fv) {
		super(api, fv);
		this.translator = translator;
		this.fieldEntry = fieldEntry;
		this.index = index;
	}

	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		TypeDescriptor typeDesc = this.translator.translate(new TypeDescriptor(desc));
		AnnotationVisitor av = super.visitAnnotation(typeDesc.toString(), visible);
		return new TranslationAnnotationVisitor(this.translator, index, typeDesc.getTypeEntry(index), this.api, av);
	}

	@Override
	public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
		TypeDescriptor typeDesc = this.translator.translate(new TypeDescriptor(desc));
		AnnotationVisitor av = super.visitAnnotation(typeDesc.toString(), visible);
		return new TranslationAnnotationVisitor(this.translator, index, typeDesc.getTypeEntry(index), this.api, av);
	}
}
