package org.quiltmc.enigma.gui.panel;

import org.quiltmc.enigma.api.analysis.index.jar.EntryIndex;
import org.quiltmc.enigma.api.analysis.index.jar.JarIndex;
import org.quiltmc.enigma.api.translation.representation.AccessFlags;
import org.quiltmc.enigma.api.translation.representation.ArgumentDescriptor;
import org.quiltmc.enigma.api.translation.representation.TypeDescriptor;
import org.quiltmc.enigma.gui.EditableType;
import org.quiltmc.enigma.gui.Gui;
import org.quiltmc.enigma.gui.config.Config;
import org.quiltmc.enigma.gui.docker.DockerManager;
import org.quiltmc.enigma.gui.element.ConvertingTextField;
import org.quiltmc.enigma.gui.element.MainWindow;
import org.quiltmc.enigma.gui.event.ConvertingTextFieldListener;
import org.quiltmc.enigma.gui.util.GridBagConstraintsBuilder;
import org.quiltmc.enigma.gui.util.GuiUtil;
import org.quiltmc.enigma.gui.util.ScaleUtil;
import org.quiltmc.enigma.api.translation.mapping.EntryChange;
import org.quiltmc.enigma.api.translation.representation.entry.ClassEntry;
import org.quiltmc.enigma.api.translation.representation.entry.Entry;
import org.quiltmc.enigma.api.translation.representation.entry.FieldEntry;
import org.quiltmc.enigma.api.translation.representation.entry.LocalVariableEntry;
import org.quiltmc.enigma.api.translation.representation.entry.MethodEntry;
import org.quiltmc.enigma.util.I18n;
import org.quiltmc.enigma.util.Pair;
import org.quiltmc.enigma.util.validation.ValidationContext;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

public class IdentifierPanel {
	private final Gui gui;

	private final JPanel ui = new JPanel();

	private Entry<?> lastEntry;
	private Entry<?> entry;
	private Entry<?> deobfEntry;
	private InfoTable infoTable;

	private ConvertingTextField nameField;

	private final ValidationContext vc;

	public IdentifierPanel(Gui gui) {
		this.gui = gui;
		this.vc = new ValidationContext(this.gui.getNotificationManager());

		this.ui.setPreferredSize(ScaleUtil.getDimension(0, 150));
		this.retranslateUi();
		this.ui.setEnabled(false);
		this.ui.setLayout(new BorderLayout());
	}

	public void setReference(Entry<?> entry) {
		this.entry = entry;
		this.refreshReference();
	}

	public boolean startRenaming() {
		if (this.nameField == null) return false;

		this.nameField.startEditing();

		return true;
	}

	public boolean startRenaming(String text) {
		if (this.nameField == null) return false;

		this.nameField.startEditing();
		this.nameField.setEditText(text);

		return true;
	}

