package cuchaz.enigma;

import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.classprovider.CachingClassProvider;
import cuchaz.enigma.classprovider.JarClassProvider;
import cuchaz.enigma.source.Decompilers;
import cuchaz.enigma.translation.mapping.EntryRemapper;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static cuchaz.enigma.TestEntryFactory.newBehaviorReferenceByMethod;
import static cuchaz.enigma.TestEntryFactory.newMethod;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class TestTokensConstructors extends TokenChecker {
	private static final Path JAR = TestUtil.obfJar("constructors");
	private static final JarIndex index;

	static {
		JarClassProvider jcp;
		try {
			jcp = new JarClassProvider(JAR);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		index = JarIndex.empty();
		index.indexJar(jcp.getClassNames(), new CachingClassProvider(jcp), ProgressListener.none());
	}

	public TestTokensConstructors() throws Exception {
		super(JAR, Decompilers.PROCYON, EntryRemapper.empty(index)); // Procyon is the only one that indexes constructor invocations
	}

	@Test
	public void baseDeclarations() {
		assertThat(this.getDeclarationToken(newMethod("a", "<init>", "()V")), is("a"));
		assertThat(this.getDeclarationToken(newMethod("a", "<init>", "(I)V")), is("a"));
	}

	@Test
	public void subDeclarations() {
		assertThat(this.getDeclarationToken(newMethod("d", "<init>", "()V")), is("d"));
		assertThat(this.getDeclarationToken(newMethod("d", "<init>", "(I)V")), is("d"));
		assertThat(this.getDeclarationToken(newMethod("d", "<init>", "(II)V")), is("d"));
		assertThat(this.getDeclarationToken(newMethod("d", "<init>", "(III)V")), is("d"));
	}

	@Test
	public void subsubDeclarations() {
		assertThat(this.getDeclarationToken(newMethod("e", "<init>", "(I)V")), is("e"));
	}

	@Test
	public void defaultDeclarations() {
		assertThat(this.getDeclarationToken(newMethod("c", "<init>", "()V")), nullValue());
	}

	@Test
	public void baseDefaultReferences() {
		MethodEntry source = newMethod("a", "<init>", "()V");
		assertThat(
			this.getReferenceTokens(newBehaviorReferenceByMethod(source, "b", "a", "()V")),
				containsInAnyOrder("a")
		);
		assertThat(
			this.getReferenceTokens(newBehaviorReferenceByMethod(source, "d", "<init>", "()V")),
				is(empty()) // implicit call, not decompiled to token
		);
		assertThat(
			this.getReferenceTokens(newBehaviorReferenceByMethod(source, "d", "<init>", "(III)V")),
				is(empty()) // implicit call, not decompiled to token
		);
	}

	@Test
	public void baseIntReferences() {
		MethodEntry source = newMethod("a", "<init>", "(I)V");
		assertThat(
			this.getReferenceTokens(newBehaviorReferenceByMethod(source, "b", "b", "()V")),
				containsInAnyOrder("a")
		);
	}

	@Test
	public void subDefaultReferences() {
		MethodEntry source = newMethod("d", "<init>", "()V");
		assertThat(
			this.getReferenceTokens(newBehaviorReferenceByMethod(source, "b", "c", "()V")),
				containsInAnyOrder("d")
		);
		assertThat(
			this.getReferenceTokens(newBehaviorReferenceByMethod(source, "d", "<init>", "(I)V")),
				containsInAnyOrder("this")
		);
	}

	@Test
	public void subIntReferences() {
		MethodEntry source = newMethod("d", "<init>", "(I)V");
		assertThat(this.getReferenceTokens(
				newBehaviorReferenceByMethod(source, "b", "d", "()V")),
				containsInAnyOrder("d")
		);
		assertThat(this.getReferenceTokens(
				newBehaviorReferenceByMethod(source, "d", "<init>", "(II)V")),
				containsInAnyOrder("this")
		);
		assertThat(this.getReferenceTokens(
				newBehaviorReferenceByMethod(source, "e", "<init>", "(I)V")),
				containsInAnyOrder("super")
		);
	}

	@Test
	public void subIntIntReferences() {
		MethodEntry source = newMethod("d", "<init>", "(II)V");
		assertThat(
			this.getReferenceTokens(newBehaviorReferenceByMethod(source, "b", "e", "()V")),
				containsInAnyOrder("d")
		);
	}

	@Test
	public void subsubIntReferences() {
		MethodEntry source = newMethod("e", "<init>", "(I)V");
		assertThat(
			this.getReferenceTokens(newBehaviorReferenceByMethod(source, "b", "f", "()V")),
				containsInAnyOrder("e")
		);
	}

	@Test
	public void defaultConstructableReferences() {
		MethodEntry source = newMethod("c", "<init>", "()V");
		assertThat(
			this.getReferenceTokens(newBehaviorReferenceByMethod(source, "b", "g", "()V")),
				containsInAnyOrder("c")
		);
	}
}
