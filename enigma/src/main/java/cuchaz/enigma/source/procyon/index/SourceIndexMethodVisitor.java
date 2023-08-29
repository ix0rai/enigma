package cuchaz.enigma.source.procyon.index;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MemberReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.assembler.metadata.VariableDefinition;
import com.strobel.decompiler.ast.Variable;
import com.strobel.decompiler.languages.TextLocation;
import com.strobel.decompiler.languages.java.ast.AstNode;
import com.strobel.decompiler.languages.java.ast.AstNodeCollection;
import com.strobel.decompiler.languages.java.ast.Identifier;
import com.strobel.decompiler.languages.java.ast.IdentifierExpression;
import com.strobel.decompiler.languages.java.ast.InvocationExpression;
import com.strobel.decompiler.languages.java.ast.Keys;
import com.strobel.decompiler.languages.java.ast.MemberReferenceExpression;
import com.strobel.decompiler.languages.java.ast.MethodGroupExpression;
import com.strobel.decompiler.languages.java.ast.ObjectCreationExpression;
import com.strobel.decompiler.languages.java.ast.ParameterDeclaration;
import com.strobel.decompiler.languages.java.ast.SimpleType;
import com.strobel.decompiler.languages.java.ast.SuperReferenceExpression;
import com.strobel.decompiler.languages.java.ast.ThisReferenceExpression;
import com.strobel.decompiler.languages.java.ast.VariableDeclarationStatement;
import com.strobel.decompiler.languages.java.ast.VariableInitializer;
import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.source.SourceIndex;
import cuchaz.enigma.source.procyon.EntryParser;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

import java.util.HashMap;
import java.util.Map;

public class SourceIndexMethodVisitor extends SourceIndexVisitor {
	private final MethodEntry methodEntry;

	private final Multimap<String, Identifier> unmatchedIdentifier = HashMultimap.create();
	private final Map<String, Entry<?>> identifierEntryCache = new HashMap<>();

	public SourceIndexMethodVisitor(MethodEntry methodEntry, EntryIndex entryIndex) {
		super(entryIndex);
		this.methodEntry = methodEntry;
	}

	@Override
	public Void visitInvocationExpression(InvocationExpression node, SourceIndex index) {
		MemberReference ref = node.getUserData(Keys.MEMBER_REFERENCE);

		if (ref != null) {
			// get the behavior entry
			ClassEntry classEntry = this.entryIndex.getClass(ref.getDeclaringType().getInternalName());
			MethodEntry methodEntry = null;
			if (ref instanceof MethodReference) {
				methodEntry = new MethodEntry(classEntry, ref.getName(), new MethodDescriptor(ref.getErasedSignature()));
			}

			if (methodEntry != null) {
				// get the node for the token
				AstNode tokenNode = null;
				if (node.getTarget() instanceof MemberReferenceExpression) {
					tokenNode = ((MemberReferenceExpression) node.getTarget()).getMemberNameToken();
				} else if (node.getTarget() instanceof SuperReferenceExpression) {
					tokenNode = node.getTarget();
				} else if (node.getTarget() instanceof ThisReferenceExpression) {
					tokenNode = node.getTarget();
				}

				if (tokenNode != null) {
					index.addReference(TokenFactory.createToken(index, tokenNode), methodEntry, this.methodEntry);
				}
			}
		}

		// Check for identifier
		node.getArguments().stream().filter(expression -> expression instanceof IdentifierExpression)
				.forEach(expression -> this.checkIdentifier((IdentifierExpression) expression, index));
		return this.visitChildren(node, index);
	}

	@Override
	public Void visitMemberReferenceExpression(MemberReferenceExpression node, SourceIndex index) {
		MemberReference ref = node.getUserData(Keys.MEMBER_REFERENCE);
		if (ref instanceof FieldReference) {
			// make sure this is actually a field
			String erasedSignature = ref.getErasedSignature();
			if (erasedSignature.indexOf('(') >= 0) {
				throw new Error("Expected a field here! got " + ref);
			}

			ClassEntry classEntry = this.entryIndex.getClass(ref.getDeclaringType().getInternalName());
			FieldEntry fieldEntry = new FieldEntry(classEntry, ref.getName(), new TypeDescriptor(erasedSignature));
			index.addReference(TokenFactory.createToken(index, node.getMemberNameToken()), fieldEntry, this.methodEntry);
		}

		return this.visitChildren(node, index);
	}

	@Override
	public Void visitSimpleType(SimpleType node, SourceIndex index) {
		TypeReference ref = node.getUserData(Keys.TYPE_REFERENCE);
		if (node.getIdentifierToken().getStartLocation() != TextLocation.EMPTY) {
			ClassEntry classEntry = this.entryIndex.getClass(ref.getInternalName());
			index.addReference(TokenFactory.createToken(index, node.getIdentifierToken()), classEntry, this.methodEntry);
		}

		return this.visitChildren(node, index);
	}

