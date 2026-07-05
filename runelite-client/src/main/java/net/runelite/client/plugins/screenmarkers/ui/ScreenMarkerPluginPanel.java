/*
 * Copyright (c) 2018, Kamiel, <https://github.com/Kamielvf>
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
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
package net.runelite.client.plugins.screenmarkers.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.image.BufferedImage;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.Getter;
import net.runelite.client.plugins.screenmarkers.ScreenMarkerOverlay;
import net.runelite.client.plugins.screenmarkers.ScreenMarkerPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.ui.components.panel.IconButton;
import net.runelite.client.ui.components.panel.PanelHeader;
import net.runelite.client.ui.theme.Theme;
import net.runelite.client.util.ImageUtil;

public class ScreenMarkerPluginPanel extends PluginPanel
{
	private static final ImageIcon ADD_ICON;

	private static final Color DEFAULT_BORDER_COLOR = Color.GREEN;
	private static final Color DEFAULT_FILL_COLOR = new Color(0, 255, 0, 0);

	private static final int DEFAULT_BORDER_THICKNESS = 3;

	public static final Color SELECTED_COLOR = DEFAULT_BORDER_COLOR;
	public static final Color SELECTED_FILL_COLOR = DEFAULT_FILL_COLOR;
	public static final int SELECTED_BORDER_THICKNESS = DEFAULT_BORDER_THICKNESS;

	private final PanelHeader header = new PanelHeader("Screen Markers");
	private final IconButton addMarker;
	private final PluginErrorPanel noMarkersPanel = new PluginErrorPanel();
	private final JPanel markerView = new JPanel(new GridBagLayout());

	private final ScreenMarkerPlugin plugin;

	@Getter
	private final ScreenMarkerCreationPanel creationPanel;

	static
	{
		final BufferedImage addIcon = ImageUtil.loadImageResource(ScreenMarkerPlugin.class, "add_icon.png");
		ADD_ICON = new ImageIcon(addIcon);
	}

	public ScreenMarkerPluginPanel(ScreenMarkerPlugin screenMarkerPlugin)
	{
		this.plugin = screenMarkerPlugin;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(Theme.SPACE_8, Theme.SPACE_8, Theme.SPACE_8, Theme.SPACE_8));

		addMarker = header.addAction(ADD_ICON, "Add new screen marker", () -> setCreation(true));

		JPanel centerPanel = new JPanel(new BorderLayout());
		centerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		markerView.setBackground(ColorScheme.DARK_GRAY_COLOR);

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1;
		constraints.gridx = 0;
		constraints.gridy = 0;

		noMarkersPanel.setContent("Screen Markers", "Highlight a region on your screen.");
		noMarkersPanel.setVisible(false);

		markerView.add(noMarkersPanel, constraints);
		constraints.gridy++;

		creationPanel = new ScreenMarkerCreationPanel(plugin);
		creationPanel.setVisible(false);

		markerView.add(creationPanel, constraints);
		constraints.gridy++;

		centerPanel.add(markerView, BorderLayout.CENTER);

		add(header, BorderLayout.NORTH);
		add(centerPanel, BorderLayout.CENTER);
	}

	public void rebuild()
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1;
		constraints.gridx = 0;
		constraints.gridy = 0;

		markerView.removeAll();

		for (final ScreenMarkerOverlay marker : plugin.getScreenMarkers())
		{
			markerView.add(new ScreenMarkerPanel(plugin, marker), constraints);
			constraints.gridy++;

			markerView.add(Box.createRigidArea(new Dimension(0, 10)), constraints);
			constraints.gridy++;
		}

		boolean empty = constraints.gridy == 0;
		noMarkersPanel.setVisible(empty);
		header.setTitleVisible(!empty);

		markerView.add(noMarkersPanel, constraints);
		constraints.gridy++;

		markerView.add(creationPanel, constraints);
		constraints.gridy++;

		repaint();
		revalidate();
	}

	/* Enables/Disables new marker creation mode */
	public void setCreation(boolean on)
	{
		if (on)
		{
			noMarkersPanel.setVisible(false);
			header.setTitleVisible(true);
		}
		else
		{
			boolean empty = plugin.getScreenMarkers().isEmpty();
			noMarkersPanel.setVisible(empty);
			header.setTitleVisible(!empty);
		}

		creationPanel.setVisible(on);
		addMarker.setVisible(!on);

		if (on)
		{
			creationPanel.lockConfirm();
			plugin.setMouseListenerEnabled(true);
			plugin.setCreatingScreenMarker(true);
		}
	}
}
