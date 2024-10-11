package org.quiltmc.enigma.gui.docker;

import org.quiltmc.enigma.gui.Gui;
import org.quiltmc.enigma.gui.docker.component.DockerSelector;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DockerManager {
	private final List<DockerResizeListener> dockerResizeListeners = new ArrayList<>();

	private final Map<Class<? extends Docker>, Docker> dockers = new LinkedHashMap<>();
	private final Map<String, Class<? extends Docker>> dockerClasses = new HashMap<>();

	private final Dock rightDock;
	private final Dock leftDock;
	private final DockerSelector rightDockerSelector;
	private final DockerSelector leftDockerSelector;

	public DockerManager(Gui gui) {
		this.rightDockerSelector = new DockerSelector(this, Docker.Side.RIGHT);
		this.leftDockerSelector = new DockerSelector(this, Docker.Side.LEFT);

		this.rightDock = new Dock(gui, Docker.Side.RIGHT);
		this.leftDock = new Dock(gui, Docker.Side.LEFT);

		this.rightDock.addComponentListener(createResizeListener(gui, Docker.Side.RIGHT));
		this.leftDock.addComponentListener(createResizeListener(gui, Docker.Side.LEFT));
	}

	private ComponentListener createResizeListener(Gui gui, Docker.Side side) {
		return new ComponentAdapter() {
			public void componentResized(ComponentEvent componentEvent) {
				for (DockerResizeListener listener : DockerManager.this.dockerResizeListeners) {
					listener.onDockerResized(side, DockerManager.this.getDock(side).getWidth(), DockerManager.this.getMainAreaWidth(gui));
				}
			}
		};
	}

	public int getMainAreaWidth(Gui gui) {
		return gui.getMainWindow().getFrame().getWidth() - (DockerManager.this.leftDockerSelector.getWidth() + DockerManager.this.leftDock.getWidth() + DockerManager.this.rightDockerSelector.getWidth() + DockerManager.this.rightDock.getWidth());
	}

	/**
	 * Gets the {@link Dock} instance positioned on the right side of the screen.
	 * @return the right dock
	 */
	public Dock getRightDock() {
		return this.rightDock;
	}

	/**
	 * Gets the {@link Dock} instance positioned on the left side of the screen.
	 * @return the left dock
	 */
	public Dock getLeftDock() {
		return this.leftDock;
	}

	public Dock getDock(Docker.Side side) {
		return side == Docker.Side.LEFT ? this.leftDock : this.rightDock;
	}

	public DockerSelector getDockerSelector(Docker.Side side) {
		return side == Docker.Side.LEFT ? this.leftDockerSelector : this.rightDockerSelector;
	}

	public void addDockerResizeListener(DockerResizeListener listener) {
		this.dockerResizeListeners.add(listener);
	}

	/**
	 * Hosts a docker, making it visible, in the location provided.
	 * @param docker the docker to be hosted
	 * @param location the location to place it
	 */
	public void host(Docker docker, Docker.Location location) {
		this.host(docker, location.side(), location.verticalLocation());
	}

	/**
	 * Hosts a docker, making it visible, in the location provided.
	 * @param docker the docker to be hosted
	 * @param side the side to place it on
	 * @param location the vertical location to place it
	 */
	public void host(Docker docker, Docker.Side side, Docker.VerticalLocation location) {
		if (side == Docker.Side.LEFT) {
			this.leftDock.host(docker, location);
		} else {
			this.rightDock.host(docker, location);
		}
	}

	/**
	 * Restores the state of both docks to the version saved in the config.
	 * @see Dock#restoreState(DockerManager)
	 */
	public void restoreStateFromConfig() {
		this.leftDock.restoreState(this);
		this.rightDock.restoreState(this);
	}

	/**
	 * Registers a new docker to be available in the GUI.
	 * @param docker the docker to be registered
	 */
	public void registerDocker(Docker docker) {
		this.dockers.put(docker.getClass(), docker);
		this.dockerClasses.put(docker.getId(), docker.getClass());
	}

	/**
	 * Gets a docker by its class.
	 * @param clazz the class of the docker to get
	 * @return the docker
	 */
	@SuppressWarnings("unchecked")
	public <T extends Docker> T getDocker(Class<T> clazz) {
		Docker panel = this.dockers.get(clazz);
		if (panel != null) {
			return (T) panel;
		} else {
			throw new IllegalArgumentException("no docker registered for class " + clazz);
		}
	}

	/**
	 * Gets a docker by its id.
	 * @param id the id of the docker to get
	 * @return the docker
	 */
	public Docker getDocker(String id) {
		if (!this.dockerClasses.containsKey(id)) {
			throw new IllegalArgumentException("no docker registered for id " + id);
		}

		return this.getDocker(this.dockerClasses.get(id));
	}

	/**
	 * Gets all currently registered dockers in a nice neat collection.
	 * @return the complete collection of dockers
	 */
	public Collection<Docker> getDockers() {
		return this.dockers.values();
	}

	public interface DockerResizeListener {
		void onDockerResized(Docker.Side side, int newWidth, int newMainAreaSize);
	}
}
