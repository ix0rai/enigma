package cuchaz.enigma;

import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.classprovider.ClasspathClassProvider;
import cuchaz.enigma.translation.TranslateResult;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.serde.MappingFormat;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class TestTranslator {
	public static final Path JAR = TestUtil.obfJar("translation");

	private static Enigma enigma;
	private static EnigmaProject project;
	private static EntryTree<EntryMapping> mappings;
	private static Translator deobfuscator;
	private static EntryIndex index;

	@BeforeAll
	public static void beforeClass() throws Exception {
		enigma = Enigma.create();
		project = enigma.openJar(JAR, new ClasspathClassProvider(), ProgressListener.none());
		mappings = MappingFormat.ENIGMA_FILE.read(
				TestUtil.getResource("/translation.mappings"),
				project.getMapper().getJarIndex().getEntryIndex(),
				ProgressListener.none());
		project.setMappings(mappings);
		deobfuscator = project.getMapper().getDeobfuscator();
		index = project.getMapper().getJarIndex().getEntryIndex();
	}

	@Test
	public void basicClasses() {
		this.assertMapping(this.newClass("a"), this.newClass("deobf/A_Basic"));
		this.assertMapping(this.newClass("b"), this.newClass("deobf/B_BaseClass"));
		this.assertMapping(this.newClass("c"), this.newClass("deobf/C_SubClass"));
	}

	@Test
	public void basicFields() {
		this.assertMapping(this.newField("a", "a", "I"), this.newField("deobf/A_Basic", "f1", "I"));
		this.assertMapping(this.newField("a", "a", "F"), this.newField("deobf/A_Basic", "f2", "F"));
		this.assertMapping(this.newField("a", "a", "Ljava/lang/String;"), this.newField("deobf/A_Basic", "f3", "Ljava/lang/String;"));
	}

	@Test
	public void basicMethods() {
		this.assertMapping(this.newMethod("a", "a", "()V"), this.newMethod("deobf/A_Basic", "m1", "()V"));
		this.assertMapping(this.newMethod("a", "a", "()I"), this.newMethod("deobf/A_Basic", "m2", "()I"));
		this.assertMapping(this.newMethod("a", "a", "(I)V"), this.newMethod("deobf/A_Basic", "m3", "(I)V"));
		this.assertMapping(this.newMethod("a", "a", "(I)I"), this.newMethod("deobf/A_Basic", "m4", "(I)I"));
	}

	// TODO: basic constructors

	@Test
	public void inheritanceFields() {
		this.assertMapping(this.newField("b", "a", "I"), this.newField("deobf/B_BaseClass", "f1", "I"));
		this.assertMapping(this.newField("b", "a", "C"), this.newField("deobf/B_BaseClass", "f2", "C"));
		this.assertMapping(this.newField("c", "b", "I"), this.newField("deobf/C_SubClass", "f3", "I"));
		this.assertMapping(this.newField("c", "c", "I"), this.newField("deobf/C_SubClass", "f4", "I"));
	}

	@Test
	public void inheritanceFieldsShadowing() {
		this.assertMapping(this.newField("c", "b", "C"), this.newField("deobf/C_SubClass", "f2", "C"));
	}

	@Test
	public void inheritanceFieldsBySubClass() {
		this.assertMapping(this.newField("c", "a", "I"), this.newField("deobf/C_SubClass", "f1", "I"));
		// NOTE: can't reference b.C by subclass since it's shadowed
	}

	@Test
	public void inheritanceMethods() {
		this.assertMapping(this.newMethod("b", "a", "()I"), this.newMethod("deobf/B_BaseClass", "m1", "()I"));
		this.assertMapping(this.newMethod("b", "b", "()I"), this.newMethod("deobf/B_BaseClass", "m2", "()I"));
		this.assertMapping(this.newMethod("c", "c", "()I"), this.newMethod("deobf/C_SubClass", "m3", "()I"));
	}

	@Test
	public void inheritanceMethodsOverrides() {
		this.assertMapping(this.newMethod("c", "a", "()I"), this.newMethod("deobf/C_SubClass", "m1", "()I"));
	}

	@Test
	public void inheritanceMethodsBySubClass() {
		this.assertMapping(this.newMethod("c", "b", "()I"), this.newMethod("deobf/C_SubClass", "m2", "()I"));
	}

	@Test
	public void innerClasses() {
		// classes
		this.assertMapping(this.newClass("g"), this.newClass("deobf/G_OuterClass"));
		this.assertMapping(this.newClass("g$a"), this.newClass("deobf/G_OuterClass$A_InnerClass"));
		this.assertMapping(this.newClass("g$a$a"), this.newClass("deobf/G_OuterClass$A_InnerClass$A_InnerInnerClass"));
		this.assertMapping(this.newClass("g$b"), this.newClass("deobf/G_OuterClass$b"));
		this.assertMapping(this.newClass("g$b$a"), this.newClass("deobf/G_OuterClass$b$A_NamedInnerClass"));

		// fields
		this.assertMapping(this.newField("g$a", "a", "I"), this.newField("deobf/G_OuterClass$A_InnerClass", "f1", "I"));
		this.assertMapping(this.newField("g$a", "a", "Ljava/lang/String;"), this.newField("deobf/G_OuterClass$A_InnerClass", "f2", "Ljava/lang/String;"));
		this.assertMapping(this.newField("g$a$a", "a", "I"), this.newField("deobf/G_OuterClass$A_InnerClass$A_InnerInnerClass", "f3", "I"));
		this.assertMapping(this.newField("g$b$a", "a", "I"), this.newField("deobf/G_OuterClass$b$A_NamedInnerClass", "f4", "I"));

		// methods
		this.assertMapping(this.newMethod("g$a", "a", "()V"), this.newMethod("deobf/G_OuterClass$A_InnerClass", "m1", "()V"));
		this.assertMapping(this.newMethod("g$a$a", "a", "()V"), this.newMethod("deobf/G_OuterClass$A_InnerClass$A_InnerInnerClass", "m2", "()V"));
	}

	@Test
	public void namelessClass() {
		this.assertMapping(this.newClass("h"), this.newClass("h"));
	}

	@Test
	public void testGenerics() {
		// classes
		this.assertMapping(this.newClass("i"), this.newClass("deobf/I_Generics"));
		this.assertMapping(this.newClass("i$a"), this.newClass("deobf/I_Generics$A_Type"));
		this.assertMapping(this.newClass("i$b"), this.newClass("deobf/I_Generics$B_Generic"));

		// fields
		this.assertMapping(this.newField("i", "a", "Ljava/util/List;"), this.newField("deobf/I_Generics", "f1", "Ljava/util/List;"));
		this.assertMapping(this.newField("i", "b", "Ljava/util/List;"), this.newField("deobf/I_Generics", "f2", "Ljava/util/List;"));
		this.assertMapping(this.newField("i", "a", "Ljava/util/Map;"), this.newField("deobf/I_Generics", "f3", "Ljava/util/Map;"));
		this.assertMapping(this.newField("i$b", "a", "Ljava/lang/Object;"), this.newField("deobf/I_Generics$B_Generic", "f4", "Ljava/lang/Object;"));
		this.assertMapping(this.newField("i", "a", "Li$b;"), this.newField("deobf/I_Generics", "f5", "Ldeobf/I_Generics$B_Generic;"));
		this.assertMapping(this.newField("i", "b", "Li$b;"), this.newField("deobf/I_Generics", "f6", "Ldeobf/I_Generics$B_Generic;"));

		// methods
		this.assertMapping(this.newMethod("i$b", "a", "()Ljava/lang/Object;"), this.newMethod("deobf/I_Generics$B_Generic", "m1", "()Ljava/lang/Object;"));
	}

	private void assertMapping(Entry<?> obf, Entry<?> deobf) {
		TranslateResult<? extends Entry<?>> result = deobfuscator.extendedTranslate(obf);
		assertThat(result, is(notNullValue()));
		assertThat(result.getValue(), is(deobf));

		String deobfName = result.getValue().getName();
		if (deobfName != null) {
			assertThat(deobfName, is(deobf.getName()));
		}
	}

	public MethodEntry newMethod(String className, String methodName, String methodSignature) {
		return index.getMethod(index.getClass(className), methodName, methodSignature);
	}

	public FieldEntry newField(String className, String methodName, String methodSignature) {
		return index.getField(index.getClass(className), methodName, methodSignature);
	}

	public ClassEntry newClass(String name) {
		return index.getClass(name);
	}
}
