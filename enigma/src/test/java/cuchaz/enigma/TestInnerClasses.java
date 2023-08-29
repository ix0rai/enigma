package cuchaz.enigma;

import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.classprovider.CachingClassProvider;
import cuchaz.enigma.classprovider.JarClassProvider;
import cuchaz.enigma.source.Decompiler;
import cuchaz.enigma.source.Decompilers;
import cuchaz.enigma.source.SourceSettings;
import cuchaz.enigma.translation.mapping.EntryRemapper;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestInnerClasses {
	private final ClassEntry simpleOuter;
	private final ClassEntry simpleInner;
	private final ClassEntry constructorArgsOuter;
	private final ClassEntry constructorArgsInner;
	private final ClassEntry classTreeRoot;
	private final ClassEntry classTreeLevel1;
	private final ClassEntry classTreeLevel2;
	private final ClassEntry classTreeLevel3;

	public static final Path JAR = TestUtil.obfJar("innerClasses");
	private final JarIndex index;
	private final EntryIndex entryIndex;
	private final Decompiler decompiler;

	public TestInnerClasses() throws Exception {
		JarClassProvider jcp = new JarClassProvider(JAR);
		CachingClassProvider classProvider = new CachingClassProvider(jcp);
		this.index = JarIndex.empty();
		this.index.indexJar(jcp.getClassNames(), classProvider, ProgressListener.none());
		this.entryIndex = this.index.getEntryIndex();
		this.decompiler = Decompilers.CFR.create(classProvider, new SourceSettings(false, false));
		simpleOuter = newClass("d");
		simpleInner = newClass("d$a");
		constructorArgsOuter = newClass("c");
		constructorArgsInner = newClass("c$a");
		classTreeRoot = newClass("f");
		classTreeLevel1 = newClass("f$a");
		classTreeLevel2 = newClass("f$a$a");
		classTreeLevel3 = newClass("f$a$a$a");
	}

	@Test
	public void simple() {
		this.decompile(simpleOuter);
	}

	@Test
	public void constructorArgs() {
		this.decompile(constructorArgsOuter);
	}

	@Test
	public void classTree() {
		// root level
		assertTrue(this.index.getEntryIndex().hasClass(classTreeRoot));

		// level 1
		ClassEntry fullClassEntry = new ClassEntry(entryIndex, classTreeRoot.getName()
			+ "$" + classTreeLevel1.getSimpleName());
		assertTrue(this.index.getEntryIndex().hasClass(fullClassEntry));

		// level 2
		fullClassEntry = new ClassEntry(entryIndex, classTreeRoot.getName()
				+ "$" + classTreeLevel1.getSimpleName()
				+ "$" + classTreeLevel2.getSimpleName());
		assertTrue(this.index.getEntryIndex().hasClass(fullClassEntry));

		// level 3
		fullClassEntry = new ClassEntry(entryIndex, classTreeRoot.getName()
			+ "$" + classTreeLevel1.getSimpleName()
			+ "$" + classTreeLevel2.getSimpleName()
			+ "$" + classTreeLevel3.getSimpleName());
		assertTrue(this.index.getEntryIndex().hasClass(fullClassEntry));
	}

	private void decompile(ClassEntry classEntry) {
		this.decompiler.getSource(classEntry.getName(), EntryRemapper.empty(this.index));
	}

	private ClassEntry newClass(String name) {
		return index.getEntryIndex().getClass(name);
	}
}
