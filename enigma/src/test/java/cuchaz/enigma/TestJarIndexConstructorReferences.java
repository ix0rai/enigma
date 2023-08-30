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
		this.baseClass = this.newClass("a");
		this.subClass = this.newClass("d");
		this.subsubClass = this.newClass("e");
		this.defaultClass = this.newClass("c");
		this.callerClass = this.newClass("b");
	}

	@Test
	public void obfEntries() {
		System.out.println(this.index.getEntryIndex().getClasses());
		assertThat(this.index.getEntryIndex().getClasses(), containsInAnyOrder(this.newClass("cuchaz/enigma/inputs/Keep"), this.baseClass,
			this.subClass, this.subsubClass, this.defaultClass, this.callerClass));
	}

	@Test
	public void baseDefault() {
		MethodEntry source = this.newMethod(this.baseClass, "<init>", "()V");
		Collection<EntryReference<MethodEntry, MethodEntry>> references = this.index.getReferenceIndex().getReferencesToMethod(source);
		assertThat(references, containsInAnyOrder(
			this.newBehaviorReferenceByMethod(source, this.callerClass.getName(), "a", "()V"),
			this.newBehaviorReferenceByMethod(source, this.subClass.getName(), "<init>", "()V"),
			this.newBehaviorReferenceByMethod(source, this.subClass.getName(), "<init>", "(III)V")
		));
	}

	@Test
	public void baseInt() {
		MethodEntry source = this.newMethod(this.baseClass, "<init>", "(I)V");
		assertThat(this.index.getReferenceIndex().getReferencesToMethod(source), containsInAnyOrder(
			this.newBehaviorReferenceByMethod(source, this.callerClass.getName(), "b", "()V")
		));
	}

	@Test
	public void subDefault() {
		MethodEntry source = this.newMethod(this.subClass, "<init>", "()V");
		assertThat(this.index.getReferenceIndex().getReferencesToMethod(source), containsInAnyOrder(
			this.newBehaviorReferenceByMethod(source, this.callerClass.getName(), "c", "()V"),
			this.newBehaviorReferenceByMethod(source, this.subClass.getName(), "<init>", "(I)V")
		));
	}

	@Test
	public void subInt() {
		MethodEntry source = this.newMethod(this.subClass, "<init>", "(I)V");
		assertThat(this.index.getReferenceIndex().getReferencesToMethod(source), containsInAnyOrder(
			this.newBehaviorReferenceByMethod(source, this.callerClass.getName(), "d", "()V"),
			this.newBehaviorReferenceByMethod(source, this.subClass.getName(), "<init>", "(II)V"),
			this.newBehaviorReferenceByMethod(source, this.subsubClass.getName(), "<init>", "(I)V")
		));
	}

	@Test
	public void subIntInt() {
		MethodEntry source = this.newMethod(this.subClass, "<init>", "(II)V");
		assertThat(this.index.getReferenceIndex().getReferencesToMethod(source), containsInAnyOrder(
			this.newBehaviorReferenceByMethod(source, this.callerClass.getName(), "e", "()V")
		));
	}

	@Test
	public void subIntIntInt() {
		MethodEntry source = this.newMethod(this.subClass, "<init>", "(III)V");
		assertThat(this.index.getReferenceIndex().getReferencesToMethod(source), is(empty()));
	}

	@Test
	public void subsubInt() {
		MethodEntry source = this.newMethod(this.subsubClass, "<init>", "(I)V");
		assertThat(this.index.getReferenceIndex().getReferencesToMethod(source), containsInAnyOrder(
			this.newBehaviorReferenceByMethod(source, this.callerClass.getName(), "f", "()V")
		));
	}

	@Test
	public void defaultConstructable() {
		MethodEntry source = this.newMethod(this.defaultClass, "<init>", "()V");
		assertThat(this.index.getReferenceIndex().getReferencesToMethod(source), containsInAnyOrder(
			this.newBehaviorReferenceByMethod(source, this.callerClass.getName(), "g", "()V")
		));
	}

	private ClassEntry newClass(String obfName) {
		return this.index.getEntryIndex().getClass(obfName);
	}

	public EntryReference<MethodEntry, MethodEntry> newBehaviorReferenceByMethod(MethodEntry methodEntry, String callerClassName, String callerName, String callerSignature) {
		return new EntryReference<>(methodEntry, "", this.newMethod(this.index.getEntryIndex().getClass(callerClassName), callerName, callerSignature));
	}

	public MethodEntry newMethod(ClassEntry className, String methodName, String methodSignature) {
		return this.index.getEntryIndex().getMethod(className, methodName, methodSignature);
	}
}
