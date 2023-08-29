package cuchaz.enigma.bytecode.translators;

import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import org.objectweb.asm.AnnotationVisitor;

public class TranslationAnnotationVisitor extends AnnotationVisitor {
	private final Translator translator;
	private final ClassEntry annotationEntry;
	private final EntryIndex index;

	public TranslationAnnotationVisitor(Translator translator, EntryIndex index, ClassEntry annotationEntry, int api, AnnotationVisitor av) {
		super(api, av);
		this.translator = translator;
		this.annotationEntry = annotationEntry;
		this.index = index;
	}

	@Override
	public void visit(String name, Object value) {
		super.visit(name, AsmObjectTranslator.translateValue(this.index, value));
	}

	@Override
	public AnnotationVisitor visitArray(String name) {
		return new TranslationAnnotationVisitor(this.translator, this.index, this.annotationEntry, this.api, super.visitArray(name));
	}

	@Override
	public AnnotationVisitor visitAnnotation(String name, String desc) {
		TypeDescriptor type = new TypeDescriptor(desc);
		if (name != null) {
			FieldEntry annotationField = this.translator.translate(new FieldEntry(this.annotationEntry, name, type));
			return super.visitAnnotation(annotationField.getName(), annotationField.getDesc().toString());
		} else {
			return super.visitAnnotation(null, this.translator.translate(type).toString());
		}
	}

	@Override
	public void visitEnum(String name, String desc, String value) {
		TypeDescriptor type = new TypeDescriptor(desc);
		FieldEntry enumField = this.translator.translate(new FieldEntry(type.getTypeEntry(this.index), value, type));
		if (name != null) {
			FieldEntry annotationField = this.translator.translate(new FieldEntry(this.annotationEntry, name, type));
			super.visitEnum(annotationField.getName(), annotationField.getDesc().toString(), enumField.getName());
		} else {
			super.visitEnum(null, this.translator.translate(type).toString(), enumField.getName());
		}
	}
}
