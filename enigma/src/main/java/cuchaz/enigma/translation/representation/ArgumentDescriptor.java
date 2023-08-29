package cuchaz.enigma.translation.representation;

import cuchaz.enigma.analysis.index.EntryIndex;

import java.util.function.UnaryOperator;

public class ArgumentDescriptor extends TypeDescriptor {
	private ParameterAccessFlags access;

	public ArgumentDescriptor(String desc, ParameterAccessFlags access) {
		super(desc);
		this.access = access;
	}

	public ParameterAccessFlags getAccess() {
		return this.access;
	}

	public void setAccess(ParameterAccessFlags access) {
		this.access = access;
	}

	public ArgumentDescriptor remap(UnaryOperator<String> remapper, EntryIndex index) {
		return new ArgumentDescriptor(super.remap(remapper, index).desc, this.getAccess());
	}
}
