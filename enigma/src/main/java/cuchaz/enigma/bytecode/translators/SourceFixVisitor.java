package cuchaz.enigma.bytecode.translators;

import cuchaz.enigma.analysis.index.BridgeMethodIndex;
import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.translation.representation.entry.definition.ClassDefinition;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class SourceFixVisitor extends ClassVisitor {
	private final JarIndex index;
	private ClassEntry ownerEntry;

	public SourceFixVisitor(int api, ClassVisitor visitor, JarIndex index) {
		super(api, visitor);
		this.index = index;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		ClassDefinition definition = ClassDefinition.parse(index.getEntryIndex(), access, signature, superName, interfaces);
		this.ownerEntry = this.index.getEntryIndex().getClass(name, definition);
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		if (this.ownerEntry.getDefinition().isRecord() && (access & Opcodes.ACC_STATIC) == 0) {
			access |= Opcodes.ACC_PRIVATE;
		}

		return super.visitField(access, name, descriptor, signature, value);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		MethodEntry methodEntry = MethodEntry.parse(this.ownerEntry, access, name, descriptor, signature);

		BridgeMethodIndex bridgeIndex = this.index.getBridgeMethodIndex();
		if (bridgeIndex.isBridgeMethod(methodEntry)) {
			access |= Opcodes.ACC_BRIDGE;
		}

		return super.visitMethod(access, name, descriptor, signature, exceptions);
	}
}
