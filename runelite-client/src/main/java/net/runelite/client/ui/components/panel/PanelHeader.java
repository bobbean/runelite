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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.theme.Theme;
import net.runelite.client.util.ImageUtil;

/**
 * The one-line panel title bar (COMPONENTS.md §1): title on the left, an
 * optional leading back button, and trailing icon action slots. Carries its
 * own bottom rule and the standard gap to the content below, so callers just
 * add it at the top of the panel.
 *
 * Per D1 this is adopted only where a panel already has a hand-rolled header —
 * panels without a title don't gain one.
 */
public class PanelHeader extends JPanel
{
	private static final ImageIcon BACK_ICON = new ImageIcon(ImageUtil.loadImageResource(PanelHeader.class, "back_icon.png"));

	private final JLabel title;
	private final JPanel actions;

	public PanelHeader(String text)
	{
		this(text, null);
	}

	/**
	 * @param onBack when non-null, a leading back button is shown (drill-in views)
	 */
	public PanelHeader(String text, Runnable onBack)
	{
		setLayout(new BorderLayout(Theme.SPACE_4, 0));
		setOpaque(false);
		setBorder(new CompoundBorder(
			new EmptyBorder(0, 0, Theme.SPACE_8, 0),
			new CompoundBorder(
				new MatteBorder(0, 0, 1, 0, Theme.getActive().getBorderSubtle()),
				new EmptyBorder(0, 0, Theme.SPACE_4, 0))));

		if (onBack != null)
		{
			add(new IconButton(BACK_ICON, "Back", onBack), BorderLayout.WEST);
		}

		title = new JLabel(text);
		title.setFont(FontManager.getTitleFont());
		title.setForeground(Theme.getActive().getTextPrimary());
		add(title, BorderLayout.CENTER);

		actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_4, 0));
		actions.setOpaque(false);
		add(actions, BorderLayout.EAST);
	}

	/**
	 * Appends a trailing action button and returns it so callers can toggle
	 * visibility or swap its icon.
	 */
	public IconButton addAction(Icon icon, String tooltip, Runnable onClick)
	{
		IconButton button = new IconButton(icon, tooltip, onClick);
		actions.add(button);
		return button;
	}

	/**
	 * Appends a trailing stateful action; selected state renders in the accent.
	 */
	public IconToggleButton addToggleAction(ImageIcon icon, String tooltip)
	{
		IconToggleButton button = new IconToggleButton(icon, tooltip);
		actions.add(button);
		return button;
	}

	public void setTitle(String text)
	{
		title.setText(text);
	}

	public void setTitleVisible(boolean visible)
	{
		title.setVisible(visible);
	}
}
