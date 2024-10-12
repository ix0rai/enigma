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
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

public class IdentifierPanel {
	private final Gui gui;

	private Entry<?> lastEntry;
	private Entry<?> entry;
	private Entry<?> deobfEntry;
	private TableHelper infoTable;

	private ConvertingTextField nameField;

	private final ValidationContext vc;

	public IdentifierPanel(Gui gui) {
		this.gui = gui;
		this.vc = new ValidationContext(this.gui.getNotificationManager());

		this.infoTable = new TableHelper(null, this.gui);
		this.infoTable.setPreferredSize(ScaleUtil.getDimension(0, 150));
		this.retranslateUi();
		this.infoTable.setEnabled(false);
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

		this.infoTable.begin(this.entry);

		if (this.entry == null) {
			this.infoTable.setEnabled(false);
		} else {
			this.infoTable.setEnabled(true);

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

		this.infoTable.end();

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

		this.infoTable.validate();
		this.infoTable.repaint();
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
		this.infoTable.setBorder(BorderFactory.createTitledBorder(I18n.translate("info_panel.identifier")));
		this.refreshReference();
	}

	public JPanel getUi() {
		return this.infoTable;
	}

	private static final class TableHelper extends JPanel {
		private Entry<?> e;
		private final Gui gui;
		private int row;

		private boolean setUp;
		private int prevSize;

		TableHelper(Entry<?> e, Gui gui) {
			this.e = e;
			this.gui = gui;
		}

		public void begin(Entry<?> e) {
			this.e = e;
			this.setUp = false;
			this.removeAll();
			this.setLayout(new GridBagLayout());
		}

		public void addRow(Component c1, Component c2) {
			GridBagConstraintsBuilder cb = createCB();
			JPanel panel = new JPanel(new GridBagLayout());
			panel.add(c1, cb.pos(0, 0).build());
			panel.add(c2, cb.pos(1, 0).weightX(1.0).fill(GridBagConstraints.HORIZONTAL).build());
			panel.setBackground(Config.currentTheme().getSyntaxPaneColors().lineNumbersBackground.value());
			panel.setBorder(new LineBorder(Config.getCurrentSyntaxPaneColors().lineNumbersSelected.value(), ScaleUtil.scale(1)));

			this.add(panel);
			this.row += 1;
		}

		public void addCopiableRow(JLabel c1, JLabel c2) {
			c2.addMouseListener(GuiUtil.onMouseClick(event -> {
				if (event.getButton() == MouseEvent.BUTTON1) {
					GuiUtil.copyToClipboard(c2.getText());
					GuiUtil.showPopup(c2, I18n.translate("popup.copied"), event.getXOnScreen(), event.getYOnScreen());
				}
			}));
			this.addRow(c1, c2);
		}

		public ConvertingTextField addConvertingTextField(String c1, String c2) {
			ConvertingTextField textField = new ConvertingTextField(c2);
			this.addRow(new JLabel(c1), textField.getUi());
			return textField;
		}

		public ConvertingTextField addRenameTextField(EditableType type, String c2) {
			String description = switch (type) {
				case CLASS -> I18n.translate("info_panel.identifier.class");
				case METHOD -> I18n.translate("info_panel.identifier.method");
				case FIELD -> I18n.translate("info_panel.identifier.field");
				case PARAMETER, LOCAL_VARIABLE -> I18n.translate("info_panel.identifier.variable");
				default -> throw new IllegalStateException("Unexpected value: " + type);
			};

			if (this.gui.getController().getProject().isRenamable(this.e)) {
				ConvertingTextField field = this.addConvertingTextField(description, c2);
				field.setEditable(this.gui.isEditable(type));
				return field;
			} else {
				this.addStringRow(description, c2);
				return null;
			}
		}

		public void addStringRow(String c1, String c2) {
			this.addRow(new JLabel(c1), GuiUtil.unboldLabel(new JLabel(c2)));
		}

		public void addCopiableStringRow(String c1, String c2) {
			this.addCopiableRow(new JLabel(c1), GuiUtil.unboldLabel(new JLabel(c2)));
		}

		public void end() {
			// Add an empty panel with y-weight=1 so that all the other elements get placed at the top edge
			this.add(new JPanel(), GridBagConstraintsBuilder.create().pos(0, this.row).weight(0.0, 1.0).build());
		}

		@Override
		public void paint(Graphics graphics) {
			int width = this.gui.getDockerManager().getMainAreaWidth(this.gui);

			if (!this.setUp || this.prevSize != width) {
				if (width == 0) {
					return;
				}

				this.setLayout(new GridBagLayout());

				// width will be the real weight in pixels, so we need to normalize it
				int scaled = ScaleUtil.invert(width);
				// todo column number should be dependent on panel size

				// todo: force all panels to be the same width
				// todo change forced width to largest panel if bigger (Math.max)
				int largestPanelWidth = 0;
				for (Component panel : this.getComponents()) {
					if (panel.getPreferredSize().getWidth() > largestPanelWidth) {
						largestPanelWidth = panel.getWidth();
					}
				}

				int preferredColumnCount = scaled / ScaleUtil.scale(300);
				if (preferredColumnCount == 0) {
					preferredColumnCount = 1;
				}

				int preferredPanelWidth = (scaled - ScaleUtil.scale(10 * preferredColumnCount)) / preferredColumnCount;

				int panelWidth = Math.max(preferredPanelWidth, largestPanelWidth);
				int columnCount = panelWidth > preferredPanelWidth ? scaled / preferredPanelWidth : preferredColumnCount;

				List<Component> components = Arrays.asList(this.getComponents());
				this.removeAll();

				int column = 0;
				int row = 1;
				for (int i = 0; i < components.size(); i++) {

					if (i == 0) {
						this.addComponent(components.get(i), i, 0, 0);
					} else {
						this.addComponent(components.get(i), i, column, row);

						column++;

						if (columnCount == 1 || (column != 0 && column % (columnCount + 1) == 0)) {
							column = 0;
							row++;
						}
					}

					components.get(i).setSize(panelWidth, components.get(i).getHeight());
				}

				// Add an empty panel with y-weight=1 so that all the other elements get placed at the top edge
				this.add(new JPanel(), GridBagConstraintsBuilder.create().pos(0, row + 1).weight(0.0, 1.0).build());

				this.setUp = true;
				this.revalidate();
			}

			this.prevSize = this.gui.getDockerManager().getMainAreaWidth(this.gui);

			super.paint(graphics);
		}

		private void addComponent(Component component, int index, int column, int row) {
			if (index == 0) {
				//this.topLevel.add(component, BorderLayout.NORTH);
			} else {
				GridBagConstraintsBuilder cb = createCB();
				cb.padding(ScaleUtil.scale(10));
				this.add(component, cb.pos(column, row).weightX(1.0).fill(GridBagConstraints.HORIZONTAL).build());
			}
		}

		private GridBagConstraintsBuilder createCB() {
			return GridBagConstraintsBuilder.create()
				.insets(2)
				.anchor(GridBagConstraints.WEST);
		}
	}
}
