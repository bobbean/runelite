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
package net.runelite.client.ui.components.panel;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JToggleButton;
import net.runelite.client.ui.theme.Theme;
import net.runelite.client.util.ImageUtil;

/**
 * The stateful variant of {@link IconButton}: an on/off action whose selected
 * state renders the icon in the accent color (COMPONENTS.md §4, D3 — callers
 * stop shipping a second "on" icon asset; pass one explicitly via
 * {@link #setSelectedIcon} only when the accent tint isn't right).
 */
public class IconToggleButton extends JToggleButton
{
	public IconToggleButton(ImageIcon icon, String tooltip)
	{
		super(icon);
		setToolTipText(tooltip);
		putClientProperty(FlatClientProperties.STYLE_CLASS, "panelIconButton");
		setPreferredSize(IconButton.HIT_AREA);
		setSelectedIcon(accentTint(icon));
	}

	private static Icon accentTint(ImageIcon icon)
	{
		BufferedImage src = ImageUtil.bufferedImageFromImage(icon.getImage());
		return new ImageIcon(ImageUtil.recolorImage(src, Theme.getActive().getAccent()));
	}
}
