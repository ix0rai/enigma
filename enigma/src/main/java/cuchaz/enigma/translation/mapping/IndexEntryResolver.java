package cuchaz.enigma.translation.mapping;

import cuchaz.enigma.analysis.IndexTreeBuilder;
import cuchaz.enigma.analysis.MethodImplementationsTreeNode;
import cuchaz.enigma.analysis.MethodInheritanceTreeNode;
import cuchaz.enigma.analysis.index.BridgeMethodIndex;
import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.analysis.index.InheritanceIndex;
import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.translation.VoidTranslator;
import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.DefinedEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

public class IndexEntryResolver implements EntryResolver {
	private final EntryIndex entryIndex;
	private final InheritanceIndex inheritanceIndex;
	private final BridgeMethodIndex bridgeMethodIndex;

	private final IndexTreeBuilder treeBuilder;

	public IndexEntryResolver(JarIndex index) {
		this.entryIndex = index.getEntryIndex();
		this.inheritanceIndex = index.getInheritanceIndex();
		this.bridgeMethodIndex = index.getBridgeMethodIndex();

		this.treeBuilder = new IndexTreeBuilder(index);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <E extends Entry<?>> Collection<E> resolveEntry(E entry, ResolutionStrategy strategy) {
		if (entry == null) {
			return Collections.emptySet();
		}

		DefinedEntry<ClassEntry, ?> classChild = this.getClassChild(entry);
		if (!(classChild instanceof ClassEntry) && classChild != null) {
			AccessFlags access = classChild.getAccess();

			// If we're looking for the closest and this entry exists, we're done looking
			if (strategy == ResolutionStrategy.RESOLVE_CLOSEST && access != null) {
				return Collections.singleton(entry);
			}

			if (access == null || (!access.isPrivate() && !access.isStatic())) {
				Collection<DefinedEntry<ClassEntry, ?>> resolvedChildren = this.resolveChildEntry(classChild, strategy);
				if (!resolvedChildren.isEmpty()) {
					return resolvedChildren.stream()
							.map(resolvedChild -> (E) entry.replaceAncestor(classChild, resolvedChild))
							.toList();
				}
			}
		}

		return Collections.singleton(entry);
	}

	@Nullable
	private DefinedEntry<ClassEntry, ?> getClassChild(Entry<?> entry) {
		if (entry instanceof ClassEntry) {
			return null;
		}

		// get the entry in the hierarchy that is the child of a class
		List<Entry<?>> ancestry = entry.getAncestry();
		for (int i = ancestry.size() - 1; i > 0; i--) {
			Entry<?> child = ancestry.get(i);
			DefinedEntry<ClassEntry, ?> cast = child.castParent(ClassEntry.class);
			if (!(cast instanceof ClassEntry) && cast != null) {
				// we found the entry which is a child of a class, we are now able to resolve the owner of this entry
				return cast;
			}
		}

		return null;
	}

	private Set<DefinedEntry<ClassEntry, ?>> resolveChildEntry(DefinedEntry<ClassEntry, ?> entry, ResolutionStrategy strategy) {
		ClassEntry ownerClass = entry.getParent();

		if (entry instanceof MethodEntry methodEntry) {
			MethodEntry bridgeMethod = this.bridgeMethodIndex.getBridgeFromSpecialized(methodEntry);
			if (bridgeMethod != null && ownerClass.equals(bridgeMethod.getParent())) {
				Set<DefinedEntry<ClassEntry, ?>> resolvedBridge = this.resolveChildEntry(bridgeMethod, strategy);
				if (!resolvedBridge.isEmpty()) {
					return resolvedBridge;
				} else {
					return Collections.singleton(bridgeMethod);
				}
			}
		}

		Set<DefinedEntry<ClassEntry, ?>> resolvedEntries = new HashSet<>();

		for (ClassEntry parentClass : this.inheritanceIndex.getParents(ownerClass)) {
			entry.setParent(parentClass);

			if (strategy == ResolutionStrategy.RESOLVE_ROOT) {
				resolvedEntries.addAll(this.resolveRoot(entry, strategy));
			} else {
				resolvedEntries.addAll(this.resolveClosest(entry, strategy));
			}
		}

		return resolvedEntries;
	}

	private Collection<DefinedEntry<ClassEntry, ?>> resolveRoot(DefinedEntry<ClassEntry, ?> entry, ResolutionStrategy strategy) {
		// When resolving root, we want to first look for the lowest entry before returning ourselves
		Set<DefinedEntry<ClassEntry, ?>> parentResolution = this.resolveChildEntry(entry, strategy);

		if (parentResolution.isEmpty()) {
			AccessFlags parentAccess = entry.getAccess();
			if (parentAccess != null && !parentAccess.isPrivate()) {
				return Collections.singleton(entry);
			}
		}

		return parentResolution;
	}

	private Collection<DefinedEntry<ClassEntry, ?>> resolveClosest(DefinedEntry<ClassEntry, ?> entry, ResolutionStrategy strategy) {
		// When resolving closest, we want to first check if we exist before looking further down
		AccessFlags parentAccess = entry.getAccess();
		if (parentAccess != null && !parentAccess.isPrivate()) {
			return Collections.singleton(entry);
		} else {
			return this.resolveChildEntry(entry, strategy);
		}
	}

	@Override
	public Set<Entry<?>> resolveEquivalentEntries(Entry<?> entry) {
		MethodEntry relevantMethod = entry.findAncestor(MethodEntry.class);
		if (relevantMethod == null || !this.entryIndex.isInJar(relevantMethod)) {
			return Collections.singleton(entry);
		}

		Set<MethodEntry> equivalentMethods = this.resolveEquivalentMethods(relevantMethod);
		Set<Entry<?>> equivalentEntries = new HashSet<>(equivalentMethods.size());

		for (MethodEntry equivalentMethod : equivalentMethods) {
			Entry<?> equivalentEntry = entry.replaceAncestor(relevantMethod, equivalentMethod);
			equivalentEntries.add(equivalentEntry);
		}

		return equivalentEntries;
	}

	@Override
	public Set<MethodEntry> resolveEquivalentMethods(MethodEntry methodEntry) {
		Set<MethodEntry> set = new HashSet<>();
		this.resolveEquivalentMethods(set, methodEntry);
		return set;
	}

	private void resolveEquivalentMethods(Set<MethodEntry> methodEntries, MethodEntry methodEntry) {
		AccessFlags access = methodEntry.getAccess();
		if (access == null) {
			throw new IllegalArgumentException("Could not find method " + methodEntry);
		}

		if (!this.canInherit(methodEntry, access)) {
			methodEntries.add(methodEntry);
			return;
		}

		this.resolveEquivalentMethods(methodEntries, this.treeBuilder.buildMethodInheritance(VoidTranslator.INSTANCE, methodEntry));
	}

	private void resolveEquivalentMethods(Set<MethodEntry> methodEntries, MethodInheritanceTreeNode node) {
		MethodEntry methodEntry = node.getMethodEntry();
		if (methodEntries.contains(methodEntry)) {
			return;
		}

		AccessFlags flags = methodEntry.getAccess();
		if (flags != null && this.canInherit(methodEntry, flags)) {
			// collect the entry
			methodEntries.add(methodEntry);
		}

		// look at bridge methods!
		MethodEntry bridgedMethod = this.bridgeMethodIndex.getBridgeFromSpecialized(methodEntry);
		while (bridgedMethod != null) {
			this.resolveEquivalentMethods(methodEntries, bridgedMethod);
			bridgedMethod = this.bridgeMethodIndex.getBridgeFromSpecialized(bridgedMethod);
		}

		// look at interface methods too
		for (MethodImplementationsTreeNode implementationsNode : this.treeBuilder.buildMethodImplementations(VoidTranslator.INSTANCE, methodEntry)) {
			this.resolveEquivalentMethods(methodEntries, implementationsNode);
		}

		// recurse
		for (int i = 0; i < node.getChildCount(); i++) {
			this.resolveEquivalentMethods(methodEntries, (MethodInheritanceTreeNode) node.getChildAt(i));
		}
	}

	private void resolveEquivalentMethods(Set<MethodEntry> methodEntries, MethodImplementationsTreeNode node) {
		MethodEntry methodEntry = node.getMethodEntry();
		AccessFlags flags = methodEntry.getAccess();
		if (flags != null && !flags.isPrivate() && !flags.isStatic()) {
			// collect the entry
			methodEntries.add(methodEntry);
		}

		// look at bridge methods!
		MethodEntry bridgedMethod = this.bridgeMethodIndex.getBridgeFromSpecialized(methodEntry);
		while (bridgedMethod != null) {
			this.resolveEquivalentMethods(methodEntries, bridgedMethod);
			bridgedMethod = this.bridgeMethodIndex.getBridgeFromSpecialized(bridgedMethod);
		}

		// recurse
		for (int i = 0; i < node.getChildCount(); i++) {
			this.resolveEquivalentMethods(methodEntries, (MethodImplementationsTreeNode) node.getChildAt(i));
		}
	}

	private boolean canInherit(MethodEntry entry, AccessFlags access) {
		return !entry.isConstructor() && !access.isPrivate() && !access.isStatic() && !access.isFinal();
	}
}
