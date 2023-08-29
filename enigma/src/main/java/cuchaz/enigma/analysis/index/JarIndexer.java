package cuchaz.enigma.analysis.index;

import cuchaz.enigma.analysis.ReferenceTargetType;
import cuchaz.enigma.translation.representation.Lambda;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public interface JarIndexer {
	default void indexClass(ClassEntry classEntry) {
	}

	default void indexField(FieldEntry fieldEntry) {
	}

	default void indexMethod(MethodEntry methodEntry) {
	}

	default void indexMethodReference(MethodEntry callerEntry, MethodEntry referencedEntry, ReferenceTargetType targetType) {
	}

	default void indexFieldReference(MethodEntry callerEntry, FieldEntry referencedEntry, ReferenceTargetType targetType) {
	}

	default void indexLambda(MethodEntry callerEntry, Lambda lambda, ReferenceTargetType targetType) {
	}

	default void indexEnclosingMethod(ClassEntry classEntry, EnclosingMethodData enclosingMethodData) {
	}

	default void processIndex(JarIndex index) {
	}

	default String getTranslationKey() {
		// REMOVE IN 2.0: this is a temporary default impl to avoid api breakage
		return this.getClass().getSimpleName();
	}

	record EnclosingMethodData(String owner, String name, String descriptor) {
		public MethodEntry getMethod(EntryIndex index) {
			return index.getMethod(index.getClass(this.owner), this.name, this.descriptor);
		}
	}
}
