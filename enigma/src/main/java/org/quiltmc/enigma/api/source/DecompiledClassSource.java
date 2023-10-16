package org.quiltmc.enigma.api.source;

import org.quiltmc.enigma.api.EnigmaProject;
import org.quiltmc.enigma.api.EnigmaServices;
import org.quiltmc.enigma.api.analysis.EntryReference;
import org.quiltmc.enigma.api.service.NameProposalService;
import org.quiltmc.enigma.impl.translation.LocalNameGenerator;
import org.quiltmc.enigma.api.translation.TranslateResult;
import org.quiltmc.enigma.api.translation.Translator;
import org.quiltmc.enigma.api.translation.mapping.EntryRemapper;
import org.quiltmc.enigma.api.translation.mapping.ResolutionStrategy;
import org.quiltmc.enigma.api.translation.representation.TypeDescriptor;
import org.quiltmc.enigma.api.translation.representation.entry.ClassEntry;
import org.quiltmc.enigma.api.translation.representation.entry.Entry;
import org.quiltmc.enigma.api.translation.representation.entry.LocalVariableDefEntry;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DecompiledClassSource {
	protected static final boolean DEBUG_TOKEN_HIGHLIGHTS = false;

	private final ClassEntry classEntry;

	private final SourceIndex obfuscatedIndex;
	private final SourceIndex remappedIndex;

	private final TokenStore highlightedTokens;

	private DecompiledClassSource(ClassEntry classEntry, SourceIndex obfuscatedIndex, SourceIndex remappedIndex, TokenStore highlightedTokens) {
		this.classEntry = classEntry;
		this.obfuscatedIndex = obfuscatedIndex;
		this.remappedIndex = remappedIndex;
		this.highlightedTokens = highlightedTokens;
	}

	public DecompiledClassSource(ClassEntry classEntry, SourceIndex index) {
		this(classEntry, index, index, TokenStore.empty());
	}

	public static DecompiledClassSource text(ClassEntry classEntry, String text) {
		return new DecompiledClassSource(classEntry, new SourceIndex(text));
	}

	public DecompiledClassSource remapSource(EnigmaProject project, Translator translator) {
		SourceRemapper remapper = new SourceRemapper(this.obfuscatedIndex.getSource(), this.obfuscatedIndex.referenceTokens());

		TokenStore tokenStore = TokenStore.create(this.obfuscatedIndex);
		SourceRemapper.Result remapResult = remapper.remap((token, movedToken) -> this.remapToken(tokenStore, project, token, movedToken, translator));
		SourceIndex remappedIndex = this.obfuscatedIndex.remapTo(remapResult);
		return new DecompiledClassSource(this.classEntry, this.obfuscatedIndex, remappedIndex, tokenStore);
	}

	private String remapToken(TokenStore target, EnigmaProject project, Token token, Token movedToken, Translator translator) {
		EntryReference<Entry<?>, Entry<?>> reference = this.obfuscatedIndex.getReference(token);

		Entry<?> entry = reference.getNameableEntry();
		TranslateResult<Entry<?>> translatedEntry = translator.extendedTranslate(entry);

		if (project.isRenamable(reference)) {
			if (!translatedEntry.isObfuscated()) {
				target.add(translatedEntry.getType(), movedToken);
				return translatedEntry.getValue().getSourceRemapName();
			} else {
				Optional<String> proposedName = proposeName(project, entry);
				if (proposedName.isPresent()) {
					target.add(RenamableTokenType.PROPOSED, movedToken);
					return proposedName.get();
				}

				target.add(RenamableTokenType.OBFUSCATED, movedToken);
			}
		} else if (DEBUG_TOKEN_HIGHLIGHTS) {
			target.add(RenamableTokenType.DEBUG, movedToken);
		}

		String defaultName = this.generateDefaultName(translatedEntry.getValue());
		return defaultName;
	}

	public static Optional<String> proposeName(EnigmaProject project, Entry<?> entry) {
		EnigmaServices services = project.getEnigma().getServices();

		return services.get(NameProposalService.TYPE).stream().flatMap(nameProposalService -> {
			EntryRemapper mapper = project.getMapper();
			Collection<Entry<?>> resolved = mapper.getObfResolver().resolveEntry(entry, ResolutionStrategy.RESOLVE_ROOT);

			return resolved.stream()
					.map(e -> nameProposalService.proposeName(e, mapper))
					.filter(Optional::isPresent)
					.map(Optional::get);
		}).findFirst();
	}

	@Nullable
	private String generateDefaultName(Entry<?> entry) {
		if (entry instanceof LocalVariableDefEntry localVariable) {
			int index = localVariable.getIndex();
			if (localVariable.isArgument()) {
				List<TypeDescriptor> arguments = localVariable.getParent().getDesc().getTypeDescs();
				return LocalNameGenerator.generateArgumentName(index, localVariable.getDesc(), arguments);
			} else {
				return LocalNameGenerator.generateLocalVariableName(index, localVariable.getDesc());
			}
		}

		return null;
	}

	public ClassEntry getEntry() {
		return this.classEntry;
	}

	public SourceIndex getIndex() {
		return this.remappedIndex;
	}

	public TokenStore getTokenStore() {
		return this.highlightedTokens;
	}

	public Map<RenamableTokenType, ? extends Collection<Token>> getHighlightedTokens() {
		return this.highlightedTokens.getByType();
	}

	public int getObfuscatedOffset(int deobfOffset) {
		return getOffset(this.remappedIndex, this.obfuscatedIndex, deobfOffset);
	}

	public int getDeobfuscatedOffset(int obfOffset) {
		return getOffset(this.obfuscatedIndex, this.remappedIndex, obfOffset);
	}

	private static int getOffset(SourceIndex fromIndex, SourceIndex toIndex, int fromOffset) {
		int relativeOffset = 0;

		Iterator<Token> fromTokenItr = fromIndex.referenceTokens().iterator();
		Iterator<Token> toTokenItr = toIndex.referenceTokens().iterator();
		while (fromTokenItr.hasNext() && toTokenItr.hasNext()) {
			Token fromToken = fromTokenItr.next();
			Token toToken = toTokenItr.next();
			if (fromToken.end > fromOffset) {
				break;
			}

			relativeOffset = toToken.end - fromToken.end;
		}

		return fromOffset + relativeOffset;
	}

	@Override
	public String toString() {
		return this.remappedIndex.getSource();
	}
}