package cuchaz.enigma.analysis.index;

import cuchaz.enigma.analysis.MethodNodeWithAction;
import cuchaz.enigma.translation.representation.ParameterAccessFlags;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.translation.representation.entry.definition.ClassDefinition;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

public class IndexClassVisitor extends ClassVisitor {
	private final JarIndexer indexer;
	private ClassEntry classEntry;
	private EntryIndex index;

	public IndexClassVisitor(JarIndex indexer, EntryIndex index, int api) {
		super(api);
		this.indexer = indexer;
		this.index = index;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		ClassDefinition definition = ClassDefinition.parse(this.index, access, signature, superName, interfaces);
		this.classEntry = this.index.getClass(name, definition);
		this.indexer.indexClass(this.classEntry);

		super.visit(version, access, name, signature, superName, interfaces);
	}

	// ASM calls the EnclosingMethod attribute "OuterClass"
	@Override
	public void visitOuterClass(String owner, String name, String descriptor) {
		this.indexer.indexEnclosingMethod(this.classEntry, new JarIndexer.EnclosingMethodData(owner, name, descriptor));

		super.visitOuterClass(owner, name, descriptor);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
		this.indexer.indexField(FieldEntry.parse(this.classEntry, access, name, desc, signature));

		return super.visitField(access, name, desc, signature, value);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		MethodEntry entry = MethodEntry.parse(this.classEntry, access, name, desc, signature);

		return new MethodNodeWithAction(this.api, access, name, desc, signature, exceptions, methodNode -> {
			// add parameter access values to the entry
			if (methodNode.parameters != null) {
				for (int i = 0; i < methodNode.parameters.size(); i++) {
					entry.getDesc().getArgumentDescs().get(i).setAccess(new ParameterAccessFlags(methodNode.parameters.get(i).access));
				}
			}

			this.indexer.indexMethod(entry);
		});
	}
}
