package cuchaz.enigma.source.cfr;

import com.google.common.base.Preconditions;
import cuchaz.enigma.analysis.index.EntryIndex;
import cuchaz.enigma.source.SourceIndex;
import cuchaz.enigma.source.SourceSettings;
import cuchaz.enigma.source.Token;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.EntryRemapper;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import org.benf.cfr.reader.bytecode.analysis.loc.HasByteCodeLoc;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.variables.NamedVariable;
import org.benf.cfr.reader.entities.AccessFlag;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.ClassFileField;
import org.benf.cfr.reader.entities.Field;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;
import org.benf.cfr.reader.util.output.MovableDumperContext;
import org.benf.cfr.reader.util.output.StringStreamDumper;
import org.benf.cfr.reader.util.output.TypeContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class EnigmaDumper extends StringStreamDumper {
	private final StringBuilder sb;
	private final SourceSettings sourceSettings;
	private final SourceIndex index;
	private final EntryRemapper mapper;
	private final EntryIndex entryIndex;
	private final Map<Object, Entry<?>> refs = new HashMap<>();
	private final TypeUsageInformation typeUsage;
	private final MovableDumperContext dumperContext;
	private boolean muteLine = false;
	private MethodEntry contextMethod = null;

	public EnigmaDumper(StringBuilder sb, SourceSettings sourceSettings, TypeUsageInformation typeUsage, Options options, EntryRemapper mapper) {
		this(sb, sourceSettings, typeUsage, options, mapper, new SourceIndex(), new MovableDumperContext());
	}

	protected EnigmaDumper(StringBuilder sb, SourceSettings sourceSettings, TypeUsageInformation typeUsage, Options options, EntryRemapper mapper, SourceIndex index, MovableDumperContext context) {
		super((m, e) -> {
		}, sb, typeUsage, options, IllegalIdentifierDump.Nop.getInstance(), context);
		this.sb = sb;
		this.sourceSettings = sourceSettings;
		this.typeUsage = typeUsage;
		this.mapper = mapper;
		this.dumperContext = context;
		this.index = index;
		this.entryIndex = mapper.getJarIndex().getEntryIndex();

		Preconditions.checkNotNull(this.entryIndex, "Entry index cannot be null!");
	}

	private MethodEntry getMethodEntry(MethodPrototype method) {
		if (method == null || method.getOwner() == null) {
			return null;
		}

		MethodDescriptor desc = new MethodDescriptor(method.getOriginalDescriptor());
		return this.entryIndex.getMethod(this.getClassEntry(method.getOwner()), method.getName(), desc);
	}

	private LocalVariableEntry getParameterEntry(MethodPrototype method, int parameterIndex) {
		MethodEntry owner = this.getMethodEntry(method);
		// params may be not computed if cfr creates a lambda expression fallback, e.g. in PointOfInterestSet
		if (owner == null || !method.parametersComputed()) {
			return null;
		}

		LocalVariable variable = method.getParameterLValues().get(parameterIndex).localVariable;
		int variableIndex = variable.getIdx();
		return this.entryIndex.getLocalVariable(owner, variableIndex, variable.getName().getStringName(), true);
	}

	private FieldEntry getFieldEntry(JavaTypeInstance owner, String name, String desc) {
		ClassEntry parentClass = this.getClassEntry(owner);
		TypeDescriptor descriptor = new TypeDescriptor(desc);

		return this.entryIndex.getField(parentClass, name, descriptor);
	}

	private ClassEntry getClassEntry(JavaTypeInstance type) {
		String name = type.getRawName().replace('.', '/');

		return this.entryIndex.getClass(name);
	}

	@Override
	public Dumper packageName(JavaRefTypeInstance t) {
		if (this.sourceSettings.removeImports()) {
			return this;
		}

		return super.packageName(t);
	}

	@Override
	public Dumper keyword(String s) {
		if (this.sourceSettings.removeImports() && s.startsWith("import")) {
			this.muteLine = true;
			return this;
		}

		return super.keyword(s);
	}

	@Override
	public Dumper endCodeln() {
		if (this.muteLine) {
			this.muteLine = false;
			return this;
		}

		return super.endCodeln();
	}

	@Override
	public Dumper print(String s) {
		if (this.muteLine) {
			return this;
		}

		return super.print(s);
	}

	@Override
	public Dumper dumpClassDoc(JavaTypeInstance owner) {
		if (this.mapper != null) {
			List<String> recordComponentDocs = new LinkedList<>();

			if (this.isRecord(owner)) {
				ClassFile classFile = ((JavaRefTypeInstance) owner).getClassFile();
				for (ClassFileField field : classFile.getFields()) {
					if (field.getField().testAccessFlag(AccessFlag.ACC_STATIC)) {
						continue;
					}

					EntryMapping mapping = this.getFieldEntry(owner, field.getFieldName(), field.getField().getDescriptor()).getMapping();

					String javadoc = mapping.javadoc();
					if (javadoc != null) {
						recordComponentDocs.add(String.format("@param %s %s", mapping.targetName(), javadoc));
					}
				}
			}

			EntryMapping mapping = this.getClassEntry(owner).getMapping();
			String javadoc = mapping.javadoc();

			if (javadoc != null || !recordComponentDocs.isEmpty()) {
				this.print("/**").newln();
				if (javadoc != null) {
					for (String line : javadoc.split("\\R")) {
						this.print(" * ").print(line).newln();
					}

					if (!recordComponentDocs.isEmpty()) {
						this.print(" * ").newln();
					}
				}

				for (String componentDoc : recordComponentDocs) {
					this.print(" * ").print(componentDoc).newln();
				}

				this.print(" */").newln();
			}
		}

		return this;
	}

	@Override
	public Dumper dumpMethodDoc(MethodPrototype method) {
		if (this.mapper != null) {
			List<String> lines = new ArrayList<>();
			MethodEntry methodEntry = this.getMethodEntry(method);
			EntryMapping mapping = methodEntry.getMapping();
			if (mapping != null) {
				String javadoc = mapping.javadoc();
				if (javadoc != null) {
					lines.addAll(Arrays.asList(javadoc.split("\\R")));
				}
			}

			Collection<Entry<?>> children = this.mapper.getObfChildren(methodEntry);

			if (children != null && !children.isEmpty()) {
				for (Entry<?> each : children) {
					if (each instanceof LocalVariableEntry) {
						EntryMapping paramMapping = each.getMapping();
						if (paramMapping != null) {
							String javadoc = paramMapping.javadoc();
							if (javadoc != null) {
								lines.addAll(Arrays.asList(("@param " + paramMapping.targetName() + " " + javadoc).split("\\R")));
							}
						}
					}
				}
			}

			if (!lines.isEmpty()) {
				this.print("/**").newln();
				for (String line : lines) {
					this.print(" * ").print(line).newln();
				}

				this.print(" */").newln();
			}
		}

		return this;
	}

	@Override
	public Dumper dumpFieldDoc(Field field, JavaTypeInstance owner) {
		boolean recordComponent = this.isRecord(owner) && !field.testAccessFlag(AccessFlag.ACC_STATIC);
		if (this.mapper != null && !recordComponent) {
			EntryMapping mapping = this.getFieldEntry(owner, field.getFieldName(), field.getDescriptor()).getMapping();
			if (mapping != null) {
				String javadoc = mapping.javadoc();
				if (javadoc != null) {
					this.print("/**").newln();
					for (String line : javadoc.split("\\R")) {
						this.print(" * ").print(line).newln();
					}

					this.print(" */").newln();
				}
			}
		}

		return this;
	}

	@Override
	public Dumper methodName(String name, MethodPrototype method, boolean special, boolean defines) {
		Entry<?> entry = this.getMethodEntry(method);
		super.methodName(name, method, special, defines);
		int now = this.sb.length();
		Token token = new Token(now - name.length(), now, name);

		// Skip constructor references
		if (entry != null && !name.equals("new")) {
			if (defines) {
				this.index.addDeclaration(token, entry); // override as cfr reuses local vars
			} else {
				this.index.addReference(token, entry, this.contextMethod);
			}
		}

		return this;
	}

	@Override
	public Dumper parameterName(String name, Object ref, MethodPrototype method, int index, boolean defines) {
		super.parameterName(name, ref, method, index, defines);
		int now = this.sb.length();
		Token token = new Token(now - name.length(), now, name);
		Entry<?> entry;
		if (defines) {
			this.refs.put(ref, entry = this.getParameterEntry(method, index));
		} else {
			entry = this.refs.get(ref);
		}

		if (entry != null) {
			if (defines) {
				this.index.addDeclaration(token, entry);
			} else {
				this.index.addReference(token, entry, this.contextMethod);
			}
		}

		return this;
	}

	@Override
	public Dumper variableName(String name, NamedVariable variable, boolean defines) {
		// todo catch var declarations in the future
		return super.variableName(name, variable, defines);
	}

	@Override
	public Dumper identifier(String name, Object ref, boolean defines) {
		super.identifier(name, ref, defines);
		Entry<?> entry;
		if (defines) {
			this.refs.remove(ref);
			return this;
		}

		if ((entry = this.refs.get(ref)) == null) {
			return this;
		}

		int now = this.sb.length();
		Token token = new Token(now - name.length(), now, name);
		this.index.addReference(token, entry, this.contextMethod);
		return this;
	}

	@Override
	public Dumper fieldName(String name, String descriptor, JavaTypeInstance owner, boolean hiddenDeclaration, boolean isStatic, boolean defines) {
		super.fieldName(name, descriptor, owner, hiddenDeclaration, isStatic, defines);
		int now = this.sb.length();
		Token token = new Token(now - name.length(), now, name);
		if (descriptor != null) {
			Entry<?> entry = this.getFieldEntry(owner, name, descriptor);

			if (defines) {
				this.index.addDeclaration(token, entry);
			} else {
				this.index.addReference(token, entry, this.contextMethod);
			}
		}

		return this;
	}

	@Override
	public Dumper dump(JavaTypeInstance type) {
		this.dumpClass(TypeContext.None, type, false);
		return this;
	}

	@Override
	public Dumper dump(JavaTypeInstance type, boolean defines) {
		this.dumpClass(TypeContext.None, type, defines);
		return this;
	}

	@Override
	public Dumper dump(JavaTypeInstance type, TypeContext context) {
		this.dumpClass(context, type, false);
		return this;
	}

	private void dumpClass(TypeContext context, JavaTypeInstance type, boolean defines) {
		if (type instanceof JavaRefTypeInstance) {
			type.dumpInto(this, this.typeUsage, context);
			String name = this.typeUsage.getName(type, context); // the actually used name, dump will indent
			int now = this.sb.length();
			Token token = new Token(now - name.length(), now, name);

			if (defines) {
				this.index.addDeclaration(token, this.getClassEntry(type));
			} else {
				this.index.addReference(token, this.getClassEntry(type), this.contextMethod);
			}

			return;
		}

		type.dumpInto(this, this.typeUsage, context);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>Otherwise the type usage override dumper will not go through the type instance dump
	 * we have here.
	 */
	@Override
	public Dumper withTypeUsageInformation(TypeUsageInformation innerclassTypeUsageInformation) {
		return new EnigmaDumper(this.sb, this.sourceSettings, innerclassTypeUsageInformation, this.options, this.mapper, this.index, this.dumperContext);
	}

	@Override
	public void informBytecodeLoc(HasByteCodeLoc loc) {
		Collection<Method> methods = loc.getLoc().getMethods();
		if (!methods.isEmpty()) {
			this.contextMethod = this.getMethodEntry(methods.iterator().next().getMethodPrototype());
		}
	}

	public SourceIndex getIndex() {
		this.index.setSource(this.getString());
		return this.index;
	}

	public String getString() {
		return this.sb.toString();
	}

	private boolean isRecord(JavaTypeInstance javaTypeInstance) {
		if (javaTypeInstance instanceof JavaRefTypeInstance refTypeInstance) {
			ClassFile classFile = refTypeInstance.getClassFile();
			return classFile.getClassSignature().getSuperClass().getRawName().equals("java.lang.Record");
		}

		return false;
	}
}
