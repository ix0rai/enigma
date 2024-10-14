package org.quiltmc.enigma.gui.panel;

import org.quiltmc.enigma.api.translation.representation.entry.Entry;
import org.quiltmc.enigma.gui.EditableType;
import org.quiltmc.enigma.gui.Gui;
import org.quiltmc.enigma.gui.config.Config;
import org.quiltmc.enigma.gui.docker.DockerManager;
import org.quiltmc.enigma.gui.element.ConvertingTextField;
import org.quiltmc.enigma.gui.element.MainWindow;
import org.quiltmc.enigma.gui.util.GridBagConstraintsBuilder;
import org.quiltmc.enigma.gui.util.GuiUtil;
import org.quiltmc.enigma.gui.util.ScaleUtil;
import org.quiltmc.enigma.util.I18n;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class InfoTable extends JPanel {
	private final List<InfoBox> components = new ArrayList<>();
	private final Gui gui;
	private final Entry<?> entry;
	private final MainWindow.WindowResizeListener windowListener;
	private final DockerManager.DockerResizeListener dockerListener;

	private JComponent renameComponent;
	private boolean initialized;

	public InfoTable(Gui gui, Entry<?> entry) {
		this.gui = gui;
		this.entry = entry;
		this.initialized = false;

		this.windowListener = (newWidth, newHeight) -> {
			this.initialized = false;
			this.repaint();
		};
		this.dockerListener = (side, dockerWidth, mainAreaWidth) -> {
			this.initialized = false;
			this.repaint();
		};

		this.setLayout(new GridBagLayout());
	}

	public void install() {
		this.gui.getDockerManager().addDockerResizeListener(this.dockerListener);
		this.gui.getMainWindow().addWindowResizeListener(this.windowListener);
	}

	public void uninstall() {
		this.gui.getDockerManager().removeDockerResizeListener(this.dockerListener);
		this.gui.getMainWindow().removeWindowResizeListener(this.windowListener);
	}

	public void addRow(JLabel label, JLabel content) {
		this.components.add(new InfoBox(label, content));
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
		JPanel panel = new JPanel(new GridBagLayout());
		this.renameComponent = InfoBox.style(panel, new JLabel(c1), textField.getUi());
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

		if (this.gui.getController().getProject().isRenamable(this.entry)) {
			ConvertingTextField field = this.addConvertingTextField(description, c2);
			field.setEditable(this.gui.isEditable(type));
			return field;
		} else {
			this.addRow(new JLabel(description), GuiUtil.unboldLabel(new JLabel(c2)));
			return null;
		}
	}

	public void addStringRow(String c1, String c2) {
		this.addRow(new JLabel(c1), GuiUtil.unboldLabel(new JLabel(c2)));
	}

	public void addCopiableStringRow(String c1, String c2) {
		this.addCopiableRow(new JLabel(c1), GuiUtil.unboldLabel(new JLabel(c2)));
	}

	@Override
	public void paint(Graphics g) {
		if (!this.initialized && this.renameComponent != null && !this.components.isEmpty()) {
			Graphics2D g2d = (Graphics2D) g;

			int largestBoxWidth = this.getLargestBoxWidth(g2d);
			int scaledWidth = ScaleUtil.invert(this.gui.getDockerManager().getMainAreaWidth(this.gui));

			int preferredColumnCount = scaledWidth / ScaleUtil.scale(300);
			if (preferredColumnCount == 0) {
				preferredColumnCount = 1;
			}

			int preferredPanelWidth = (scaledWidth - ScaleUtil.scale(10 * preferredColumnCount)) / preferredColumnCount;

			int panelWidth = Math.max(preferredPanelWidth, largestBoxWidth);
			int columnCount = panelWidth > preferredPanelWidth ? scaledWidth / preferredPanelWidth : preferredColumnCount;

			// we don't want more columns than we have boxes
			if (columnCount > this.components.size()) {
				columnCount = this.components.size();
			}

			// add struts to make sure all columns are the same size
			List<Component> struts = addStruts(columnCount, panelWidth);

			// add boxes
			if (this.renameComponent == null) {
				throw new RuntimeException("attempted to paint info table before setting rename component!");
			}

			this.addRenameComponent(this.renameComponent, columnCount);

			int column = 0;
			int row = 1;
			for (InfoBox infoBox : this.components) {
				infoBox.setWidth(panelWidth);
				this.addInfoBox(infoBox, column, row);

				column++;

				if (columnCount == 1 || (column == columnCount)) {
					column = 0;
					row++;
				}
			}

			// remove struts to allow resizing
			for (Component component : struts) {
				this.remove(component);
			}

			// Add an empty panel with y-weight=1 so that all the other elements get placed at the top edge
			this.add(new JPanel(), GridBagConstraintsBuilder.create().pos(0, row + 1).weight(0.0, 1.0).build());
			this.initialized = true;

			this.revalidate();
			this.repaint();
		} else {
			super.paint(g);
		}
	}

	private void addRenameComponent(Component component, int columnCount) {
		GridBagConstraintsBuilder cb = createCB().size(columnCount, 1);
		this.add(component, cb.pos(0, 0).weightX(1.0).fill(GridBagConstraints.HORIZONTAL).build());
	}

	private void addInfoBox(InfoBox component, int column, int row) {
		GridBagConstraintsBuilder cb = createCB().size(1, 1).pos(column, row).fill(GridBagConstraints.HORIZONTAL);

		if (column != 0) {
			cb = cb.weightX(1.0);
		} else {
			cb = cb.weightX(0.0).weightY(0.1);
		}

		this.add(component, cb.build());
	}

	private List<Component> addStruts(int columnCount, int panelWidth) {
		List<Component> struts = new ArrayList<>();
		for (int i = 0; i < columnCount; i++) {
			var strut = Box.createHorizontalStrut(panelWidth);
			// i've discovered that grid bag layout does not work by PREFERRED size, as it should, but by MINIMUM size.
			//
			strut.setMinimumSize(new Dimension(0, 0));
			struts.add(strut);

			// row does not matter since struts don't take up any space
			this.add(strut, createCB().pos(i, 0).build());
		}

		return struts;
	}

	private int getLargestBoxWidth(Graphics2D g2d) {
		int largestBoxWidth = 0;
		for (InfoBox box : this.components) {
			int requiredWidth = box.getNeededWidth(g2d);
			if (requiredWidth > largestBoxWidth) {
				largestBoxWidth = box.getWidth();
			}
		}

		return largestBoxWidth;
	}

	private static GridBagConstraintsBuilder createCB() {
		return GridBagConstraintsBuilder.create()
			.insets(2)
			.anchor(GridBagConstraints.WEST);
	}

	public static class InfoBox extends JPanel {
		private final JLabel label;
		private final JLabel content;

		private int width;

		public InfoBox(JLabel label, JLabel content) {
			this.label = label;
			this.content = content;

			style(this, label, content);
		}

		static JPanel style(JPanel panel, Component leftComponent, Component rightComponent) {
			GridBagConstraintsBuilder cb = InfoTable.createCB();
			panel.add(leftComponent, cb.pos(0, 0).build());
			panel.add(rightComponent, cb.pos(1, 0).weightX(1.0).fill(GridBagConstraints.HORIZONTAL).build());
			panel.setBackground(Config.currentTheme().getSyntaxPaneColors().lineNumbersBackground.value());
			panel.setBorder(new LineBorder(Config.getCurrentSyntaxPaneColors().lineNumbersSelected.value(), ScaleUtil.scale(1)));

			return panel;
		}

		public int getNeededWidth(Graphics2D g) {
			double labelWidth = getTextWidth(g, this.label);
			double contentWidth = getTextWidth(g, this.content);

			int paddingAssumption = ScaleUtil.scale(10);

			return (int) (labelWidth + contentWidth + paddingAssumption);
		}

		private static double getTextWidth(Graphics2D g, JLabel label) {
			Font labelFont = label.getFont();
			String text = label.getText();
			return labelFont.createGlyphVector(g.getFontRenderContext(), text).getVisualBounds().getWidth();
		}

		public void setWidth(int width) {
			this.width = width;
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(this.width, 0);
		}
	}
}
