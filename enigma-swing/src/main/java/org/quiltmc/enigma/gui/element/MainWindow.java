package org.quiltmc.enigma.gui.element;

import org.quiltmc.enigma.gui.Gui;
import org.quiltmc.enigma.gui.docker.Docker;
import org.quiltmc.enigma.gui.docker.DockerManager;
import org.quiltmc.enigma.gui.docker.component.DockerSelector;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JPanel;

public class MainWindow {
	private final JFrame frame;
	private final JPanel workArea = new JPanel();

	private final JMenuBar menuBar = new JMenuBar();
	private final StatusBar statusBar = new StatusBar();

	private final List<WindowResizeListener> windowResizeListeners = new ArrayList<>();

	public MainWindow(DockerManager dockerManager, String title) {
		this.frame = new JFrame(title);
		this.frame.setJMenuBar(this.menuBar);

		Container contentPane = this.frame.getContentPane();
		contentPane.setLayout(new BorderLayout());
		contentPane.add(this.workArea, BorderLayout.CENTER);
		contentPane.add(this.statusBar.getUi(), BorderLayout.SOUTH);
		contentPane.add(dockerManager.getDockerSelector(Docker.Side.RIGHT), BorderLayout.EAST);
		contentPane.add(dockerManager.getDockerSelector(Docker.Side.LEFT), BorderLayout.WEST);

		this.frame.addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent componentEvent) {
				for (WindowResizeListener listener : MainWindow.this.windowResizeListeners) {
					listener.onWindowResized(MainWindow.this.frame.getWidth(), MainWindow.this.frame.getHeight());
				}
			}
		});
	}

	public void setVisible(boolean visible) {
		this.frame.setVisible(visible);
	}

	public JMenuBar getMenuBar() {
		return this.menuBar;
	}

	public StatusBar getStatusBar() {
		return this.statusBar;
	}

	public Container getWorkArea() {
		return this.workArea;
	}

	public JFrame getFrame() {
		return this.frame;
	}

	public void setTitle(String title) {
		this.frame.setTitle(title);
	}

	public void addWindowResizeListener(WindowResizeListener listener) {
		this.windowResizeListeners.add(listener);
		listener.onWindowResized(this.frame.getWidth(), this.frame.getHeight());
	}

	public interface WindowResizeListener {
		void onWindowResized(int newWidth, int newHeight);
	}
}