	@Override
	public Void visitParameterDeclaration(ParameterDeclaration node, SourceIndex index) {
		ParameterDefinition def = node.getUserData(Keys.PARAMETER_DEFINITION);
		int parameterIndex = def.getSlot();

		if (parameterIndex >= 0) {
			MethodEntry ownerMethod = this.methodEntry;
			if (def.getMethod() instanceof MethodDefinition definition) {
				ownerMethod = EntryParser.parse(definition, this.entryIndex);
			}

			TypeDescriptor parameterType = EntryParser.parseTypeDescriptor(def.getParameterType());
			LocalVariableEntry localVariableEntry = new LocalVariableEntry(ownerMethod, parameterIndex, node.getName(), true, parameterType, EntryMapping.DEFAULT);
			Identifier identifier = node.getNameToken();
			// cache the argument entry and the identifier
			this.identifierEntryCache.put(identifier.getName(), localVariableEntry);
			index.addDeclaration(TokenFactory.createToken(index, identifier), localVariableEntry);
		}

		return this.visitChildren(node, index);
	}

	@Override
	public Void visitIdentifierExpression(IdentifierExpression node, SourceIndex index) {
		MemberReference ref = node.getUserData(Keys.MEMBER_REFERENCE);
		if (ref != null) {
			ClassEntry classEntry = this.entryIndex.getClass(ref.getDeclaringType().getInternalName());
			FieldEntry fieldEntry = new FieldEntry(classEntry, ref.getName(), new TypeDescriptor(ref.getErasedSignature()));
			index.addReference(TokenFactory.createToken(index, node.getIdentifierToken()), fieldEntry, this.methodEntry);
		} else {
			this.checkIdentifier(node, index);
		}

		return this.visitChildren(node, index);
	}

	private void checkIdentifier(IdentifierExpression node, SourceIndex index) {
		if (this.identifierEntryCache.containsKey(node.getIdentifier())) { // If it's in the argument cache, create a token!
			index.addDeclaration(TokenFactory.createToken(index, node.getIdentifierToken()), this.identifierEntryCache.get(node.getIdentifier()));
		} else {
			this.unmatchedIdentifier.put(node.getIdentifier(), node.getIdentifierToken()); // Not matched actually, put it!
		}
	}

	private void addDeclarationToUnmatched(String key, SourceIndex index) {
		Entry<?> entry = this.identifierEntryCache.get(key);

		// This cannot happen in theory
		if (entry == null) {
			return;
		}

		for (Identifier identifier : this.unmatchedIdentifier.get(key)) {
			index.addDeclaration(TokenFactory.createToken(index, identifier), entry);
		}

		this.unmatchedIdentifier.removeAll(key);
	}

	@Override
	public Void visitObjectCreationExpression(ObjectCreationExpression node, SourceIndex index) {
		MemberReference ref = node.getUserData(Keys.MEMBER_REFERENCE);
		if (ref != null && node.getType() instanceof SimpleType simpleTypeNode) {
			ClassEntry classEntry = this.entryIndex.getClass(ref.getDeclaringType().getInternalName());
			MethodEntry constructorEntry = new MethodEntry(classEntry, "<init>", new MethodDescriptor(ref.getErasedSignature()));
			index.addReference(TokenFactory.createToken(index, simpleTypeNode.getIdentifierToken()), constructorEntry, this.methodEntry);
		}

		return this.visitChildren(node, index);
	}

	@Override
	public Void visitVariableDeclaration(VariableDeclarationStatement node, SourceIndex index) {
		AstNodeCollection<VariableInitializer> variables = node.getVariables();

		// Single assignation
		if (variables.size() == 1) {
			VariableInitializer initializer = variables.firstOrNullObject();
			if (initializer != null && node.getType() instanceof SimpleType) {
				Identifier identifier = initializer.getNameToken();
				Variable variable = initializer.getUserData(Keys.VARIABLE);
				if (variable != null) {
					VariableDefinition originalVariable = variable.getOriginalVariable();
					if (originalVariable != null) {
						int variableIndex = originalVariable.getSlot();
						if (variableIndex >= 0) {
							MethodEntry ownerMethod = EntryParser.parse(originalVariable.getDeclaringMethod(), this.entryIndex);
							TypeDescriptor variableType = EntryParser.parseTypeDescriptor(originalVariable.getVariableType());
							LocalVariableEntry localVariableEntry = new LocalVariableEntry(ownerMethod, variableIndex, initializer.getName(), false, variableType, null);
							this.identifierEntryCache.put(identifier.getName(), localVariableEntry);
							this.addDeclarationToUnmatched(identifier.getName(), index);
							index.addDeclaration(TokenFactory.createToken(index, identifier), localVariableEntry);
						}
					}
				}
			}
		}

		return this.visitChildren(node, index);
	}

	@Override
	public Void visitMethodGroupExpression(MethodGroupExpression node, SourceIndex index) {
		MemberReference ref = node.getUserData(Keys.MEMBER_REFERENCE);

		if (ref instanceof MethodReference) {
			// get the behavior entry
			ClassEntry classEntry = this.entryIndex.getClass(ref.getDeclaringType().getInternalName());
			MethodEntry methodEntry = new MethodEntry(classEntry, ref.getName(), new MethodDescriptor(ref.getErasedSignature()));

			// get the node for the token
			AstNode methodNameToken = node.getMethodNameToken();
			AstNode targetToken = node.getTarget();

			if (methodNameToken != null) {
				index.addReference(TokenFactory.createToken(index, methodNameToken), methodEntry, this.methodEntry);
			}

			if (targetToken != null && !(targetToken instanceof ThisReferenceExpression)) {
				index.addReference(TokenFactory.createToken(index, targetToken), methodEntry.getParent(), this.methodEntry);
			}
		}

		return this.visitChildren(node, index);
	}
}
