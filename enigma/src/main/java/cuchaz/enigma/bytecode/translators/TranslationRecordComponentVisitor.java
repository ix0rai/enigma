package cuchaz.enigma.bytecode.translators;

import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.TypePath;

public class TranslationRecordComponentVisitor extends RecordComponentVisitor {
	private final Translator translator;
	private final EntryIndex index;

	public TranslationRecordComponentVisitor(Translator translator, EntryIndex index, int api, RecordComponentVisitor rcv) {
		super(api, rcv);
		this.translator = translator;
		this.index = index;
	}

	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		TypeDescriptor typeDesc = this.translator.translate(new TypeDescriptor(desc));
		AnnotationVisitor av = super.visitAnnotation(typeDesc.toString(), visible);
		return new TranslationAnnotationVisitor(this.translator, this.index, typeDesc.getTypeEntry(index), this.api, av);
	}

	@Override
	public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
		TypeDescriptor typeDesc = this.translator.translate(new TypeDescriptor(desc));
		AnnotationVisitor av = super.visitAnnotation(typeDesc.toString(), visible);
		return new TranslationAnnotationVisitor(this.translator, this.index, typeDesc.getTypeEntry(index), this.api, av);
	}
}
