package cuchaz.enigma.source.vineflower;

import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.source.SourceIndex;
import cuchaz.enigma.source.Token;
import cuchaz.enigma.translation.mapping.EntryRemapper;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import org.jetbrains.java.decompiler.main.extern.TextTokenVisitor;
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.util.Pair;
import org.jetbrains.java.decompiler.util.token.TextRange;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

public class EnigmaTextTokenCollector extends TextTokenVisitor {
	private String content;
	private MethodEntry currentMethod;

	private final Map<Token, Entry<?>> declarations = new HashMap<>();
	private final Map<Token, Pair<Entry<?>, Entry<?>>> references = new HashMap<>();
	private final Map<Token, Boolean> tokens = new LinkedHashMap<>();
	private final EntryIndex entryIndex;

	public EnigmaTextTokenCollector(TextTokenVisitor next, EntryRemapper remapper) {
		super(next);
		this.entryIndex = remapper.getJarIndex().getEntryIndex();
	}

	private Token getToken(TextRange range) {
		return new Token(range.start, range.start + range.length, this.content.substring(range.start, range.start + range.length));
	}

	private void addDeclaration(Token token, Entry<?> entry) {
		this.declarations.put(token, entry);
		this.tokens.put(token, true);
	}

	private void addReference(Token token, Entry<?> entry, Entry<?> context) {
		this.references.put(token, Pair.of(entry, context));
		this.tokens.put(token, false);
	}

	public void addTokensToIndex(SourceIndex index, UnaryOperator<Token> tokenProcessor) {
		for (Token token : this.tokens.keySet()) {
			Token newToken = tokenProcessor.apply(token);
			if (newToken == null) {
				continue;
			}

			if (this.tokens.get(token)) {
				index.addDeclaration(newToken, this.declarations.get(token));
			} else {
				Pair<Entry<?>, Entry<?>> ref = this.references.get(token);
				index.addReference(newToken, ref.a, ref.b);
			}
		}
	}

	@Override
	public void start(String content) {
		this.content = content;
		this.currentMethod = null;
	}

	@Override
	public void visitClass(TextRange range, boolean declaration, String name) {
		super.visitClass(range, declaration, name);
		Token token = this.getToken(range);

		if (declaration) {
			this.addDeclaration(token, this.entryIndex.getClass(name));
		} else {
			this.addReference(token, this.entryIndex.getClass(name), this.currentMethod);
		}
	}

	@Override
	public void visitField(TextRange range, boolean declaration, String className, String name, FieldDescriptor descriptor) {
		super.visitField(range, declaration, className, name, descriptor);
		Token token = this.getToken(range);

		if (declaration) {
			this.addDeclaration(token, this.entryIndex.getField(this.entryIndex.getClass(className), name, descriptor.descriptorString));
		} else {
			this.addReference(token, this.entryIndex.getField(this.entryIndex.getClass(className), name, descriptor.descriptorString), this.currentMethod);
		}
	}

	@Override
	public void visitMethod(TextRange range, boolean declaration, String className, String name, MethodDescriptor descriptor) {
		super.visitMethod(range, declaration, className, name, descriptor);
		Token token = this.getToken(range);
		MethodEntry entry = this.entryIndex.getMethod(this.entryIndex.getClass(className), name, descriptor.toString());

		if (token.text.equals("new")) {
			return;
		}

		if (declaration) {
			this.addDeclaration(token, entry);
			this.currentMethod = entry;
		} else {
			this.addReference(token, entry, this.currentMethod);
		}
	}

	@Override
	public void visitParameter(TextRange range, boolean declaration, String className, String methodName, MethodDescriptor methodDescriptor, int idx, String name) {
		super.visitParameter(range, declaration, className, methodName, methodDescriptor, idx, name);
		Token token = this.getToken(range);
		MethodEntry parent = this.entryIndex.getMethod(this.entryIndex.getClass(className), methodName, methodDescriptor.toString());

		if (declaration) {
			this.addDeclaration(token, this.entryIndex.getLocalVariable(parent, idx, name, true));
		} else {
			this.addReference(token, this.entryIndex.getLocalVariable(parent, idx, name, true), this.currentMethod);
		}
	}

	@Override
	public void visitLocal(TextRange range, boolean declaration, String className, String methodName, MethodDescriptor methodDescriptor, int idx, String name) {
		super.visitLocal(range, declaration, className, methodName, methodDescriptor, idx, name);
		Token token = this.getToken(range);
		MethodEntry parent = this.entryIndex.getMethod(this.entryIndex.getClass(className), methodName, methodDescriptor.toString());

		if (declaration) {
			this.addDeclaration(token, this.entryIndex.getLocalVariable(parent, idx, name, false));
		} else {
			this.addReference(token, this.entryIndex.getLocalVariable(parent, idx, name, false), this.currentMethod);
		}
	}
}
