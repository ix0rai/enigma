package cuchaz.enigma.analysis.index;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import cuchaz.enigma.analysis.EntryReference;
import cuchaz.enigma.analysis.ReferenceTargetType;
import cuchaz.enigma.translation.mapping.ResolutionStrategy;
import cuchaz.enigma.translation.representation.Lambda;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

import java.util.Collection;
import java.util.Map;

public class ReferenceIndex implements JarIndexer {
	private Multimap<MethodEntry, MethodEntry> methodReferences = HashMultimap.create();

	private Multimap<MethodEntry, EntryReference<MethodEntry, MethodEntry>> referencesToMethods = HashMultimap.create();
	private Multimap<ClassEntry, EntryReference<ClassEntry, MethodEntry>> referencesToClasses = HashMultimap.create();
	private Multimap<FieldEntry, EntryReference<FieldEntry, MethodEntry>> referencesToFields = HashMultimap.create();
	private Multimap<ClassEntry, EntryReference<ClassEntry, FieldEntry>> fieldTypeReferences = HashMultimap.create();
	private Multimap<ClassEntry, EntryReference<ClassEntry, MethodEntry>> methodTypeReferences = HashMultimap.create();

	private final EntryIndex index;

	public ReferenceIndex(EntryIndex index) {
		this.index = index;
	}

	@Override
	public void indexMethod(MethodEntry methodEntry) {
		this.indexMethodDescriptor(methodEntry, methodEntry.getDesc());
	}

	private void indexMethodDescriptor(MethodEntry entry, MethodDescriptor descriptor) {
		for (TypeDescriptor typeDescriptor : descriptor.getArgumentDescs()) {
			this.indexMethodTypeDescriptor(entry, typeDescriptor);
		}

		this.indexMethodTypeDescriptor(entry, descriptor.getReturnDesc());
	}

	private void indexMethodTypeDescriptor(MethodEntry method, TypeDescriptor typeDescriptor) {
		if (typeDescriptor.isType()) {
			ClassEntry referencedClass = typeDescriptor.getTypeEntry(index);
			this.methodTypeReferences.put(referencedClass, new EntryReference<>(referencedClass, referencedClass.getName(), method));
		} else if (typeDescriptor.isArray()) {
			this.indexMethodTypeDescriptor(method, typeDescriptor.getArrayType());
		}
	}

	@Override
	public void indexField(FieldEntry fieldEntry) {
		this.indexFieldTypeDescriptor(fieldEntry, fieldEntry.getDesc());
	}

	private void indexFieldTypeDescriptor(FieldEntry field, TypeDescriptor typeDescriptor) {
		if (typeDescriptor.isType()) {
			ClassEntry referencedClass = typeDescriptor.getTypeEntry(index);
			this.fieldTypeReferences.put(referencedClass, new EntryReference<>(referencedClass, referencedClass.getName(), field));
		} else if (typeDescriptor.isArray()) {
			this.indexFieldTypeDescriptor(field, typeDescriptor.getArrayType());
		}
	}

	@Override
	public void indexMethodReference(MethodEntry callerEntry, MethodEntry referencedEntry, ReferenceTargetType targetType) {
		this.referencesToMethods.put(referencedEntry, new EntryReference<>(referencedEntry, referencedEntry.getName(), callerEntry, targetType));
		this.methodReferences.put(callerEntry, referencedEntry);

		if (referencedEntry.isConstructor()) {
			ClassEntry referencedClass = referencedEntry.getParent();
			this.referencesToClasses.put(referencedClass, new EntryReference<>(referencedClass, referencedEntry.getName(), callerEntry, targetType));
		}
	}

	@Override
	public void indexFieldReference(MethodEntry callerEntry, FieldEntry referencedEntry, ReferenceTargetType targetType) {
		this.referencesToFields.put(referencedEntry, new EntryReference<>(referencedEntry, referencedEntry.getName(), callerEntry, targetType));
	}

