package cuchaz.enigma.translation.mapping;

import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.utils.validation.ValidationContext;

import javax.annotation.Nonnull;

public class EntryUtil {
	public static EntryMapping applyChange(ValidationContext vc, EntryRemapper remapper, EntryChange<?> change) {
		Entry<?> target = change.getTarget();
		EntryMapping prev = target.getMapping();
		EntryMapping mapping = EntryUtil.applyChange(prev, change);

		remapper.putMapping(vc, target, mapping);

		return mapping;
	}

	public static EntryMapping applyChange(@Nonnull EntryMapping self, EntryChange<?> change) {
		if (change.getDeobfName().isSet()) {
			self = self.withDeobfName(change.getDeobfName().getNewValue());
		} else if (change.getDeobfName().isReset()) {
			self = self.withDeobfName(null);
		}

		if (change.getJavadoc().isSet()) {
			self = self.withDocs(change.getJavadoc().getNewValue());
		} else if (change.getJavadoc().isReset()) {
			self = self.withDocs(null);
		}

		return self;
	}
}
