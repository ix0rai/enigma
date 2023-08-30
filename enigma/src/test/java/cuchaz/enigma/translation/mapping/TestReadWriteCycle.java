package cuchaz.enigma.translation.mapping;

import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.source.RenamableTokenType;
import cuchaz.enigma.translation.mapping.serde.MappingFileNameFormat;
import cuchaz.enigma.translation.mapping.serde.MappingFormat;
import cuchaz.enigma.translation.mapping.serde.MappingParseException;
import cuchaz.enigma.translation.mapping.serde.MappingSaveParameters;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.mapping.tree.HashEntryTree;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

/**
 * Tests that a MappingFormat can write out a fixed set of mappings and read them back without losing any information.
 */
public class TestReadWriteCycle {
	private final MappingSaveParameters parameters = new MappingSaveParameters(MappingFileNameFormat.BY_DEOBF);

	private final ClassEntry testClazz;
	private final FieldEntry testField1;
	private final FieldEntry testField2;
	private final MethodEntry testMethod1;
	private final MethodEntry testMethod2;

	public TestReadWriteCycle() {
		EntryIndex index = new EntryIndex();

		this.testClazz = index.getClass("a/b/c");
		this.testClazz.setMapping(new EntryMapping("alpha/beta/charlie", "this is a test class", RenamableTokenType.DEOBFUSCATED));

		this.testField1 = index.getField(index.getClass("a/b/c"), "field1", "I");
		this.testField1.setMapping(new EntryMapping("mapped1", "this is field 1", RenamableTokenType.DEOBFUSCATED));

		this.testField2 = index.getField(index.getClass("a/b/c"), "field2", "I");
		this.testField2.setMapping(new EntryMapping("mapped2", "this is field 2", RenamableTokenType.DEOBFUSCATED));

		this.testMethod1 = index.getMethod(index.getClass("a/b/c"), "method1", "()V");
		this.testMethod1.setMapping(new EntryMapping("mapped3", "this is method 1", RenamableTokenType.DEOBFUSCATED));

		this.testMethod2 = index.getMethod(index.getClass("a/b/c"), "method2", "()V");
		this.testMethod2.setMapping(new EntryMapping("mapped4", "this is method 2", RenamableTokenType.DEOBFUSCATED));
	}

	private void insertMapping(EntryTree<EntryMapping> mappings, Entry<?> entry) {
		mappings.insert(entry, entry.getMapping());
	}

	private void testReadWriteCycle(MappingFormat mappingFormat, String tmpNameSuffix) throws IOException, MappingParseException {
		//construct some known mappings to test with
		EntryTree<EntryMapping> testMappings = new HashEntryTree<>();
		this.insertMapping(testMappings, this.testClazz);
		this.insertMapping(testMappings, this.testField1);
		this.insertMapping(testMappings, this.testField2);
		this.insertMapping(testMappings, this.testMethod1);
		this.insertMapping(testMappings, this.testMethod2);

		Assertions.assertTrue(testMappings.contains(this.testClazz), "Test mapping insertion failed: testClazz");
		Assertions.assertTrue(testMappings.contains(this.testField1), "Test mapping insertion failed: testField1");
		Assertions.assertTrue(testMappings.contains(this.testField2), "Test mapping insertion failed: testField2");
		Assertions.assertTrue(testMappings.contains(this.testMethod1), "Test mapping insertion failed: testMethod1");
		Assertions.assertTrue(testMappings.contains(this.testMethod2), "Test mapping insertion failed: testMethod2");

		File tempFile = File.createTempFile("readWriteCycle", tmpNameSuffix);
		tempFile.delete(); //remove the auto created file

		mappingFormat.write(testMappings, tempFile.toPath(), ProgressListener.none(), this.parameters);
		Assertions.assertTrue(tempFile.exists(), "Written file not created");

		EntryIndex index = new EntryIndex();
		EntryTree<EntryMapping> loadedMappings = mappingFormat.read(tempFile.toPath(), index, ProgressListener.none());

		System.out.println(loadedMappings.getAllEntries().toList());
		Assertions.assertTrue(loadedMappings.contains(this.testClazz), "Loaded mappings don't contain testClazz");
		Assertions.assertTrue(loadedMappings.contains(this.testField1), "Loaded mappings don't contain testField1");
		Assertions.assertTrue(loadedMappings.contains(this.testField2), "Loaded mappings don't contain testField2");
		Assertions.assertTrue(loadedMappings.contains(this.testMethod1), "Loaded mappings don't contain testMethod1");
		Assertions.assertTrue(loadedMappings.contains(this.testMethod2), "Loaded mappings don't contain testMethod2");

		ClassEntry newClass = index.getClass(this.testClazz.getObfName());
		FieldEntry newField1 = index.getField(this.testField1.getParent(), this.testField1.getObfName(), this.testField1.getDesc());
		FieldEntry newField2 = index.getField(this.testField2.getParent(), this.testField2.getObfName(), this.testField2.getDesc());
		MethodEntry newMethod1 = index.getMethod(this.testMethod1.getParent(), this.testMethod1.getObfName(), this.testMethod1.getDesc());
		MethodEntry newMethod2 = index.getMethod(this.testMethod2.getParent(), this.testMethod2.getObfName(), this.testMethod2.getDesc());

		Assertions.assertEquals(this.testClazz.getDeobfName(), newClass.getDeobfName(), "Incorrect mapping: testClazz");
		Assertions.assertEquals(this.testField1.getDeobfName(), newField1.getDeobfName(), "Incorrect mapping: testField1");
		Assertions.assertEquals(this.testField2.getDeobfName(), newField2.getDeobfName(), "Incorrect mapping: testField2");
		Assertions.assertEquals(this.testMethod1.getDeobfName(), newMethod1.getDeobfName(), "Incorrect mapping: testMethod1");
		Assertions.assertEquals(this.testMethod2.getDeobfName(), newMethod2.getDeobfName(), "Incorrect mapping: testMethod2");

		Assertions.assertEquals(this.testClazz.getJavadocs(), newClass.getJavadocs(), "Incorrect javadoc: testClazz");
		Assertions.assertEquals(this.testField1.getJavadocs(), newField1.getJavadocs(), "Incorrect javadoc: testField1");
		Assertions.assertEquals(this.testField2.getJavadocs(), newField2.getJavadocs(), "Incorrect javadoc: testField2");
		Assertions.assertEquals(this.testMethod1.getJavadocs(), newMethod1.getJavadocs(), "Incorrect javadoc: testMethod1");
		Assertions.assertEquals(this.testMethod2.getJavadocs(), newMethod2.getJavadocs(), "Incorrect javadoc: testMethod2");

		tempFile.delete();
	}

	@Test
	public void testEnigmaFile() throws IOException, MappingParseException {
		this.testReadWriteCycle(MappingFormat.ENIGMA_FILE, ".enigma");
	}

	@Test
	public void testEnigmaDir() throws IOException, MappingParseException {
		this.testReadWriteCycle(MappingFormat.ENIGMA_DIRECTORY, ".tmp");
	}

	@Test
	public void testEnigmaZip() throws IOException, MappingParseException {
		this.testReadWriteCycle(MappingFormat.ENIGMA_ZIP, ".zip");
	}

	@Test
	public void testTinyV2() throws IOException, MappingParseException {
		this.testReadWriteCycle(MappingFormat.TINY_V2, ".tiny");
	}

	@Test
	@Disabled
	public void testRecaf() throws IOException, MappingParseException {
		this.testReadWriteCycle(MappingFormat.RECAF, ".recaf");
	}
}
