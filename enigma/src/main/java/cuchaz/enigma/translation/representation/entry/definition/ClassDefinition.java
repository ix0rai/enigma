package cuchaz.enigma.translation.representation.entry.definition;

import com.google.common.base.Preconditions;
import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.Signature;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

import javax.annotation.Nullable;
import java.util.Arrays;

public class ClassDefinition implements Definition {
	private final AccessFlags access;
	private final Signature signature;
	@Nullable
	private final ClassEntry superClass;
	private final ClassEntry[] interfaces;

	public ClassDefinition(AccessFlags access, Signature signature, @Nullable ClassEntry superClass, ClassEntry[] interfaces) {
		this.access = access;
		this.signature = signature;
		this.superClass = superClass;
		this.interfaces = interfaces;

		Preconditions.checkNotNull(signature, "Class signature cannot be null");
		Preconditions.checkNotNull(access, "Class access cannot be null");
	}

	public AccessFlags getAccess() {
		return this.access;
	}

	public Signature getSignature() {
		return this.signature;
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

	public static ClassDefinition parse(EntryIndex index, int access, String signature, String superName, String[] interfaces) {
		ClassEntry superClass = superName != null ? index.getClass(superName) : null;
		ClassEntry[] interfaceClasses = Arrays.stream(interfaces).map(index::getClass).toArray(ClassEntry[]::new);
		return new ClassDefinition(new AccessFlags(access), Signature.createSignature(signature), superClass, interfaceClasses);
	}
}
