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
import java.awt.Component;
import java.util.Locale;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.theme.Theme;

/**
 * The small-caps section label with a subtle bottom rule (COMPONENTS.md §2,
 * NAVIGATION.md §4) — the hierarchy device used for list groupings ("Pinned",
 * "All plugins") and by {@link Section} for collapsible config sections. Usable
 * standalone in flat lists where grouping into a body container isn't practical.
 */
public class SectionHeader extends JPanel
{
	private final JLabel label;

	public SectionHeader(String name)
	{
		setLayout(new BorderLayout());
		setOpaque(false);
		// The single pixel of right border keeps the header from extending out
		// by one pixel when a collapsible section is closed (see Section).
		setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, Theme.getActive().getBorderSubtle()),
			new EmptyBorder(0, 0, 3, 1)));

		label = new JLabel(name.toUpperCase(Locale.ROOT));
		label.setFont(FontManager.getSmallFont());
		label.setForeground(Theme.getActive().getTextMuted());
		add(label, BorderLayout.CENTER);
	}

	/**
	 * Tooltip on the label; pass the un-capitalized name plus description.
	 */
	public void setLabelTooltip(String tooltip)
	{
		label.setToolTipText(tooltip);
	}

	JLabel getLabel()
	{
		return label;
	}

	void addLeading(Component component)
	{
		add(component, BorderLayout.WEST);
	}
}
