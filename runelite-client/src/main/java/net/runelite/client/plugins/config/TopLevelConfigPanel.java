/*
 * Copyright (c) 2023 Abex
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
package net.runelite.client.plugins.config;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridLayout;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

@Singleton
public class TopLevelConfigPanel extends PluginPanel
{
	private final MaterialTabGroup tabGroup;
	private final CardLayout layout;
	private final JPanel content;

	private final EventBus eventBus;
	private final PluginListPanel pluginListPanel;
	private final MaterialTab pluginListPanelTab;

	private boolean active = false;
	private PluginPanel current;

	@Inject
	TopLevelConfigPanel(
		EventBus eventBus,
		PluginListPanel pluginListPanel,
		ProfilePanel profilePanel
	)
	{
		super(false);

		this.eventBus = eventBus;

		tabGroup = new MaterialTabGroup();
		tabGroup.setLayout(new GridLayout(1, 0, 7, 7));
		tabGroup.setBorder(new EmptyBorder(10, 10, 0, 10));

		content = new JPanel();
		layout = new CardLayout();
		content.setLayout(layout);

		setLayout(new BorderLayout());
		add(tabGroup, BorderLayout.NORTH);
		add(content, BorderLayout.CENTER);

		this.pluginListPanel = pluginListPanel;
		pluginListPanelTab = addTab(pluginListPanel.getMuxer(), "Plugins");

		addTab(profilePanel, "Profiles");

		tabGroup.select(pluginListPanelTab);
	}

	private MaterialTab addTab(PluginPanel panel, String title)
	{
		MaterialTab mt = new MaterialTab(title, tabGroup, null);
		mt.setHorizontalAlignment(SwingConstants.CENTER);
		tabGroup.addTab(mt);

		content.add(title, panel.getWrappedPanel());
		eventBus.register(panel);

		mt.setOnSelectEvent(() ->
		{
			switchTo(title, panel);
			return true;
		});
		return mt;
	}

	private void switchTo(String cardName, PluginPanel panel)
	{
		PluginPanel prevPanel = current;
		if (active)
		{
			prevPanel.onDeactivate();
			panel.onActivate();
		}

		current = panel;

		layout.show(content, cardName);
		content.revalidate();
	}

	@Override
	public void onActivate()
	{
		active = true;
		current.onActivate();
	}

	@Override
	public void onDeactivate()
	{
		active = false;
		current.onDeactivate();
	}

	public void openConfigurationPanel(String name)
	{
		tabGroup.select(pluginListPanelTab);
		pluginListPanel.openConfigurationPanel(name);
	}

	public void openConfigurationPanel(Plugin plugin)
	{
		tabGroup.select(pluginListPanelTab);
		pluginListPanel.openConfigurationPanel(plugin);
	}

	public void openWithFilter(String filter)
	{
		tabGroup.select(pluginListPanelTab);
		pluginListPanel.openWithFilter(filter);
	}
}
