package cuchaz.enigma.translation.mapping;

import cuchaz.enigma.Enigma;
import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.TestUtil;
import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.classprovider.ClasspathClassProvider;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.mapping.tree.HashEntryTree;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.utils.validation.EmptyNotifier;
import cuchaz.enigma.utils.validation.Message;
import cuchaz.enigma.utils.validation.ParameterizedMessage;
import cuchaz.enigma.utils.validation.ValidationContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;

import java.nio.file.Path;

import static cuchaz.enigma.TestEntryFactory.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class TestMappingValidator {
	private static final String REPEATED_TEST_NAME = RepeatedTest.DISPLAY_NAME_PLACEHOLDER + " :: repetition " + RepeatedTest.CURRENT_REPETITION_PLACEHOLDER + "/" + RepeatedTest.TOTAL_REPETITIONS_PLACEHOLDER;

	public static final Path JAR = TestUtil.obfJar("validation");
	private static EnigmaProject project;
	private static EntryRemapper remapper;
	private static EntryIndex index;

	@BeforeAll
	public static void beforeAll() throws Exception {
		Enigma enigma = Enigma.create();
		project = enigma.openJar(JAR, new ClasspathClassProvider(), ProgressListener.none());
		remapper = project.getMapper();
		index = remapper.getJarIndex().getEntryIndex();
	}

	@BeforeEach
	public void beforeEach(RepetitionInfo repetitionInfo) {
		EntryTree<EntryMapping> mappings = new HashEntryTree<>();
		project.setMappings(mappings);
		remapper = project.getMapper();

		// repeat with mapped classes
		if (repetitionInfo.getCurrentRepetition() == 1) {
			remapper.putMapping(newVC(), newClass("a"), new EntryMapping("BaseClass"));
			remapper.putMapping(newVC(), newClass("b"), new EntryMapping("SuperClass"));
		}
	}

	@RepeatedTest(value = 2, name = REPEATED_TEST_NAME)
	public void shadowPrivateFields() {
		ClassEntry b = index.getClass("b");
		ClassEntry a = index.getClass("a");

		// static fields
		remapper.putMapping(newVC(), index.getField(b, "a", "Ljava/lang/String;"), new EntryMapping("FIELD_00"));

		ValidationContext vc = newVC();
		remapper.validatePutMapping(vc, index.getField(a, "c", "Ljava/lang/String;"), new EntryMapping("FIELD_00"));

		// todo broken!
		//assertMessages(vc, Message.SHADOWED_NAME_CLASS);

		// final fields
		remapper.putMapping(newVC(), index.getField(b, "a", "I"), new EntryMapping("field01"));

		vc = newVC();
		remapper.validatePutMapping(vc, index.getField(a, "a", "I"), new EntryMapping("field01"));

		// todo broken!
		//assertMessages(vc);

		// instance fields
		remapper.putMapping(newVC(), index.getField(b, "b", "I"), new EntryMapping("field02"));

		vc = newVC();
		remapper.validatePutMapping(vc, index.getField(a, "b", "I"), new EntryMapping("field02"));

		// todo broken!
		//assertMessages(vc);
	}

	@RepeatedTest(value = 2, name = REPEATED_TEST_NAME)
	public void shadowPublicFields() {
		ClassEntry b = index.getClass("b");
		ClassEntry a = index.getClass("a");

		// static fields
		remapper.putMapping(newVC(), index.getField(b, "b", "Ljava/lang/String;"), new EntryMapping("FIELD_04"));

		ValidationContext vc = newVC();
		remapper.validatePutMapping(vc, index.getField(a, "a", "Ljava/lang/String;"), new EntryMapping("FIELD_04"));

		// todo broken!
		//assertMessages(vc, Message.SHADOWED_NAME_CLASS);

		// default fields
		remapper.putMapping(newVC(), index.getField(b, "b", "Z"), new EntryMapping("field05"));

		vc = newVC();
		remapper.validatePutMapping(vc, index.getField(a, "a", "Z"), new EntryMapping("field05"));

		// todo broken!
		//assertMessages(vc);
	}

	@RepeatedTest(value = 2, name = REPEATED_TEST_NAME)
	public void shadowMethods() {
		ClassEntry b = index.getClass("b");
		ClassEntry a = index.getClass("a");

		// static methods
		remapper.putMapping(newVC(), index.getMethod(b, "c", "()V"), new EntryMapping("method01"));

		ValidationContext vc = newVC();
		remapper.validatePutMapping(vc, index.getMethod(a, "a", "()V"), new EntryMapping("method01"));

		// todo broken!
		//assertMessages(vc, Message.SHADOWED_NAME_CLASS);

		// private methods
		remapper.putMapping(newVC(), index.getMethod(b, "a", "()V"), new EntryMapping("method02"));

		vc = newVC();
		remapper.validatePutMapping(vc, index.getMethod(a, "d", "()V"), new EntryMapping("method02"));

		// todo!
		//assertMessages(vc);
	}

	@RepeatedTest(value = 2, name = REPEATED_TEST_NAME)
	public void nonUniqueFields() {
		ClassEntry a = index.getClass("a");

		remapper.putMapping(newVC(), index.getField(a, "a", "I"), new EntryMapping("field01"));

		ValidationContext vc = newVC();
		remapper.validatePutMapping(vc, index.getField(a, "b", "I"), new EntryMapping("field01"));

		assertMessages(vc, Message.NON_UNIQUE_NAME_CLASS);

		remapper.putMapping(newVC(), index.getField(a, "c", "Ljava/lang/String;"), new EntryMapping("FIELD_02"));

		vc = newVC();
		remapper.validatePutMapping(vc, index.getField(a, "a", "Ljava/lang/String;"), new EntryMapping("FIELD_02"));

		assertMessages(vc, Message.NON_UNIQUE_NAME_CLASS);
	}

	@RepeatedTest(value = 2, name = REPEATED_TEST_NAME)
	public void nonUniqueMethods() {
		ClassEntry a = index.getClass("a");

		remapper.putMapping(newVC(), index.getMethod(a, "a", "()V"), new EntryMapping("method01"));

		ValidationContext vc = newVC();
		remapper.validatePutMapping(vc, index.getMethod(a, "b", "()V"), new EntryMapping("method01"));

		assertMessages(vc, Message.NON_UNIQUE_NAME_CLASS);

		vc = newVC();
		remapper.validatePutMapping(vc, index.getMethod(a, "d", "()V"), new EntryMapping("method01"));

		assertMessages(vc, Message.NON_UNIQUE_NAME_CLASS);
	}

	@RepeatedTest(value = 2, name = REPEATED_TEST_NAME)
	public void conflictingMethods() {
		ClassEntry b = index.getClass("b");
		ClassEntry a = index.getClass("a");

		// "overriding" w/different return descriptor
		remapper.putMapping(newVC(), index.getMethod(b, "a", "()Z"), new EntryMapping("method01"));

		ValidationContext vc = newVC();
		remapper.validatePutMapping(vc, index.getMethod(a, "b", "()V"), new EntryMapping("method01"));


		assertMessages(vc, Message.NON_UNIQUE_NAME_CLASS);

		// "overriding" a static method
		remapper.putMapping(newVC(), index.getMethod(b, "c", "()V"), new EntryMapping("method02"));

		vc = newVC();
		remapper.validatePutMapping(vc, index.getMethod(a, "b", "()V"), new EntryMapping("method02"));

		assertMessages(vc, Message.NON_UNIQUE_NAME_CLASS);

		// "overriding" when the original methods were not related
		remapper.putMapping(newVC(), index.getMethod(b, "b", "()I"), new EntryMapping("method03"));

		vc = newVC();
		remapper.validatePutMapping(vc, index.getMethod(a, "a", "()I"), new EntryMapping("method03"));

		assertMessages(vc, Message.NON_UNIQUE_NAME_CLASS);
	}

	/**
	 * Assert that the validation context contains the messages.
	 *
	 * @param vc validation context
	 * @param messages the messages the validation context should contain
	 */
	private static void assertMessages(ValidationContext vc, Message... messages) {
		assertThat(vc.getMessages().size(), is(messages.length));
		for (int i = 0; i < messages.length; i++) {
			ParameterizedMessage msg = vc.getMessages().get(i);
			assertThat(msg.message(), is(messages[i]));
		}
	}

	private static ValidationContext newVC() {
		return new ValidationContext(EmptyNotifier.INSTANCE);
	}
}
