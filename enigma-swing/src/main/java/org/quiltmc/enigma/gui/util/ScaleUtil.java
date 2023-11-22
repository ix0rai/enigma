package org.quiltmc.enigma.gui.util;

import com.github.swingdpi.UiDefaultsScaler;
import com.github.swingdpi.plaf.BasicTweaker;
import com.github.swingdpi.plaf.MetalTweaker;
import com.github.swingdpi.plaf.NimbusTweaker;
import com.github.swingdpi.plaf.WindowsTweaker;
import org.quiltmc.enigma.gui.config.Config;
import org.quiltmc.syntaxpain.SyntaxpainConfiguration;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;

public class ScaleUtil {
	private static final List<ScaleChangeListener> listeners = new ArrayList<>();

	public static void setScaleFactor(float scaleFactor) {
		float oldScale = Config.getScaleFactor();
		float clamped = Math.min(Math.max(0.25f, scaleFactor), 10.0f);
		Config.setScaleFactor(clamped);
		rescaleFontInConfig("Default", oldScale);
		rescaleFontInConfig("Default 2", oldScale);
		rescaleFontInConfig("Small", oldScale);
		rescaleFontInConfig("Editor", oldScale);
		Config.save();
		listeners.forEach(l -> l.onScaleChanged(clamped, oldScale));
	}

	public static void addListener(ScaleChangeListener listener) {
		listeners.add(listener);
	}

	public static void removeListener(ScaleChangeListener listener) {
		listeners.remove(listener);
	}

	public static Dimension getDimension(int width, int height) {
		return new Dimension(scale(width), scale(height));
	}

	public static Insets getInsets(int top, int left, int bottom, int right) {
		return new Insets(scale(top), scale(left), scale(bottom), scale(right));
	}

	public static Font getFont(String fontName, int style, int fontSize) {
		return scaleFont(new Font(fontName, style, fontSize));
	}

	public static Font scaleFont(Font font) {
		return createTweakerForCurrentLook(Config.getActiveScaleFactor()).modifyFont("", font);
	}

	private static void rescaleFontInConfig(String name, float oldScale) {
		Config.getFont(name).ifPresent(font -> Config.setFont(name, rescaleFont(font, oldScale)));
	}

	// This does not use the font that's currently active in the UI!
	private static Font rescaleFont(Font font, float oldScale) {
		float newSize = Math.round(font.getSize() / oldScale * Config.getScaleFactor());
		return font.deriveFont(newSize);
	}

	public static float scale(float f) {
		return f * Config.getActiveScaleFactor();
	}

	public static float invert(float f) {
		return f / Config.getActiveScaleFactor();
	}

	public static int scale(int i) {
		return (int) (i * Config.getActiveScaleFactor());
	}

	public static Border createEmptyBorder(int top, int left, int bottom, int right) {
		return BorderFactory.createEmptyBorder(scale(top), scale(left), scale(bottom), scale(right));
	}

	public static int invert(int i) {
		return (int) (i / Config.getActiveScaleFactor());
	}

	public static void applyScaling() {
		float scale = Config.getActiveScaleFactor();

		if (Config.getActiveLookAndFeel().needsScaling()) {
			UiDefaultsScaler.updateAndApplyGlobalScaling((int) (100 * scale), true);
		}

		Font font = Config.getEditorFont();
		font = font.deriveFont(12 * scale);
		SyntaxpainConfiguration.setEditorFont(font);
	}

	private static BasicTweaker createTweakerForCurrentLook(float dpiScaling) {
		String testString = UIManager.getLookAndFeel().getName().toLowerCase();
		if (testString.contains("windows")) {
			return new WindowsTweaker(dpiScaling, testString.contains("classic"));
		} else if (testString.contains("metal")) {
			return new MetalTweaker(dpiScaling);
		} else if (testString.contains("nimbus")) {
			return new NimbusTweaker(dpiScaling);
		}

		return new BasicTweaker(dpiScaling);
	}
}
