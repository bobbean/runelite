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
import java.awt.Dimension;
import javax.swing.Icon;
import javax.swing.JButton;

/**
 * A borderless icon action button (see docs/ui-rework/design/COMPONENTS.md §4).
 *
 * Hover feedback is a background fill, not an icon swap — the
 * {@code panelIconButton} style class in RuneLiteLAF.properties supplies the
 * hover/pressed backgrounds from the theme. For a stateful (on/off) action use
 * {@link IconToggleButton}.
 */
public class IconButton extends JButton
{
	static final Dimension HIT_AREA = new Dimension(22, 22);

	public IconButton(Icon icon, String tooltip)
	{
		super(icon);
		setToolTipText(tooltip);
		putClientProperty(FlatClientProperties.STYLE_CLASS, "panelIconButton");
		setPreferredSize(HIT_AREA);
	}

	public IconButton(Icon icon, String tooltip, Runnable onClick)
	{
		this(icon, tooltip);
		addActionListener(ev -> onClick.run());
	}
}
