package cuchaz.enigma.analysis;

import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.analysis.index.ReferenceIndex;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

import javax.swing.tree.DefaultMutableTreeNode;

public class FieldReferenceTreeNode extends DefaultMutableTreeNode implements ReferenceTreeNode<FieldEntry, MethodEntry> {
	private final Translator translator;
	private final FieldEntry entry;
	private final EntryReference<FieldEntry, MethodEntry> reference;

	public FieldReferenceTreeNode(Translator translator, FieldEntry entry) {
		this.translator = translator;
		this.entry = entry;
		this.reference = null;
	}

	private FieldReferenceTreeNode(Translator translator, EntryReference<FieldEntry, MethodEntry> reference) {
		this.translator = translator;
		this.entry = reference.entry;
		this.reference = reference;
	}

	@Override
	public FieldEntry getEntry() {
		return this.entry;
	}

	@Override
	public EntryReference<FieldEntry, MethodEntry> getReference() {
		return this.reference;
	}

	@Override
	public String toString() {
		if (this.reference != null) {
			return String.format("%s", this.translator.translate(this.reference.context));
		}

		return this.translator.translate(this.entry).toString();
	}

	public void load(JarIndex index, boolean recurse) {
		ReferenceIndex referenceIndex = index.getReferenceIndex();

		// get all the child nodes
		if (this.reference == null) {
			for (EntryReference<FieldEntry, MethodEntry> reference : referenceIndex.getReferencesToField(this.entry)) {
				this.add(new FieldReferenceTreeNode(this.translator, reference));
			}
		} else {
			for (EntryReference<MethodEntry, MethodEntry> reference : referenceIndex.getReferencesToMethod(this.reference.context)) {
				this.add(new MethodReferenceTreeNode(this.translator, reference));
			}
		}

		if (recurse && this.children != null) {
			for (Object node : this.children) {
				if (node instanceof MethodReferenceTreeNode methodNode) {
					methodNode.load(index, true, false);
				} else if (node instanceof FieldReferenceTreeNode fieldNode) {
					fieldNode.load(index, true);
				}
			}
		}
	}
}
