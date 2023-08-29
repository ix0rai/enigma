package cuchaz.enigma.bytecode.translators;

import com.google.common.base.CharMatcher;
import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.translation.LocalNameGenerator;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.translation.representation.entry.definition.ClassDefinition;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalVariableFixVisitor extends ClassVisitor {
	private ClassEntry ownerEntry;
	private EntryIndex index;

	public LocalVariableFixVisitor(EntryIndex index, int api, ClassVisitor visitor) {
		super(api, visitor);
		this.index = index;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		ClassDefinition definition = ClassDefinition.parse(index, access, signature, superName, interfaces);
		this.ownerEntry = index.getClass(name, definition);
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		MethodEntry methodEntry = MethodEntry.parse(this.ownerEntry, access, name, descriptor, signature);
		return new Method(this.api, methodEntry, super.visitMethod(access, name, descriptor, signature, exceptions));
	}

	private class Method extends MethodVisitor {
		private final MethodEntry methodEntry;
		private final Map<Integer, String> parameterNames = new HashMap<>();
		private final Map<Integer, Integer> parameterIndices = new HashMap<>();
		private boolean hasParameterTable;
		private int parameterIndex = 0;

		Method(int api, MethodEntry methodEntry, MethodVisitor visitor) {
			super(api, visitor);
			this.methodEntry = methodEntry;

			int lvIndex = methodEntry.getAccess().isStatic() ? 0 : 1;
			List<TypeDescriptor> parameters = methodEntry.getDesc().getTypeDescs();
			for (int parameterIndex = 0; parameterIndex < parameters.size(); parameterIndex++) {
				TypeDescriptor param = parameters.get(parameterIndex);
				this.parameterIndices.put(lvIndex, parameterIndex);
				lvIndex += param.getSize();
			}
		}

		@Override
		public void visitParameter(String name, int access) {
			this.hasParameterTable = true;
			super.visitParameter(this.fixParameterName(this.parameterIndex, name), this.fixParameterAccess(this.parameterIndex, access));
			this.parameterIndex++;
		}

		@Override
		public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
			if (index == 0 && !this.methodEntry.getAccess().isStatic()) {
				name = "this";
			} else if (this.parameterIndices.containsKey(index)) {
				name = this.fixParameterName(this.parameterIndices.get(index), name);
			} else if (this.isInvalidName(name)) {
				name = LocalNameGenerator.generateLocalVariableName(LocalVariableFixVisitor.this.index, index, new TypeDescriptor(desc));
			}

			super.visitLocalVariable(name, desc, signature, start, end, index);
		}

		private boolean isInvalidName(String name) {
			return name == null || name.isEmpty() || !CharMatcher.ascii().matchesAllOf(name);
		}

		@Override
		public void visitEnd() {
			if (!this.hasParameterTable) {
				List<TypeDescriptor> arguments = this.methodEntry.getDesc().getTypeDescs();
				for (int argumentIndex = 0; argumentIndex < arguments.size(); argumentIndex++) {
					super.visitParameter(this.fixParameterName(argumentIndex, null), this.fixParameterAccess(argumentIndex, 0));
				}
			}

			super.visitEnd();
		}

		private String fixParameterName(int index, String name) {
			if (this.parameterNames.get(index) != null) {
				return this.parameterNames.get(index); // to make sure that LVT names are consistent with parameter table names
			}

			if (this.isInvalidName(name)) {
				List<TypeDescriptor> arguments = this.methodEntry.getDesc().getTypeDescs();
				name = LocalNameGenerator.generateArgumentName(LocalVariableFixVisitor.this.index, index, arguments.get(index), arguments);
			}

			if (index == 0 && LocalVariableFixVisitor.this.ownerEntry.getAccess().isEnum() && this.methodEntry.getObfName().equals("<init>")) {
				name = "name";
			}

			if (index == 1 && LocalVariableFixVisitor.this.ownerEntry.getAccess().isEnum() && this.methodEntry.getObfName().equals("<init>")) {
				name = "ordinal";
			}

			this.parameterNames.put(index, name);
			return name;
		}

		private int fixParameterAccess(int index, int access) {
			if (index == 0 && LocalVariableFixVisitor.this.ownerEntry.getAccess().isEnum() && this.methodEntry.getObfName().equals("<init>")) {
				access |= Opcodes.ACC_SYNTHETIC;
			}

			if (index == 1 && LocalVariableFixVisitor.this.ownerEntry.getAccess().isEnum() && this.methodEntry.getObfName().equals("<init>")) {
				access |= Opcodes.ACC_SYNTHETIC;
			}

			return access;
		}
	}
}
