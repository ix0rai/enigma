package cuchaz.enigma.source.procyon.index;

import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.languages.java.ast.AstNode;
import com.strobel.decompiler.languages.java.ast.DepthFirstAstVisitor;
import com.strobel.decompiler.languages.java.ast.Keys;
import com.strobel.decompiler.languages.java.ast.TypeDeclaration;
import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.source.SourceIndex;
import cuchaz.enigma.source.procyon.EntryParser;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class SourceIndexVisitor extends DepthFirstAstVisitor<SourceIndex, Void> {
	protected EntryIndex entryIndex;

	public SourceIndexVisitor(EntryIndex entryIndex) {
		this.entryIndex = entryIndex;
	}

	@Override
	public Void visitTypeDeclaration(TypeDeclaration node, SourceIndex index) {
		TypeDefinition def = node.getUserData(Keys.TYPE_DEFINITION);
		ClassEntry classEntry = EntryParser.parse(def, this.entryIndex);
		index.addDeclaration(TokenFactory.createToken(index, node.getNameToken()), classEntry);

		return node.acceptVisitor(new SourceIndexClassVisitor(classEntry, this.entryIndex), index);
	}

	@Override
	protected Void visitChildren(AstNode node, SourceIndex index) {
		for (final AstNode child : node.getChildren()) {
			child.acceptVisitor(this, index);
		}

		return null;
	}
}
