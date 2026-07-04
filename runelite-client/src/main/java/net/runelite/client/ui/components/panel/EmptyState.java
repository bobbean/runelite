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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.shadowlabel.JShadowedLabel;
import net.runelite.client.ui.theme.Theme;

/**
 * A centered empty/info state for panel content areas (COMPONENTS.md §5):
 * "no results", "nothing tracked yet", and the like. The themed replacement
 * for {@link net.runelite.client.ui.components.PluginErrorPanel}, which is now
 * a thin subclass of this.
 */
public class EmptyState extends JPanel
{
	private final JLabel title = new JShadowedLabel();
	private final JLabel description = new JShadowedLabel();

	public EmptyState()
	{
		setOpaque(false);
		setBorder(new EmptyBorder(2 * Theme.SPACE_24, Theme.SPACE_8, 0, Theme.SPACE_8));
		setLayout(new BorderLayout());

		title.setFont(FontManager.getTitleFont());
		title.setForeground(Theme.getActive().getTextPrimary());
		title.setHorizontalAlignment(SwingConstants.CENTER);

		description.setFont(FontManager.getSmallFont());
		description.setForeground(Theme.getActive().getTextMuted());
		description.setHorizontalAlignment(SwingConstants.CENTER);

		add(title, BorderLayout.NORTH);
		add(description, BorderLayout.CENTER);
	}

	public EmptyState(String title, String description)
	{
		this();
		setContent(title, description);
	}

	/**
	 * Changes the content of the panel to the given parameters.
	 * The description is wrapped in html so that its text can wrap.
	 */
	public void setContent(String title, String description)
	{
		this.title.setText(title);
		this.description.setText("<html><body style = 'text-align:center'>" + description + "</body></html>");
	}
}
