/*
 * Copyright (c) 2026, Josh
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.ui.theme;

import java.awt.Color;
import java.awt.image.BufferedImage;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;

/**
 * The semantic design tokens for the client UI (see docs/ui-rework/design/TOKENS.md).
 *
 * A theme is resolved once at startup, before the look-and-feel is installed —
 * {@link net.runelite.client.ui.laf.RuneLiteLAF} exports the {@link ColorScheme}
 * aliases into FlatLaf {@code @variables} at construction time, so theme changes
 * require a client restart.
 */
@Getter
public final class Theme
{
	public static final Color DEFAULT_ACCENT = new Color(220, 138, 0);

	// typography role sizes (see TOKENS.md). The RuneScape TTFs are pixel fonts
	// on a 16px grid — off-grid sizes (e.g. 18) rasterize with broken stems, so
	// roles stay at the native size; the small role is smaller via its face
	public static final int FONT_SIZE_TITLE = 16;
	public static final int FONT_SIZE_BODY = 16;
	public static final int FONT_SIZE_SMALL = 16;

	// spacing scale
	public static final int SPACE_2 = 2;
	public static final int SPACE_4 = 4;
	public static final int SPACE_8 = 8;
	public static final int SPACE_12 = 12;
	public static final int SPACE_16 = 16;
	public static final int SPACE_24 = 24;

	// corner radii (kept square by decision; tokens exist so a restyle stays a data change)
	public static final int RADIUS_CONTROL = 0;
	public static final int RADIUS_CARD = 0;

	private static Theme active = dark(DEFAULT_ACCENT);

	// surfaces
	private final Color surface;
	private final Color surfaceSunken;
	private final Color surfaceRaised;
	private final Color surfaceHover;
	private final Color surfaceSunkenHover;
	private final Color control;
	private final Color scrollTrack;

	// lines & selection
	private final Color border;
	private final Color borderSubtle;
	private final Color selection;

	// text
	private final Color textPrimary;
	private final Color textMuted;
	private final Color textDisabled;

	// accent (user-adjustable; derived values computed in dark())
	private final Color accent;
	private final Color accentHover;
	private final Color accentMuted;
	private final Color onAccent;

	// status & data (fixed; never follow the accent)
	private final Color success;
	private final Color warning;
	private final Color error;
	private final Color info;
	private final Color positive;
	private final Color value;

	// overlay seeds (full overlay pass is Phase 5)
	private final Color overlayBackground;
	private final Color overlayText;

	private Theme(Color accent)
	{
		surface = new Color(32, 31, 28);
		surfaceSunken = new Color(23, 22, 19);
		surfaceRaised = new Color(42, 41, 37);
		surfaceHover = new Color(40, 39, 35);
		surfaceSunkenHover = new Color(33, 31, 27);
		control = new Color(25, 24, 19);
		scrollTrack = surfaceSunken;

		border = new Color(17, 16, 9);
		borderSubtle = new Color(51, 49, 43);
		selection = new Color(58, 56, 51);

		textPrimary = new Color(230, 227, 220);
		textMuted = new Color(168, 162, 150);
		textDisabled = new Color(95, 91, 83);

		this.accent = accent;
		accentHover = lighten(accent, .22f);
		accentMuted = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120);
		onAccent = luminance(accent) > .45 ? new Color(26, 18, 0) : Color.WHITE;

		success = new Color(75, 197, 94);
		warning = new Color(224, 160, 48);
		error = new Color(229, 72, 77);
		info = new Color(88, 166, 224);
		positive = new Color(110, 225, 110);
		value = new Color(240, 207, 123);

		overlayBackground = new Color(70, 61, 50, 156);
		overlayText = Color.WHITE;
	}

	/**
	 * The dark theme with the given accent color.
	 */
	public static Theme dark(Color accent)
	{
		return new Theme(accent);
	}

	public static Theme getActive()
	{
		return active;
	}

	/**
	 * Makes the given theme active and repoints the {@link ColorScheme} aliases at it.
	 * Must run before the LAF is installed for the FlatLaf variable export to see it.
	 */
	public static void install(Theme theme)
	{
		active = theme;
		ColorScheme.applyTheme(theme);
	}

	/**
	 * Remaps an icon authored in the default (orange) accent to the active accent.
	 * Hue and saturation follow the accent while per-pixel brightness ratios are
	 * preserved, so multi-tone icons keep their shading; near-grayscale pixels
	 * (shadows, knobs) are left alone. Identity when the accent is the default.
	 */
	public BufferedImage accentize(BufferedImage src)
	{
		float[] acc = Color.RGBtoHSB(accent.getRed(), accent.getGreen(), accent.getBlue(), null);
		float[] base = Color.RGBtoHSB(DEFAULT_ACCENT.getRed(), DEFAULT_ACCENT.getGreen(), DEFAULT_ACCENT.getBlue(), null);

		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < src.getHeight(); y++)
		{
			for (int x = 0; x < src.getWidth(); x++)
			{
				int argb = src.getRGB(x, y);
				float[] hsb = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
				if (hsb[1] >= .15f)
				{
					float bri = Math.min(1f, hsb[2] * acc[2] / base[2]);
					int rgb = Color.HSBtoRGB(acc[0], hsb[1] * acc[1], bri);
					argb = (argb & 0xFF000000) | (rgb & 0xFFFFFF);
				}
				out.setRGB(x, y, argb);
			}
		}
		return out;
	}

	private static Color lighten(Color c, float f)
	{
		return new Color(
			Math.min(255, Math.round(c.getRed() + (255 - c.getRed()) * f)),
			Math.min(255, Math.round(c.getGreen() + (255 - c.getGreen()) * f)),
			Math.min(255, Math.round(c.getBlue() + (255 - c.getBlue()) * f)));
	}

	private static double luminance(Color c)
	{
		return (.2126 * c.getRed() + .7152 * c.getGreen() + .0722 * c.getBlue()) / 255;
	}
}
