package cuchaz.enigma;

import cuchaz.enigma.analysis.EntryReference;
import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.classprovider.CachingClassProvider;
import cuchaz.enigma.classprovider.JarClassProvider;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class TestJarIndexConstructorReferences {
	public static final Path JAR = TestUtil.obfJar("constructors");

	private final ClassEntry baseClass;
	private final ClassEntry subClass;
	private final ClassEntry subsubClass;
	private final ClassEntry defaultClass;
	private final ClassEntry callerClass;

	private final JarIndex index;

	public TestJarIndexConstructorReferences() throws Exception {
		JarClassProvider jcp = new JarClassProvider(JAR);
		this.index = JarIndex.empty();
		this.index.indexJar(jcp.getClassNames(), new CachingClassProvider(jcp), ProgressListener.none());
		baseClass = newClass("a");
		subClass = newClass("d");
		subsubClass = newClass("e");
		defaultClass = newClass("c");
		callerClass = newClass("b");
	}

	@Test
	public void obfEntries() {
		assertThat(this.index.getEntryIndex().getClasses(), containsInAnyOrder(newClass("cuchaz/enigma/inputs/Keep"), baseClass,
			subClass, subsubClass, defaultClass, callerClass));
	}

	@Test
	public void baseDefault() {
		MethodEntry source = newMethod(baseClass, "<init>", "()V");
		Collection<EntryReference<MethodEntry, MethodEntry>> references = this.index.getReferenceIndex().getReferencesToMethod(source);
		assertThat(references, containsInAnyOrder(
				newBehaviorReferenceByMethod(source, callerClass.getName(), "a", "()V"),
				newBehaviorReferenceByMethod(source, subClass.getName(), "<init>", "()V"),
				newBehaviorReferenceByMethod(source, subClass.getName(), "<init>", "(III)V")
		));
	}

	@Test
	public void baseInt() {
		MethodEntry source = newMethod(baseClass, "<init>", "(I)V");
		assertThat(this.index.getReferenceIndex().getReferencesToMethod(source), containsInAnyOrder(
				newBehaviorReferenceByMethod(source, callerClass.getName(), "b", "()V")
		));
	}

	@Test
	public void subDefault() {
		MethodEntry source = newMethod(subClass, "<init>", "()V");
		assertThat(this.index.getReferenceIndex().getReferencesToMethod(source), containsInAnyOrder(
				newBehaviorReferenceByMethod(source, callerClass.getName(), "c", "()V"),
				newBehaviorReferenceByMethod(source, subClass.getName(), "<init>", "(I)V")
		));
	}

	@Test
	public void subInt() {
		MethodEntry source = newMethod(subClass, "<init>", "(I)V");
		assertThat(this.index.getReferenceIndex().getReferencesToMethod(source), containsInAnyOrder(
				newBehaviorReferenceByMethod(source, callerClass.getName(), "d", "()V"),
				newBehaviorReferenceByMethod(source, subClass.getName(), "<init>", "(II)V"),
				newBehaviorReferenceByMethod(source, subsubClass.getName(), "<init>", "(I)V")
		));
	}

	@Test
	public void subIntInt() {
		MethodEntry source = newMethod(subClass, "<init>", "(II)V");
		assertThat(this.index.getReferenceIndex().getReferencesToMethod(source), containsInAnyOrder(
				newBehaviorReferenceByMethod(source, callerClass.getName(), "e", "()V")
		));
	}

	@Test
	public void subIntIntInt() {
		MethodEntry source = newMethod(subClass, "<init>", "(III)V");
		assertThat(this.index.getReferenceIndex().getReferencesToMethod(source), is(empty()));
	}

	@Test
	public void subsubInt() {
		MethodEntry source = newMethod(subsubClass, "<init>", "(I)V");
		assertThat(this.index.getReferenceIndex().getReferencesToMethod(source), containsInAnyOrder(
				newBehaviorReferenceByMethod(source, callerClass.getName(), "f", "()V")
		));
	}

	@Test
	public void defaultConstructable() {
		MethodEntry source = newMethod(defaultClass, "<init>", "()V");
		assertThat(this.index.getReferenceIndex().getReferencesToMethod(source), containsInAnyOrder(
				newBehaviorReferenceByMethod(source, callerClass.getName(), "g", "()V")
		));
	}

	private ClassEntry newClass(String obfName) {
		return this.index.getEntryIndex().getClass(obfName);
	}

	public EntryReference<MethodEntry, MethodEntry> newBehaviorReferenceByMethod(MethodEntry methodEntry, String callerClassName, String callerName, String callerSignature) {
		return new EntryReference<>(methodEntry, "", newMethod(this.index.getEntryIndex().getClass(callerClassName), callerName, callerSignature));
	}

	public MethodEntry newMethod(ClassEntry className, String methodName, String methodSignature) {
		return this.index.getEntryIndex().getMethod(className, methodName, methodSignature);
	}
}
