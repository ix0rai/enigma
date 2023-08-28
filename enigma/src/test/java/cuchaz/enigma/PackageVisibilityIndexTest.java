package cuchaz.enigma;

import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.analysis.index.PackageVisibilityIndex;
import cuchaz.enigma.classprovider.JarClassProvider;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class PackageVisibilityIndexTest {
	public static final Path JAR = TestUtil.obfJar("packageAccess");
	private final JarIndex jarIndex;
	private final EntryIndex entryIndex;

	public PackageVisibilityIndexTest() throws Exception {
		JarClassProvider jcp = new JarClassProvider(JAR);
		this.jarIndex = JarIndex.empty();
		this.jarIndex.indexJar(jcp.getClassNames(), jcp, ProgressListener.none());
		this.entryIndex = this.jarIndex.getEntryIndex();
	}

	@Test
	public void test() {
		ClassEntry keep = newClass("cuchaz/enigma/inputs/Keep");
		ClassEntry base = newClass("a");
		ClassEntry samePackageChild = newClass("b");
		ClassEntry samePackageChildInner = newClass("b$a");
		ClassEntry otherPackageChild = newClass("c");
		ClassEntry otherPackageChildInner = newClass("c$a");

		PackageVisibilityIndex visibilityIndex = this.jarIndex.getPackageVisibilityIndex();
		assertThat(visibilityIndex.getPartition(base), containsInAnyOrder(base, samePackageChild, samePackageChildInner));
		System.out.println(visibilityIndex.getPartitions());
		assertThat(visibilityIndex.getPartitions(), containsInAnyOrder(
				containsInAnyOrder(base, samePackageChild, samePackageChildInner),
				containsInAnyOrder(otherPackageChild, otherPackageChildInner),
				contains(keep)
		));
	}

	private ClassEntry newClass(String obfName) {
		return this.entryIndex.getClass(obfName);
	}
}
