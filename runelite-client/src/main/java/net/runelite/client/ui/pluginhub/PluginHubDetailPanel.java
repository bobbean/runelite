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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import net.runelite.client.externalplugins.PluginHubManifest;
import net.runelite.client.plugins.info.JRichTextPane;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.panel.Chip;
import net.runelite.client.ui.components.panel.IconButton;
import net.runelite.client.ui.components.panel.SectionHeader;
import net.runelite.client.ui.theme.Theme;
import net.runelite.client.util.QuantityFormatter;

/**
 * The drill-in detail page for a single plugin (PLUGIN_HUB.md D4): big icon,
 * full metadata, complete description, warning block, and all actions plus an
 * "Open website" jump (which replaces the old {@code ?} help button as the
 * path to the README/screenshots the manifest doesn't carry).
 */
class PluginHubDetailPanel extends JPanel
{
	private static final int ICON_SIZE = 96;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
		.withZone(ZoneId.systemDefault());

	PluginHubDetailPanel(PluginHubWindow window, HubPluginEntry entry)
	{
		Theme theme = Theme.getActive();
		PluginHubManifest.DisplayData m = entry.getManifest();

		setLayout(new BorderLayout());
		setBackground(theme.getSurface());
		setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_12, Theme.SPACE_12, Theme.SPACE_12, Theme.SPACE_12));

		// header: back + title
		JLabel title = new JLabel(m.getDisplayName());
		title.setFont(FontManager.getTitleFont());
		title.setForeground(theme.getTextPrimary());

		JPanel header = new JPanel(new BorderLayout(Theme.SPACE_4, 0));
		header.setOpaque(false);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createEmptyBorder(0, 0, Theme.SPACE_8, 0),
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, theme.getBorderSubtle()),
				BorderFactory.createEmptyBorder(0, 0, Theme.SPACE_8, 0))));
		header.add(new IconButton(loadBackIcon(), "Back", window::showBrowse), BorderLayout.WEST);
		header.add(title, BorderLayout.CENTER);
		add(header, BorderLayout.NORTH);

		// top block: icon | meta / tags / actions / warning
		JLabel icon = window.new PluginIcon(m);
		icon.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));
		icon.setHorizontalAlignment(JLabel.CENTER);
		JPanel iconWrap = new JPanel(new BorderLayout());
		iconWrap.setOpaque(false);
		iconWrap.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_12, 0, 0, Theme.SPACE_16));
		iconWrap.add(icon, BorderLayout.NORTH);

		JPanel column = new JPanel();
		column.setOpaque(false);
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_12, 0, 0, 0));

		column.add(leftLabel(metaLine(entry), FontManager.getSmallFont(), theme.getTextMuted()));

		if (m.getTags() != null && m.getTags().length > 0)
		{
			JPanel tags = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_4, 0));
			tags.setOpaque(false);
			tags.setAlignmentX(Component.LEFT_ALIGNMENT);
			for (String tag : m.getTags())
			{
				Chip chip = new Chip(tag);
				chip.setEnabled(false);
				tags.add(chip);
			}
			column.add(Box.createVerticalStrut(Theme.SPACE_8));
			column.add(tags);
		}

		column.add(Box.createVerticalStrut(Theme.SPACE_16));
		column.add(buildActions(window, entry));

		if (m.getWarning() != null)
		{
			JLabel warning = new JLabel("<html>⚠ " + m.getWarning() + "</html>");
			warning.setFont(FontManager.getSmallFont());
			warning.setForeground(theme.getWarning());
			warning.setAlignmentX(Component.LEFT_ALIGNMENT);
			warning.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(theme.getWarning()),
				BorderFactory.createEmptyBorder(Theme.SPACE_8, Theme.SPACE_8, Theme.SPACE_8, Theme.SPACE_8)));
			column.add(Box.createVerticalStrut(Theme.SPACE_12));
			column.add(warning);
		}

		JPanel infoRow = new JPanel(new BorderLayout());
		infoRow.setOpaque(false);
		infoRow.add(iconWrap, BorderLayout.WEST);
		infoRow.add(column, BorderLayout.CENTER);

		// description: the manifest's short blurb shows instantly, then the full
		// README (fetched from the plugin's source repo) replaces it once loaded
		JRichTextPane descPane = new JRichTextPane();
		descPane.setContentType("text/html");
		descPane.setFont(FontManager.getSmallFont());
		descPane.setForeground(theme.getTextPrimary());
		descPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		descPane.setText(entry.getDescriptionHtml());
		descPane.setCaretPosition(0);

		JPanel descArea = new JPanel(new BorderLayout(0, Theme.SPACE_8));
		descArea.setOpaque(false);
		descArea.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_16, 0, 0, 0));
		descArea.add(new SectionHeader("Description"), BorderLayout.NORTH);
		descArea.add(descPane, BorderLayout.CENTER);

		PluginHubWindow.WidthTrackingView content = new PluginHubWindow.WidthTrackingView();
		content.add(infoRow, BorderLayout.NORTH);
		content.add(descArea, BorderLayout.CENTER);

		JScrollPane scroll = new JScrollPane(content);
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);

		if (!entry.isUnavailable())
		{
			window.loadFullDescription(m.getInternalName(), html ->
			{
				if (html != null && !html.isEmpty())
				{
					descPane.setText(html);
					descPane.setCaretPosition(0);
				}
			});
		}
	}

	private JPanel buildActions(PluginHubWindow window, HubPluginEntry entry)
	{
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_8, 0));
		actions.setOpaque(false);
		actions.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton primary = window.makeActionButton(entry);
		enlarge(primary);
		actions.add(primary);

		// an installed plugin with an available update shows Remove too, so the
		// user isn't forced to update just to get rid of it
		if (entry.getAction() == HubPluginEntry.Action.UPDATE)
		{
			JButton remove = window.makeRemoveButton(entry);
			enlarge(remove);
			actions.add(remove);
		}

		if (!entry.getLoadedPlugins().isEmpty())
		{
			JButton configure = neutralButton("⚙ Configure");
			configure.addActionListener(e -> window.openConfig(entry));
			actions.add(configure);
		}

		JButton website = neutralButton("Open website ↗");
		website.addActionListener(e -> window.openWebsite(entry));
		actions.add(website);

		return actions;
	}

	private static JButton neutralButton(String text)
	{
		Theme theme = Theme.getActive();
		JButton button = new JButton(text);
		button.setFont(FontManager.getSmallFont());
		button.setFocusPainted(false);
		button.setContentAreaFilled(false);
		button.setForeground(theme.getTextPrimary());
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(theme.getBorderSubtle()),
			BorderFactory.createEmptyBorder(6, Theme.SPACE_16, 6, Theme.SPACE_16)));
		return button;
	}

	private static void enlarge(JButton button)
	{
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(button.getForeground()),
			BorderFactory.createEmptyBorder(6, Theme.SPACE_16, 6, Theme.SPACE_16)));
	}

	private static String metaLine(HubPluginEntry entry)
	{
		PluginHubManifest.DisplayData m = entry.getManifest();
		StringBuilder sb = new StringBuilder("<html>");
		sb.append("by <b>").append(escape(m.getAuthor())).append("</b>");
		if (entry.getUserCount() > 0)
		{
			sb.append("&nbsp;&nbsp;·&nbsp;&nbsp;<b>")
				.append(QuantityFormatter.quantityToStackSize(entry.getUserCount()))
				.append("</b> users");
		}
		sb.append("&nbsp;&nbsp;·&nbsp;&nbsp;v<b>").append(escape(m.getVersion())).append("</b>");
		if (entry.isUpdateAvailable())
		{
			sb.append(" (update available)");
		}
		String updated = formatTimestamp(m.getLastUpdatedAt());
		if (updated != null)
		{
			sb.append("&nbsp;&nbsp;·&nbsp;&nbsp;updated <b>").append(updated).append("</b>");
		}
		sb.append("</html>");
		return sb.toString();
	}

	// createdAt/lastUpdatedAt units aren't asserted anywhere in the client;
	// detect seconds vs millis so either encoding renders. 0/absent => hidden.
	private static String formatTimestamp(long value)
	{
		if (value <= 0)
		{
			return null;
		}
		long millis = value < 100_000_000_000L ? value * 1000L : value;
		return DATE.format(Instant.ofEpochMilli(millis));
	}

	private static JLabel leftLabel(String text, java.awt.Font font, java.awt.Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(font);
		label.setForeground(color);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static javax.swing.ImageIcon loadBackIcon()
	{
		return new javax.swing.ImageIcon(net.runelite.client.util.ImageUtil.loadImageResource(
			net.runelite.client.ui.components.panel.PanelHeader.class, "back_icon.png"));
	}

	private static String escape(String s)
	{
		return com.google.common.html.HtmlEscapers.htmlEscaper().escape(s);
	}
}