	public void refreshReference() {
		this.deobfEntry = this.entry == null ? null : this.gui.getController().getProject().getRemapper().deobfuscate(this.entry);

		// Prevent IdentifierPanel from being rebuilt if you didn't click off.
		if (this.lastEntry == this.entry && this.nameField != null) {
			if (!this.nameField.hasChanges()) {
				final String name;

				// Find what to set the name to.
				if (this.deobfEntry instanceof MethodEntry methodEntry && methodEntry.isConstructor()) {
					// Get the parent of the method if it is a constructor.
					final ClassEntry parent = methodEntry.getParent();

					if (parent == null) {
						throw new IllegalStateException("constructor method entry to render has no parent!");
					}

					name = parent.isInnerClass() ? parent.getName() : parent.getFullName();
				} else if (this.deobfEntry instanceof ClassEntry classEntry && !classEntry.isInnerClass()) {
					name = classEntry.getFullName();
				} else {
					name = this.deobfEntry.getName();
				}

				this.nameField.setReferenceText(name);
			}

			return;
		}

		this.lastEntry = this.entry;

		this.nameField = null;

		if (this.infoTable != null) {
			this.infoTable.uninstall();
		}

		this.ui.removeAll();
		this.infoTable = new InfoTable(this.gui, this.entry);

		if (this.entry == null) {
			this.ui.setEnabled(false);
		} else {
			this.ui.setEnabled(true);

			if (this.deobfEntry instanceof ClassEntry ce) {
				String name = ce.isInnerClass() ? ce.getName() : ce.getFullName();
				this.nameField = this.infoTable.addRenameTextField(EditableType.CLASS, name);
				this.infoTable.addCopiableStringRow(I18n.translate("info_panel.identifier.obfuscated"), this.entry.getName());

				if (ce.getParent() != null) {
					this.infoTable.addCopiableStringRow(I18n.translate("info_panel.identifier.outer_class"), ce.getParent().getFullName());

					if (ce.getParent().isInnerClass()) {
						this.infoTable.addCopiableStringRow(I18n.translate("info_panel.identifier.top_level_class"), ce.getTopLevelClass().getFullName());
					}
				}
			} else if (this.deobfEntry instanceof FieldEntry fe) {
				this.nameField = this.infoTable.addRenameTextField(EditableType.FIELD, fe.getName());
				this.infoTable.addStringRow(I18n.translate("info_panel.identifier.class"), fe.getParent().getFullName());
				this.infoTable.addCopiableStringRow(I18n.translate("info_panel.identifier.obfuscated"), this.entry.getName());
				this.infoTable.addCopiableStringRow(I18n.translate("info_panel.identifier.type"), toReadableType(fe.getDesc()));
			} else if (this.deobfEntry instanceof MethodEntry me) {
				if (me.isConstructor()) {
					ClassEntry ce = me.getParent();
					if (ce != null) {
						String name = ce.isInnerClass() ? ce.getName() : ce.getFullName();
						this.nameField = this.infoTable.addRenameTextField(EditableType.CLASS, name);
					}
				} else {
					this.nameField = this.infoTable.addRenameTextField(EditableType.METHOD, me.getName());
					this.infoTable.addStringRow(I18n.translate("info_panel.identifier.class"), me.getParent().getFullName());
				}

				this.infoTable.addCopiableStringRow(I18n.translate("info_panel.identifier.obfuscated"), this.entry.getName());
				this.infoTable.addCopiableStringRow(I18n.translate("info_panel.identifier.method_descriptor"), me.getDesc().toString());
			} else if (this.deobfEntry instanceof LocalVariableEntry lve) {
				EditableType type;

				if (lve.isArgument()) {
					type = EditableType.PARAMETER;
				} else {
					type = EditableType.LOCAL_VARIABLE;
				}

				this.nameField = this.infoTable.addRenameTextField(type, lve.getName());
				this.infoTable.addStringRow(I18n.translate("info_panel.identifier.class"), lve.getContainingClass().getFullName());
				this.infoTable.addCopiableStringRow(I18n.translate("info_panel.identifier.method"), lve.getParent().getName());
				this.infoTable.addStringRow(I18n.translate("info_panel.identifier.index"), Integer.toString(lve.getIndex()));

				// type
				JarIndex index = this.gui.getController().getProject().getJarIndex();
				AccessFlags access = index.getIndex(EntryIndex.class).getMethodAccess(lve.getParent());
				int i = access != null && access.isStatic() ? 0 : 1;
				var args = lve.getParent().getDesc().getArgumentDescs();

				for (ArgumentDescriptor arg : args) {
					if (i == lve.getIndex()) {
						this.infoTable.addCopiableStringRow(I18n.translate("info_panel.identifier.type"), toReadableType(arg));
						break;
					}

					var primitive = TypeDescriptor.Primitive.get(arg.toString().charAt(0));
					i += primitive == null ? 1 : primitive.getSize();
				}
			} else {
				throw new IllegalStateException("unreachable");
			}

			var mapping = this.gui.getController().getProject().getRemapper().getMapping(this.entry);
			if (Config.main().development.showMappingSourcePlugin.value() && mapping.tokenType().isProposed()) {
				this.infoTable.addStringRow(I18n.translate("dev.source_plugin"), mapping.sourcePluginId());
			}
		}

		this.ui.add(this.infoTable, BorderLayout.CENTER);
		this.infoTable.install();

		if (this.nameField != null) {
			this.nameField.addListener(new ConvertingTextFieldListener() {
				@Override
				public void onStartEditing(ConvertingTextField field) {
					int i = field.getText().lastIndexOf('/');
					if (i != -1) {
						field.selectSubstring(i + 1);
					}
				}

				@Override
				public boolean tryStopEditing(ConvertingTextField field, boolean abort) {
					if (abort) return true;

					IdentifierPanel.this.vc.setNotifier(IdentifierPanel.this.gui.getNotificationManager());
					IdentifierPanel.this.vc.reset();
					return IdentifierPanel.this.vc.canProceed();
				}

				@Override
				public void onStopEditing(ConvertingTextField field, boolean abort) {
					if (!abort) {
						IdentifierPanel.this.vc.setNotifier(IdentifierPanel.this.gui.getNotificationManager());
						IdentifierPanel.this.vc.reset();
						IdentifierPanel.this.doRename(field.getText());
					}

					EditorPanel e = IdentifierPanel.this.gui.getActiveEditor();
					if (e != null) {
						e.getEditor().requestFocusInWindow();
					}
				}
			});
		}

		this.ui.validate();
		this.ui.repaint();
	}

	private static String toReadableType(TypeDescriptor descriptor) {
		var primitive = TypeDescriptor.Primitive.get(descriptor.toString().charAt(0));

		if (primitive != null) {
			return descriptor + " (" + primitive.getKeyword() + ")";
		} else {
			String raw = descriptor.toString();
			// type will look like "LClassName;", with an optional [ at the start to denote an array
			// strip semicolon (;) from the end
			raw = raw.substring(0, raw.length() - 1);
			// handle arrays: add "[]" to the end and strip "["
			while (raw.startsWith("[")) {
				raw = raw.substring(1) + "[]";
			}

			// strip "L"
			return raw.substring(1);
		}
	}

	private void doRename(String newName) {
		this.gui.getController().applyChange(this.vc, this.getRename(newName));
	}

	private EntryChange<? extends Entry<?>> getRename(String newName) {
		Entry<?> entry = this.entry;
		if (entry instanceof MethodEntry method && method.isConstructor()) {
			entry = method.getContainingClass();
		}

		return EntryChange.modify(entry).withDeobfName(newName);
	}

	public void retranslateUi() {
		this.ui.setBorder(BorderFactory.createTitledBorder(I18n.translate("info_panel.identifier")));
		this.refreshReference();
	}

	public JPanel getUi() {
		return this.ui;
	}
}
