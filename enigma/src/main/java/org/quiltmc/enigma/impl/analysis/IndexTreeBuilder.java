package org.quiltmc.enigma.impl.analysis;

import org.quiltmc.enigma.api.analysis.tree.ClassImplementationsTreeNode;
import org.quiltmc.enigma.api.analysis.tree.ClassInheritanceTreeNode;
import org.quiltmc.enigma.api.analysis.tree.MethodImplementationsTreeNode;
import org.quiltmc.enigma.api.analysis.tree.MethodInheritanceTreeNode;
import org.quiltmc.enigma.api.analysis.index.JarIndex;
import org.quiltmc.enigma.api.translation.Translator;
import org.quiltmc.enigma.api.translation.mapping.EntryResolver;
import org.quiltmc.enigma.api.translation.mapping.ResolutionStrategy;
import org.quiltmc.enigma.api.translation.representation.entry.ClassEntry;
import org.quiltmc.enigma.api.translation.representation.entry.MethodEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class IndexTreeBuilder {
	private final JarIndex index;

	public IndexTreeBuilder(JarIndex index) {
		this.index = index;
	}

	public ClassInheritanceTreeNode buildClassInheritance(Translator translator, ClassEntry obfClassEntry) {
		// get the root node
		List<String> ancestry = new ArrayList<>();
		ancestry.add(obfClassEntry.getFullName());
		for (ClassEntry classEntry : this.index.getInheritanceIndex().getAncestors(obfClassEntry)) {
			ancestry.add(classEntry.getFullName());
		}

		ClassInheritanceTreeNode rootNode = new ClassInheritanceTreeNode(translator, ancestry.get(ancestry.size() - 1));

		// expand all children recursively
		rootNode.load(this.index.getInheritanceIndex(), true);

		return rootNode;
	}

	public ClassImplementationsTreeNode buildClassImplementations(Translator translator, ClassEntry obfClassEntry) {
		if (this.index.getInheritanceIndex().isParent(obfClassEntry)) {
			ClassImplementationsTreeNode node = new ClassImplementationsTreeNode(translator, obfClassEntry);
			node.load(this.index);
			return node;
		}

		return null;
	}

	public MethodInheritanceTreeNode buildMethodInheritance(Translator translator, MethodEntry obfMethodEntry) {
		MethodEntry resolvedEntry = this.index.getEntryResolver().resolveFirstEntry(obfMethodEntry, ResolutionStrategy.RESOLVE_ROOT);

		// make a root node at the base
		MethodInheritanceTreeNode rootNode = new MethodInheritanceTreeNode(
				translator, resolvedEntry,
				this.index.getEntryIndex().hasMethod(resolvedEntry)
		);

		// expand the full tree
		rootNode.load(this.index);

		return rootNode;
	}

	public List<MethodImplementationsTreeNode> buildMethodImplementations(Translator translator, MethodEntry obfMethodEntry) {
		EntryResolver resolver = this.index.getEntryResolver();
		Collection<MethodEntry> resolvedEntries = resolver.resolveEntry(obfMethodEntry, ResolutionStrategy.RESOLVE_ROOT);

		List<MethodImplementationsTreeNode> nodes = new ArrayList<>();
		for (MethodEntry resolvedEntry : resolvedEntries) {
			MethodImplementationsTreeNode node = new MethodImplementationsTreeNode(translator, resolvedEntry);
			node.load(this.index);
			nodes.add(node);
		}

		return nodes;
	}
}