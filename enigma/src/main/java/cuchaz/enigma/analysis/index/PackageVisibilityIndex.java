package cuchaz.enigma.analysis.index;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import cuchaz.enigma.analysis.EntryReference;
import cuchaz.enigma.analysis.ReferenceTargetType;
import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PackageVisibilityIndex implements JarIndexer {
	private static boolean requiresSamePackage(AccessFlags entryAcc, EntryReference<?, ?> ref, InheritanceIndex inheritanceIndex) {
		if (entryAcc.isPublic()) {
			return false;
		}

		if (entryAcc.isProtected()) {
			ClassEntry contextClass = ref.context.getContainingClass();
			ClassEntry referencedClass = ref.entry.getContainingClass();

			if (!inheritanceIndex.getAncestors(contextClass).contains(referencedClass)) {
				return true; // access to protected member not in superclass
			}

			if (ref.targetType.getKind() == ReferenceTargetType.Kind.NONE) {
				return false; // access to superclass or static superclass member
			}

			// access to instance member only valid if target's class assignable to context class
			return !(ref.targetType.getKind() == ReferenceTargetType.Kind.UNINITIALIZED
					|| ((ReferenceTargetType.ClassType) ref.targetType).getEntry().equals(contextClass)
					|| inheritanceIndex.getAncestors(((ReferenceTargetType.ClassType) ref.targetType).getEntry()).contains(contextClass));
		}

		return true;
	}

	private final HashMultimap<ClassEntry, ClassEntry> connections = HashMultimap.create();
	private final List<Set<ClassEntry>> partitions = new ArrayList<>();
	private final Map<ClassEntry, Set<ClassEntry>> classPartitions = Maps.newHashMap();

	private void addConnection(ClassEntry classA, ClassEntry classB) {
		if (classA != classB) {
			this.connections.put(classA, classB);
			this.connections.put(classB, classA);
		}
	}

	private void buildPartition(Set<ClassEntry> unassignedClasses, Set<ClassEntry> partition, ClassEntry member) {
		for (ClassEntry connected : this.connections.get(member)) {
			if (unassignedClasses.remove(connected)) {
				partition.add(connected);
				this.buildPartition(unassignedClasses, partition, connected);
			}
		}
	}

	private void addConnections(EntryIndex entryIndex, ReferenceIndex referenceIndex, InheritanceIndex inheritanceIndex) {
		for (FieldEntry entry : entryIndex.getFields()) {
			AccessFlags entryAcc = entry.getAccess();
			if (!entryAcc.isPublic() && !entryAcc.isPrivate()) {
				for (EntryReference<FieldEntry, MethodEntry> ref : referenceIndex.getReferencesToField(entry)) {
					if (requiresSamePackage(entryAcc, ref, inheritanceIndex)) {
						this.addConnection(ref.entry.getContainingClass(), ref.context.getContainingClass());
					}
				}
			}
		}

		for (MethodEntry entry : entryIndex.getMethods()) {
			AccessFlags entryAcc = entry.getAccess();
			if (!entryAcc.isPublic() && !entryAcc.isPrivate()) {
				for (EntryReference<MethodEntry, MethodEntry> ref : referenceIndex.getReferencesToMethod(entry)) {
					if (requiresSamePackage(entryAcc, ref, inheritanceIndex)) {
						this.addConnection(ref.entry.getContainingClass(), ref.context.getContainingClass());
					}
				}
			}
		}

		for (ClassEntry entry : entryIndex.getClasses()) {
			AccessFlags entryAcc = entry.getAccess();
			if (!entryAcc.isPublic() && !entryAcc.isPrivate()) {
				for (EntryReference<ClassEntry, FieldEntry> ref : referenceIndex.getFieldTypeReferencesToClass(entry)) {
					if (requiresSamePackage(entryAcc, ref, inheritanceIndex)) {
						this.addConnection(ref.entry.getContainingClass(), ref.context.getContainingClass());
					}
				}

				for (EntryReference<ClassEntry, MethodEntry> ref : referenceIndex.getMethodTypeReferencesToClass(entry)) {
					if (requiresSamePackage(entryAcc, ref, inheritanceIndex)) {
						this.addConnection(ref.entry.getContainingClass(), ref.context.getContainingClass());
					}
				}
			}

			for (ClassEntry parent : inheritanceIndex.getParents(entry)) {
				AccessFlags parentFlags = parent.getAccess();
				if (parentFlags != null && !parentFlags.isPublic() && !parentFlags.isPrivate()) {
					this.addConnection(entry, parent);
				}
			}

			ClassEntry outerClass = entry.getOuterClass();
			if (outerClass != null) {
				this.addConnection(entry, outerClass);
			}
		}
	}

	private void addPartitions(EntryIndex entryIndex) {
		Set<ClassEntry> unassignedClasses = Sets.newHashSet(entryIndex.getClasses());
		while (!unassignedClasses.isEmpty()) {
			Iterator<ClassEntry> iterator = unassignedClasses.iterator();
			ClassEntry initialEntry = iterator.next();
			iterator.remove();

			HashSet<ClassEntry> partition = Sets.newHashSet();
			partition.add(initialEntry);
			this.buildPartition(unassignedClasses, partition, initialEntry);
			this.partitions.add(partition);
			for (ClassEntry entry : partition) {
				this.classPartitions.put(entry, partition);
			}
		}
	}

	public Collection<Set<ClassEntry>> getPartitions() {
		return this.partitions;
	}

	public Set<ClassEntry> getPartition(ClassEntry classEntry) {
		return this.classPartitions.get(classEntry);
	}

	@Override
	public void processIndex(JarIndex index) {
		EntryIndex entryIndex = index.getEntryIndex();
		ReferenceIndex referenceIndex = index.getReferenceIndex();
		InheritanceIndex inheritanceIndex = index.getInheritanceIndex();
		this.addConnections(entryIndex, referenceIndex, inheritanceIndex);
		this.addPartitions(entryIndex);
	}

	@Override
	public String getTranslationKey() {
		return "progress.jar.indexing.process.package_visibility";
	}
}
