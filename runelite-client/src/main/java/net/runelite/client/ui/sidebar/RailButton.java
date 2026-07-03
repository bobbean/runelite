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
package net.runelite.client.ui.sidebar;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.theme.Theme;
import net.runelite.client.util.ImageUtil;

/**
 * One icon in the {@link SidebarRail}: hover/active states from the theme
 * tokens, active shown as a 2px accent bar on the outer (right) edge.
 */
class RailButton extends JButton
{
	static final int WIDTH = 36;
	static final int HEIGHT = 30;
	private static final int ICON_SIZE = 16;
	private static final int ACCENT_BAR_WIDTH = 2;

	@Getter
	private final NavigationButton navBtn;

	@Getter
	@Setter
	private boolean active;

	// set while a drag gesture ends on this button, so the release doesn't
	// also fire the select action
	private boolean suppressAction;

	RailButton(NavigationButton navBtn)
	{
		this.navBtn = navBtn;

		BufferedImage icon = navBtn.getIcon();
		if (icon.getWidth() != ICON_SIZE || icon.getHeight() != ICON_SIZE)
		{
			icon = ImageUtil.resizeImage(icon, ICON_SIZE, ICON_SIZE);
		}
		setIcon(new ImageIcon(icon));
		setToolTipText(navBtn.getTooltip());

		setFocusable(false);
		setBorder(null);
		setBorderPainted(false);
		setContentAreaFilled(false);
		setFocusPainted(false);
		setOpaque(false);
		setRolloverEnabled(true);
	}

	void suppressAction()
	{
		suppressAction = true;
	}

	boolean consumeSuppressAction()
	{
		boolean v = suppressAction;
		suppressAction = false;
		return v;
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(WIDTH, HEIGHT);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Theme theme = Theme.getActive();

		if (active)
		{
			g.setColor(theme.getSurfaceRaised());
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(theme.getAccent());
			g.fillRect(getWidth() - ACCENT_BAR_WIDTH, 2, ACCENT_BAR_WIDTH, getHeight() - 4);
		}
		else if (getModel().isRollover() || getModel().isPressed())
		{
			g.setColor(theme.getSurfaceHover());
			g.fillRect(0, 0, getWidth(), getHeight());
		}

		super.paintComponent(g);
	}
}
