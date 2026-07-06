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

import java.awt.Color;
import java.awt.Graphics;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.theme.Theme;

/**
 * A small toggleable filter chip: control background with a subtle border,
 * accent fill when selected. Extracted from PluginListPanel's category chips
 * (Phase 2) once the Plugin Hub window became its second consumer.
 */
public class Chip extends JToggleButton
{
	public Chip(String label)
	{
		super(label);
		setFont(FontManager.getSmallFont());
		setFocusable(false);
		setFocusPainted(false);
		setContentAreaFilled(false);
		setBorder(new EmptyBorder(3, Theme.SPACE_8, 4, Theme.SPACE_8));
		setRolloverEnabled(true);
		addChangeListener(ev -> updateForeground());
		updateForeground();
	}

	private void updateForeground()
	{
		Theme theme = Theme.getActive();
		Color fg;
		if (isSelected())
		{
			fg = theme.getOnAccent();
		}
		else if (!isEnabled())
		{
			fg = theme.getTextDisabled();
		}
		else if (getModel().isRollover())
		{
			fg = theme.getTextPrimary();
		}
		else
		{
			fg = theme.getTextMuted();
		}

		if (!fg.equals(getForeground()))
		{
			setForeground(fg);
		}
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Theme theme = Theme.getActive();
		if (isSelected())
		{
			g.setColor(theme.getAccent());
			g.fillRect(0, 0, getWidth(), getHeight());
		}
		else
		{
			g.setColor(getModel().isRollover() && isEnabled() ? theme.getSurfaceHover() : theme.getControl());
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(theme.getBorderSubtle());
			g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
		}
		super.paintComponent(g);
	}
}
