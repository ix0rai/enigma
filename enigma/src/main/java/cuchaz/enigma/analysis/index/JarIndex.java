package cuchaz.enigma.analysis.index;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import cuchaz.enigma.Enigma;
import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.analysis.ReferenceTargetType;
import cuchaz.enigma.classprovider.ClassProvider;
import cuchaz.enigma.translation.mapping.EntryResolver;
import cuchaz.enigma.translation.mapping.IndexEntryResolver;
import cuchaz.enigma.translation.representation.Lambda;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.DefinedEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.translation.representation.entry.ParentedEntry;
import cuchaz.enigma.utils.I18n;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JarIndex implements JarIndexer {
	private final Set<String> indexedClasses = new HashSet<>();
	private final EntryIndex entryIndex;
	private final InheritanceIndex inheritanceIndex;
	private final ReferenceIndex referenceIndex;
	private final BridgeMethodIndex bridgeMethodIndex;
	private final PackageVisibilityIndex packageVisibilityIndex;
	private final EnclosingMethodIndex enclosingMethodIndex;
	private final EntryResolver entryResolver;

	private final Collection<JarIndexer> indexers;

	private final Multimap<String, MethodEntry> methodImplementations = HashMultimap.create();
	private final ListMultimap<ClassEntry, DefinedEntry<?, ?>> childrenByClass;

	private ProgressListener progress;

	public JarIndex(EntryIndex entryIndex, InheritanceIndex inheritanceIndex, ReferenceIndex referenceIndex, BridgeMethodIndex bridgeMethodIndex, PackageVisibilityIndex packageVisibilityIndex, EnclosingMethodIndex enclosingMethodIndex) {
		this.entryIndex = entryIndex;
		this.inheritanceIndex = inheritanceIndex;
		this.referenceIndex = referenceIndex;
		this.bridgeMethodIndex = bridgeMethodIndex;
		this.packageVisibilityIndex = packageVisibilityIndex;
		this.enclosingMethodIndex = enclosingMethodIndex;
		this.indexers = List.of(entryIndex, inheritanceIndex, referenceIndex, bridgeMethodIndex, packageVisibilityIndex, enclosingMethodIndex);
		this.entryResolver = new IndexEntryResolver(this);
		this.childrenByClass = ArrayListMultimap.create();
	}

	public static JarIndex empty() {
		EntryIndex entryIndex = new EntryIndex();
		InheritanceIndex inheritanceIndex = new InheritanceIndex(entryIndex);
		ReferenceIndex referenceIndex = new ReferenceIndex(entryIndex);
		BridgeMethodIndex bridgeMethodIndex = new BridgeMethodIndex(entryIndex, inheritanceIndex, referenceIndex);
		PackageVisibilityIndex packageVisibilityIndex = new PackageVisibilityIndex();
		EnclosingMethodIndex enclosingMethodIndex = new EnclosingMethodIndex();
		return new JarIndex(entryIndex, inheritanceIndex, referenceIndex, bridgeMethodIndex, packageVisibilityIndex, enclosingMethodIndex);
	}

	public void indexJar(Set<String> classNames, ClassProvider classProvider, ProgressListener progress) {
		// for use in processIndex
		this.progress = progress;

		this.indexedClasses.addAll(classNames);
		this.progress.init(4, I18n.translate("progress.jar.indexing"));

		this.progress.step(1, I18n.translate("progress.jar.indexing.entries"));

		for (String className : classNames) {
			classProvider.get(className).accept(new IndexClassVisitor(this, this.entryIndex, Enigma.ASM_VERSION));
		}

		this.progress.step(2, I18n.translate("progress.jar.indexing.references"));

		for (String className : classNames) {
			try {
				classProvider.get(className).accept(new IndexReferenceVisitor(this, this.entryIndex, this.inheritanceIndex, Enigma.ASM_VERSION));
			} catch (Exception e) {
				throw new RuntimeException("Exception while indexing class: " + className, e);
			}
		}

		this.progress.step(3, I18n.translate("progress.jar.indexing.methods"));
		this.bridgeMethodIndex.findBridgeMethods();

		this.processIndex(this);

		this.progress = null;
	}

	@Override
	public void processIndex(JarIndex index) {
		this.stepProcessingProgress("progress.jar.indexing.process.jar");

		this.indexers.forEach(indexer -> {
			this.stepProcessingProgress(indexer.getTranslationKey());
			indexer.processIndex(index);
		});

		this.stepProcessingProgress("progress.jar.indexing.process.done");
	}

	private void stepProcessingProgress(String key) {
		if (this.progress != null) {
			this.progress.step(4, I18n.translateFormatted("progress.jar.indexing.process", I18n.translate(key)));
		}
	}

	@Override
	public void indexClass(ClassEntry classEntry) {
		Preconditions.checkNotNull(classEntry.getDefinition(), "Cannot index class with null definition!");

		if (classEntry.isJre()) {
			return;
		}

		for (ClassEntry interfaceEntry : classEntry.getDefinition().getInterfaces()) {
			if (classEntry.equals(interfaceEntry)) {
				throw new IllegalArgumentException("Class cannot be its own interface! " + classEntry);
			}
		}

		this.indexers.forEach(indexer -> indexer.indexClass(classEntry));
		if (classEntry.isInnerClass() && !classEntry.getAccess().isSynthetic()) {
			this.childrenByClass.put(classEntry.getParent(), classEntry);
		}
	}

	@Override
	public void indexField(FieldEntry fieldEntry) {
		Preconditions.checkNotNull(fieldEntry.getDefinition(), "Cannot index field with null definition!");
		Preconditions.checkNotNull(fieldEntry.getParent(), "Cannot index field with null parent!");

		if (fieldEntry.getParent().isJre()) {
			return;
		}

		this.indexers.forEach(indexer -> indexer.indexField(fieldEntry));
		if (!fieldEntry.getAccess().isSynthetic()) {
			this.childrenByClass.put(fieldEntry.getParent(), fieldEntry);
		}
	}

	@Override
	public void indexMethod(MethodEntry methodEntry) {
		Preconditions.checkNotNull(methodEntry.getDefinition(), "Cannot index method with null definition!");
		Preconditions.checkNotNull(methodEntry.getParent(), "Cannot index method with null parent!");

		if (methodEntry.getParent().isJre()) {
			return;
		}

		this.indexers.forEach(indexer -> indexer.indexMethod(methodEntry));
		if (!methodEntry.getAccess().isSynthetic() && !methodEntry.getObfName().equals("<clinit>")) {
			this.childrenByClass.put(methodEntry.getParent(), methodEntry);
		}

		if (!methodEntry.isConstructor()) {
			this.methodImplementations.put(methodEntry.getParent().getFullName(), methodEntry);
		}
	}

	@Override
	public void indexMethodReference(MethodEntry callerEntry, MethodEntry referencedEntry, ReferenceTargetType targetType) {
		Preconditions.checkNotNull(callerEntry.getParent(), "Cannot index method with null parent!");

		if (callerEntry.getParent().isJre()) {
			return;
		}

		this.indexers.forEach(indexer -> indexer.indexMethodReference(callerEntry, referencedEntry, targetType));
	}

	@Override
	public void indexFieldReference(MethodEntry callerEntry, FieldEntry referencedEntry, ReferenceTargetType targetType) {
		Preconditions.checkNotNull(callerEntry.getParent(), "Cannot index method with null parent!");

		if (callerEntry.getParent().isJre()) {
			return;
		}

		this.indexers.forEach(indexer -> indexer.indexFieldReference(callerEntry, referencedEntry, targetType));
	}

	@Override
	public void indexLambda(MethodEntry callerEntry, Lambda lambda, ReferenceTargetType targetType) {
		Preconditions.checkNotNull(callerEntry.getParent(), "Cannot index method with null parent!");

		if (callerEntry.getParent().isJre()) {
			return;
		}

		this.indexers.forEach(indexer -> indexer.indexLambda(callerEntry, lambda, targetType));
	}

	@Override
	public void indexEnclosingMethod(ClassEntry classEntry, EnclosingMethodData enclosingMethodData) {
		if (classEntry.isJre()) {
			return;
		}

		this.indexers.forEach(indexer -> indexer.indexEnclosingMethod(classEntry, enclosingMethodData));
	}

	@Override
	public String getTranslationKey() {
		return "progress.jar.indexing.jar";
	}

	public EntryIndex getEntryIndex() {
		return this.entryIndex;
	}

	public InheritanceIndex getInheritanceIndex() {
		return this.inheritanceIndex;
	}

	public ReferenceIndex getReferenceIndex() {
		return this.referenceIndex;
	}

	public BridgeMethodIndex getBridgeMethodIndex() {
		return this.bridgeMethodIndex;
	}

	public PackageVisibilityIndex getPackageVisibilityIndex() {
		return this.packageVisibilityIndex;
	}

	public EnclosingMethodIndex getEnclosingMethodIndex() {
		return this.enclosingMethodIndex;
	}

	public EntryResolver getEntryResolver() {
		return this.entryResolver;
	}

	public ListMultimap<ClassEntry, DefinedEntry<?, ?>> getChildrenByClass() {
		return this.childrenByClass;
	}

	public boolean isIndexed(String internalName) {
		return this.indexedClasses.contains(internalName);
	}
}
