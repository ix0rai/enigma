package cuchaz.enigma;

import cuchaz.enigma.classprovider.ClasspathClassProvider;
import cuchaz.enigma.classprovider.JarClassProvider;
import cuchaz.enigma.source.Decompiler;
import cuchaz.enigma.source.Decompilers;
import cuchaz.enigma.source.SourceSettings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class TestDeobfuscator {
	private static final Path JAR = TestUtil.obfJar("loneClass");

	private EnigmaProject openProject() throws IOException {
		Enigma enigma = Enigma.create();
		return enigma.openJar(JAR, new ClasspathClassProvider(), ProgressListener.none());
	}

	@Test
	public void loadJar() throws Exception {
		this.openProject();
	}

	@Test
	public void decompileClass() throws Exception {
		EnigmaProject project = this.openProject();
		ClasspathClassProvider classProvider = new ClasspathClassProvider();
		project.getJarIndex().indexJar(new HashSet<>(classProvider.getClassNames()), classProvider, ProgressListener.none());
		Decompiler decompiler = Decompilers.CFR.create(project.getClassProvider(), new SourceSettings(false, false));

		decompiler.getSource("a", project.getMapper()).asString();
	}
}
