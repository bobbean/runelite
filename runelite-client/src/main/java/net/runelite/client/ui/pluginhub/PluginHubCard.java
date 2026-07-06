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
package net.runelite.client.ui.pluginhub;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.panel.Card;
import net.runelite.client.ui.components.panel.IconButton;
import net.runelite.client.ui.theme.Theme;
import net.runelite.client.util.QuantityFormatter;

/**
 * One plugin in the hub browse grid (PLUGIN_HUB.md D2): icon, name,
 * author + user count, two-line description, version + configure + action.
 * Clicking anywhere that isn't a button drills into the detail view (D4).
 */
class PluginHubCard extends Card
{
	private static final int ICON_SIZE = 48;

	PluginHubCard(PluginHubWindow window, HubPluginEntry entry)
	{
		Theme theme = Theme.getActive();
		setLayout(new BorderLayout(Theme.SPACE_8, 0));

		// icon, top-aligned
		JLabel icon = window.new PluginIcon(entry.getManifest());
		icon.setHorizontalAlignment(JLabel.CENTER);
		icon.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));
		JPanel iconWrap = new JPanel(new BorderLayout());
		iconWrap.setOpaque(false);
		iconWrap.add(icon, BorderLayout.NORTH);
		add(iconWrap, BorderLayout.WEST);

		// name / meta / description
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

		JLabel name = new JLabel(entry.getManifest().getDisplayName());
		name.setFont(FontManager.getTitleFont());
		name.setForeground(theme.getTextPrimary());
		if (entry.isUnavailable())
		{
			name.setIcon(PluginHubWindow.ALERT_ICON);
			name.setHorizontalTextPosition(JLabel.LEADING);
			name.setToolTipText(entry.getDescriptionHtml());
		}

		String meta = entry.getManifest().getAuthor();
		if (entry.getUserCount() > 0)
		{
			meta += " · " + QuantityFormatter.quantityToStackSize(entry.getUserCount()) + " users";
		}
		JLabel metaLabel = new JLabel(meta);
		metaLabel.setFont(FontManager.getSmallFont());
		metaLabel.setForeground(theme.getTextMuted());

		JLabel description = new JLabel(entry.getDescriptionHtml());
		description.setFont(FontManager.getSmallFont());
		description.setForeground(entry.isUnavailable() ? theme.getWarning() : theme.getTextMuted());
		description.setVerticalAlignment(JLabel.TOP);
		description.setToolTipText(entry.getDescriptionHtml());
		int lineHeight = description.getFontMetrics(description.getFont()).getHeight();
		clampHeight(description, lineHeight * 2 + 2);

		text.add(name);
		text.add(metaLabel);
		text.add(description);
		add(text, BorderLayout.CENTER);

		// footer: version left, configure + action right
		JLabel version = new JLabel(entry.getManifest().getVersion());
		version.setFont(FontManager.getSmallFont());
		version.setForeground(theme.getTextDisabled());

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_4, 0));
		actions.setOpaque(false);
		if (!entry.getLoadedPlugins().isEmpty())
		{
			actions.add(new IconButton(PluginHubWindow.CONFIGURE_ICON, "Configure", () -> window.openConfig(entry)));
		}
		actions.add(window.makeActionButton(entry));

		JPanel footer = new JPanel(new BorderLayout());
		footer.setOpaque(false);
		footer.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_4, 0, 0, 0));
		footer.add(version, BorderLayout.WEST);
		footer.add(actions, BorderLayout.EAST);
		add(footer, BorderLayout.SOUTH);

		// hover + click-to-detail on everything that isn't a button
		MouseAdapter hover = hoverListener();
		MouseAdapter open = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					window.showDetail(entry);
				}
			}
		};
		for (JComponent c : new JComponent[]{this, iconWrap, icon, text, name, metaLabel, description, footer, version})
		{
			c.addMouseListener(hover);
			c.addMouseListener(open);
		}
		setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
	}

	private static void clampHeight(JComponent c, int height)
	{
		c.setPreferredSize(new Dimension(0, height));
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
	}
}
