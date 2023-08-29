package cuchaz.enigma.translation.mapping;

import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.DefinedEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.translation.representation.entry.ParentedEntry;
import cuchaz.enigma.utils.validation.Message;
import cuchaz.enigma.utils.validation.ValidationContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class MappingValidator {
	private final Translator deobfuscator;
	private final JarIndex index;

	public MappingValidator(Translator deobfuscator, JarIndex index) {
		this.deobfuscator = deobfuscator;
		this.index = index;
	}

	public void validateRename(ValidationContext vc, Entry<?> entry, String name) {
		entry.validateName(vc, name);
		this.validateUnique(vc, entry, name);
	}

	/**
	 * Ensures that this name is unique: it is not used by any other entry with a signature similar enough to conflict.
	 */
	private void validateUnique(ValidationContext context, Entry<?> entry, String name) {
		ClassEntry containingClass = entry.getContainingClass();

		// parameters must be special-cased
		if (entry instanceof LocalVariableEntry parameter) {
			this.validateParameterUniqueness(context, name, parameter);
		}

		List<DefinedEntry<?, ?>> siblings = new ArrayList<>(this.index.getChildrenByClass().get(containingClass));

		// add sibling classes
		if (entry instanceof ClassEntry classEntry) {
			siblings.addAll(this.index.getEntryIndex().getClasses().stream().filter(e -> {
				if (e.isInnerClass()) {
					return false;
				}

				// filter by package
				String packageName = e.getPackageName();
				String originalPackageName = classEntry.getPackageName();

				return (originalPackageName == null && packageName == null)
					|| (packageName != null && packageName.equals(originalPackageName));
			}).toList());
		}

		// add all ancestors
		var a = this.index.getInheritanceIndex().getAncestors(containingClass);
		var b = this.index.getChildrenByClass();
		for (ClassEntry ancestor : this.index.getInheritanceIndex().getAncestors(containingClass)) {
			siblings.addAll(this.index.getChildrenByClass().get(ancestor));
		}

		if (!this.isUnique(entry, siblings, name)) {
			this.raiseConflict(context, entry.getParent(), name, false);
		} else {
			Entry<?> shadowedEntry = this.getShadowedEntry(entry, siblings, name);
			if (shadowedEntry != null) {
				this.raiseConflict(context, shadowedEntry.getParent(), name, true);
			}
		}
	}

	/**
	 * Ensures that the parameter's new name is not used by any other parameter of its parent method.
	 * @implNote currently, we cannot check against obfuscated parameter names, since parameters are not indexed
	 */
	private void validateParameterUniqueness(ValidationContext context, String name, LocalVariableEntry parameter) {
		MethodEntry parent = parameter.getParent();
		if (parent != null) {
			Iterator<LocalVariableEntry> iterator = parent.getParameterIterator(this.index.getEntryIndex(), this.deobfuscator);
			while (iterator.hasNext()) {
				if (iterator.next().getName().equals(name)) {
					this.raiseConflict(context, parent, name, false);
				}
			}
		}
	}

	private void raiseConflict(ValidationContext context, Entry<?> parent, String name, boolean shadow) {
		if (parent != null) {
			context.raise(shadow ? Message.SHADOWED_NAME_CLASS : Message.NON_UNIQUE_NAME_CLASS, name, parent);
		} else {
			context.raise(shadow ? Message.SHADOWED_NAME : Message.NON_UNIQUE_NAME, name);
		}
	}

	private boolean isUnique(Entry<?> entry, List<? extends DefinedEntry<?, ?>> siblings, String name) {
		// Methods need further checks
		if (entry instanceof MethodEntry methodEntry) {
			return this.isMethodUnique(methodEntry, siblings, name);
		}

		for (Entry<?> sibling : siblings) {
			if (name.equals("a12") && sibling.getName().equals("a12")) {
				System.out.println();
			}

			if (conflicts(entry, sibling, name)) {
				return false;
			}
		}

		return true;
	}

	private static boolean conflicts(Entry<?> entry, Entry<?> sibling, String name) {
		return entry.canConflictWith(sibling)
			&& ((sibling.isObfuscated() && sibling.getObfName().equals(name))
			|| (!sibling.isObfuscated() && name.equals(sibling.getDeobfName())));
	}

	private boolean isMethodUnique(MethodEntry entry, List<? extends DefinedEntry<?, ?>> siblings, String name) {
		for (DefinedEntry<?, ?> sibling : siblings) {
			if (conflicts(entry, sibling, name)) {
				AccessFlags siblingFlags = sibling.getAccess();
				AccessFlags flags = entry.getAccess();
				boolean sameParent = (entry.getParent() != null && entry.getParent().equals(sibling.getParent()));

				if (!sameParent && flags != null && siblingFlags != null) {
					// Methods from different parents don't conflict if they are both static or private
					if ((flags.isStatic() && siblingFlags.isStatic())
							|| (flags.isPrivate() && siblingFlags.isPrivate())) {
						continue;
					}
				}

				return false;
			}
		}

		return true;
	}

	@Nullable
	private Entry<?> getShadowedEntry(Entry<?> entry, List<? extends DefinedEntry<?, ?>> siblings, String name) {
		for (DefinedEntry<?, ?> sibling : siblings) {
			if (entry.canShadow(sibling)) {
				Set<ClassEntry> ancestors = this.index.getInheritanceIndex().getAncestors(entry.getContainingClass());

				if (ancestors.contains(sibling.getContainingClass())) {
					AccessFlags siblingFlags = sibling.getAccess();
					AccessFlags flags = entry instanceof DefinedEntry<?,?> defined ? defined.getAccess() : null;

					if ((siblingFlags == null || (!siblingFlags.isPrivate() && siblingFlags.isStatic()))
							&& (flags == null || flags.isStatic())
							&& name.equals(sibling.getName())) {
						return sibling;
					}
				}
			}
		}

		return null;
	}
}