	@Override
	public void indexLambda(MethodEntry callerEntry, Lambda lambda, ReferenceTargetType targetType) {
		if (lambda.implMethod() instanceof MethodEntry method) {
			this.indexMethodReference(callerEntry, method, targetType);
		} else {
			this.indexFieldReference(callerEntry, (FieldEntry) lambda.implMethod(), targetType);
		}

		this.indexMethodDescriptor(callerEntry, lambda.invokedType());
		this.indexMethodDescriptor(callerEntry, lambda.samMethodType());
		this.indexMethodDescriptor(callerEntry, lambda.instantiatedMethodType());
	}

	@Override
	public void processIndex(JarIndex index) {
		this.methodReferences = this.remapReferences(index, this.methodReferences);
		this.referencesToMethods = this.remapReferencesTo(index, this.referencesToMethods);
		this.referencesToClasses = this.remapReferencesTo(index, this.referencesToClasses);
		this.referencesToFields = this.remapReferencesTo(index, this.referencesToFields);
		this.fieldTypeReferences = this.remapReferencesTo(index, this.fieldTypeReferences);
		this.methodTypeReferences = this.remapReferencesTo(index, this.methodTypeReferences);
	}

	private <K extends Entry<?>, V extends Entry<?>> Multimap<K, V> remapReferences(JarIndex index, Multimap<K, V> multimap) {
		final int keySetSize = multimap.keySet().size();
		Multimap<K, V> resolved = HashMultimap.create(multimap.keySet().size(), keySetSize == 0 ? 0 : multimap.size() / keySetSize);
		for (Map.Entry<K, V> entry : multimap.entries()) {
			resolved.put(this.remap(index, entry.getKey()), this.remap(index, entry.getValue()));
		}

		return resolved;
	}

	private <E extends Entry<?>, C extends Entry<?>> Multimap<E, EntryReference<E, C>> remapReferencesTo(JarIndex index, Multimap<E, EntryReference<E, C>> multimap) {
		final int keySetSize = multimap.keySet().size();
		Multimap<E, EntryReference<E, C>> resolved = HashMultimap.create(keySetSize, keySetSize == 0 ? 0 : multimap.size() / keySetSize);
		for (Map.Entry<E, EntryReference<E, C>> entry : multimap.entries()) {
			resolved.put(this.remap(index, entry.getKey()), this.remap(index, entry.getValue()));
		}

		return resolved;
	}

	private <E extends Entry<?>> E remap(JarIndex index, E entry) {
		return index.getEntryResolver().resolveFirstEntry(entry, ResolutionStrategy.RESOLVE_CLOSEST);
	}

	private <E extends Entry<?>, C extends Entry<?>> EntryReference<E, C> remap(JarIndex index, EntryReference<E, C> reference) {
		return index.getEntryResolver().resolveFirstReference(reference, ResolutionStrategy.RESOLVE_CLOSEST);
	}

	public Collection<MethodEntry> getMethodsReferencedBy(MethodEntry entry) {
		return this.methodReferences.get(entry);
	}

	public Collection<EntryReference<FieldEntry, MethodEntry>> getReferencesToField(FieldEntry entry) {
		return this.referencesToFields.get(entry);
	}

	public Collection<EntryReference<ClassEntry, MethodEntry>> getReferencesToClass(ClassEntry entry) {
		return this.referencesToClasses.get(entry);
	}

	public Collection<EntryReference<MethodEntry, MethodEntry>> getReferencesToMethod(MethodEntry entry) {
		return this.referencesToMethods.get(entry);
	}

	public Collection<EntryReference<ClassEntry, FieldEntry>> getFieldTypeReferencesToClass(ClassEntry entry) {
		return this.fieldTypeReferences.get(entry);
	}

	public Collection<EntryReference<ClassEntry, MethodEntry>> getMethodTypeReferencesToClass(ClassEntry entry) {
		return this.methodTypeReferences.get(entry);
	}

	@Override
	public String getTranslationKey() {
		return "progress.jar.indexing.process.references";
	}
}
