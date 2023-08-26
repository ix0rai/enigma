package cuchaz.enigma;

import cuchaz.enigma.classprovider.ClasspathClassProvider;
import cuchaz.enigma.source.Decompiler;
import cuchaz.enigma.source.Decompilers;
import cuchaz.enigma.source.SourceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static cuchaz.enigma.TestEntryFactory.newClass;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class TestDeobfed {
	public static final Path OBF = TestUtil.obfJar("translation");
	public static final Path DEOBF = TestUtil.deobfJar("translation");
	private static EnigmaProject deobfProject;

	@BeforeAll
	public static void beforeClass() throws Exception {
		Enigma enigma = Enigma.create();

		Files.createDirectories(DEOBF.getParent());
		EnigmaProject obfProject = enigma.openJar(OBF, new ClasspathClassProvider(), ProgressListener.none());
		obfProject.exportRemappedJar(ProgressListener.none()).write(DEOBF, ProgressListener.none());

		deobfProject = enigma.openJar(DEOBF, new ClasspathClassProvider(), ProgressListener.none());
	}

	@Test
	public void obfEntries() {
		assertThat(deobfProject.getJarIndex().getEntryIndex().getClasses(), containsInAnyOrder(
				newClass("cuchaz/enigma/inputs/Keep"),
				newClass("a"),
				newClass("b"),
				newClass("c"),
				newClass("d"),
				newClass("d$1"),
				newClass("e"),
				newClass("f"),
				newClass("g"),
				newClass("g$a"),
				newClass("g$a$a"),
				newClass("g$b"),
				newClass("g$b$a"),
				newClass("h"),
				newClass("h$a"),
				newClass("h$a$a"),
				newClass("h$b"),
				newClass("h$b$a"),
				newClass("h$b$a$a"),
				newClass("h$b$a$b"),
				newClass("i"),
				newClass("i$a"),
				newClass("i$b")
		));
	}

	@Test
	public void decompile() {
		Decompiler decompiler = Decompilers.CFR.create(deobfProject.getClassProvider(), new SourceSettings(false, false));

		decompiler.getSource("a", deobfProject.getMapper());
		decompiler.getSource("b", deobfProject.getMapper());
		decompiler.getSource("c", deobfProject.getMapper());
		decompiler.getSource("d", deobfProject.getMapper());
		decompiler.getSource("d$1", deobfProject.getMapper());
		decompiler.getSource("e", deobfProject.getMapper());
		decompiler.getSource("f", deobfProject.getMapper());
		decompiler.getSource("g", deobfProject.getMapper());
		decompiler.getSource("g$a", deobfProject.getMapper());
		decompiler.getSource("g$a$a", deobfProject.getMapper());
		decompiler.getSource("g$b", deobfProject.getMapper());
		decompiler.getSource("g$b$a", deobfProject.getMapper());
		decompiler.getSource("h", deobfProject.getMapper());
		decompiler.getSource("h$a", deobfProject.getMapper());
		decompiler.getSource("h$a$a", deobfProject.getMapper());
		decompiler.getSource("h$b", deobfProject.getMapper());
		decompiler.getSource("h$b$a", deobfProject.getMapper());
		decompiler.getSource("h$b$a$a", deobfProject.getMapper());
		decompiler.getSource("h$b$a$b", deobfProject.getMapper());
		decompiler.getSource("i", deobfProject.getMapper());
		decompiler.getSource("i$a", deobfProject.getMapper());
		decompiler.getSource("i$b", deobfProject.getMapper());
	}
}
