/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
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

import com.google.common.collect.ImmutableList;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.MultiplexingPluginPanel;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.theme.Theme;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
class PluginListPanel extends PluginPanel
{
	private static final String RUNELITE_GROUP_NAME = RuneLiteConfig.class.getAnnotation(ConfigGroup.class).value();
	private static final String PINNED_PLUGINS_CONFIG_KEY = "pinnedPlugins";
	private static final ImmutableList<String> CATEGORY_TAGS = ImmutableList.of(
		"Combat",
		"Chat",
		"Item",
		"Minigame",
		"Notification",
		"Plugin Hub",
		"Skilling",
		"XP"
	);

	private final ConfigManager configManager;
	private final PluginManager pluginManager;
	private final Provider<ConfigPanel> configPanelProvider;
	private final Provider<PluginHubPanel> pluginHubPanelProvider;
	private final List<PluginConfigurationDescriptor> fakePlugins = new ArrayList<>();

	@Getter
	private final ExternalPluginManager externalPluginManager;

	@Getter
	private final MultiplexingPluginPanel muxer;

	private final IconTextField searchBar;
	private final JScrollPane scrollPane;
	private final FixedWidthPanel mainPanel;
	private final List<CategoryChip> categoryChips = new ArrayList<>();
	private List<PluginListItem> pluginList;

	// search term of the selected category chip; null = All
	private String selectedCategory;

	@Inject
	public PluginListPanel(
		ConfigManager configManager,
		PluginManager pluginManager,
		ExternalPluginManager externalPluginManager,
		EventBus eventBus,
		Provider<ConfigPanel> configPanelProvider,
		Provider<PluginHubPanel> pluginHubPanelProvider)
	{
		super(false);

		this.configManager = configManager;
		this.pluginManager = pluginManager;
		this.externalPluginManager = externalPluginManager;
		this.configPanelProvider = configPanelProvider;
		this.pluginHubPanelProvider = pluginHubPanelProvider;

		muxer = new MultiplexingPluginPanel(this)
		{
			@Override
			protected void onAdd(PluginPanel p)
			{
				eventBus.register(p);
			}

			@Override
			protected void onRemove(PluginPanel p)
			{
				eventBus.unregister(p);
			}
		};

		searchBar = new IconTextField();
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchBar.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				onSearchBarChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				onSearchBarChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				onSearchBarChanged();
			}
		});
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// category chips replace the old search-suggestion dropdown
		// (see docs/ui-rework/design/NAVIGATION.md §3)
		JPanel chipBar = new JPanel(new DynamicGridLayout(0, 3, 4, 4));
		chipBar.setOpaque(false);
		ButtonGroup chipGroup = new ButtonGroup();
		addCategoryChip(chipBar, chipGroup, "All", null).setSelected(true);
		for (String tag : CATEGORY_TAGS)
		{
			addCategoryChip(chipBar, chipGroup, tag, tag.replace(" ", "").toLowerCase(Locale.ROOT));
		}

		JPanel topPanel = new JPanel();
		topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		topPanel.setLayout(new BorderLayout(0, BORDER_OFFSET));
		topPanel.add(searchBar, BorderLayout.CENTER);
		topPanel.add(chipBar, BorderLayout.SOUTH);
		add(topPanel, BorderLayout.NORTH);

		mainPanel = new FixedWidthPanel();
		mainPanel.setBorder(new EmptyBorder(8, 10, 10, 10));
		mainPanel.setLayout(new DynamicGridLayout(0, 1, 0, 5));
		mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel northPanel = new FixedWidthPanel();
		northPanel.setLayout(new BorderLayout());
		northPanel.add(mainPanel, BorderLayout.NORTH);

		scrollPane = new JScrollPane(northPanel);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(scrollPane, BorderLayout.CENTER);

		// the Plugin Hub lives here now, not in the top tab strip; it becomes
		// its own window in Phase 4 (see docs/ui-rework/design/NAVIGATION.md §2)
		JButton hubButton = new JButton("Browse Plugin Hub...");
		hubButton.addActionListener(ev -> muxer.pushState(pluginHubPanelProvider.get()));

		JPanel hubRow = new JPanel(new BorderLayout());
		hubRow.setBorder(new EmptyBorder(0, 10, 10, 10));
		hubRow.add(hubButton, BorderLayout.CENTER);
		add(hubRow, BorderLayout.SOUTH);
	}

	void rebuildPluginList()
	{
		final List<String> pinnedPlugins = getPinnedPluginNames();

		// populate pluginList with all non-hidden plugins
		pluginList = Stream.concat(
			fakePlugins.stream(),
			pluginManager.getPlugins().stream()
				.filter(plugin -> !plugin.getClass().getAnnotation(PluginDescriptor.class).hidden())
				.map(plugin ->
				{
					PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
					Config config = pluginManager.getPluginConfigProxy(plugin);
					ConfigDescriptor configDescriptor = config == null ? null : configManager.getConfigDescriptor(config);
					List<String> conflicts = pluginManager.conflictsForPlugin(plugin).stream()
						.map(Plugin::getName)
						.collect(Collectors.toList());

					return new PluginConfigurationDescriptor(
						descriptor.name(),
						descriptor.description(),
						descriptor.tags(),
						plugin,
						config,
						configDescriptor,
						conflicts);
				})
		)
			.map(desc ->
			{
				PluginListItem listItem = new PluginListItem(this, desc);
				listItem.setPinned(pinnedPlugins.contains(desc.getName().replace(",", "")));
				return listItem;
			})
			.sorted(Comparator.comparing(p -> p.getPluginConfig().getName()))
			.collect(Collectors.toList());

		mainPanel.removeAll();
		refresh();
	}

	void addFakePlugin(PluginConfigurationDescriptor... descriptor)
	{
		Collections.addAll(fakePlugins, descriptor);
	}

	void refresh()
	{
		// update enabled / disabled status of all items
		pluginList.forEach(listItem ->
		{
			final Plugin plugin = listItem.getPluginConfig().getPlugin();
			if (plugin != null)
			{
				listItem.setPluginEnabled(pluginManager.isPluginActive(plugin));
			}
		});

		int scrollBarPosition = scrollPane.getVerticalScrollBar().getValue();

		onSearchBarChanged();
		searchBar.requestFocusInWindow();
		validate();

		scrollPane.getVerticalScrollBar().setValue(scrollBarPosition);
	}

	void openWithFilter(String filter)
	{
		searchBar.setText(filter);
		onSearchBarChanged();
		muxer.pushState(this);
	}

	private void onSearchBarChanged()
	{
		final String text = searchBar.getText();
		final boolean searching = !text.isEmpty();
		mainPanel.removeAll();

		// a text query overrides category browsing
		for (CategoryChip chip : categoryChips)
		{
			chip.setEnabled(!searching);
		}

		final Comparator<PluginListItem> byName = Comparator.comparing(p -> p.getPluginConfig().getName());
		if (searching)
		{
			PluginSearch.search(pluginList, text).forEach(mainPanel::add);
		}
		else if (selectedCategory != null)
		{
			pluginList.stream()
				.filter(item -> Text.matchesSearchTerms(List.of(selectedCategory), item.getKeywords()))
				.sorted(byName)
				.forEach(mainPanel::add);
		}
		else
		{
			List<PluginListItem> pinned = pluginList.stream()
				.filter(PluginListItem::isPinned)
				.sorted(byName)
				.collect(Collectors.toList());

			if (!pinned.isEmpty())
			{
				mainPanel.add(sectionHeader("Pinned"));
				pinned.forEach(mainPanel::add);
				mainPanel.add(sectionHeader("All plugins"));
			}

			pluginList.stream()
				.filter(item -> !item.isPinned())
				.sorted(byName)
				.forEach(mainPanel::add);
		}
		mainPanel.revalidate();
		mainPanel.repaint();
		revalidate();
	}

	private CategoryChip addCategoryChip(JPanel chipBar, ButtonGroup group, String label, String searchTerm)
	{
		CategoryChip chip = new CategoryChip(label);
		chip.addActionListener(ev ->
		{
			selectedCategory = searchTerm;
			onSearchBarChanged();
		});
		group.add(chip);
		chipBar.add(chip);
		categoryChips.add(chip);
		return chip;
	}

	private static JLabel sectionHeader(String text)
	{
		JLabel label = new JLabel(text.toUpperCase(Locale.ROOT));
		label.setFont(FontManager.getSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(new EmptyBorder(Theme.SPACE_8, Theme.SPACE_2, 0, 0));
		return label;
	}

	private static class CategoryChip extends JToggleButton
	{
		CategoryChip(String label)
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

	void openConfigurationPanel(String configGroup)
	{
		for (PluginListItem pluginListItem : pluginList)
		{
			if (pluginListItem.getPluginConfig().getName().equals(configGroup))
			{
				openConfigurationPanel(pluginListItem.getPluginConfig());
				break;
			}
		}
	}

	void openConfigurationPanel(Plugin plugin)
	{
		for (PluginListItem pluginListItem : pluginList)
		{
			if (pluginListItem.getPluginConfig().getPlugin() == plugin)
			{
				openConfigurationPanel(pluginListItem.getPluginConfig());
				break;
			}
		}
	}

	void openConfigurationPanel(PluginConfigurationDescriptor plugin)
	{
		ConfigPanel panel = configPanelProvider.get();
		panel.init(plugin);
		muxer.pushState(this);
		muxer.pushState(panel);
	}

	void startPlugin(Plugin plugin)
	{
		pluginManager.setPluginEnabled(plugin, true);

		try
		{
			pluginManager.startPlugin(plugin);
		}
		catch (PluginInstantiationException ex)
		{
			log.warn("Error when starting plugin {}", plugin.getClass().getSimpleName(), ex);
		}
	}

	void stopPlugin(Plugin plugin)
	{
		pluginManager.setPluginEnabled(plugin, false);

		try
		{
			pluginManager.stopPlugin(plugin);
		}
		catch (PluginInstantiationException ex)
		{
			log.warn("Error when stopping plugin {}", plugin.getClass().getSimpleName(), ex);
		}
	}

	private List<String> getPinnedPluginNames()
	{
		final String config = configManager.getConfiguration(RUNELITE_GROUP_NAME, PINNED_PLUGINS_CONFIG_KEY);

		if (config == null)
		{
			return Collections.emptyList();
		}

		return Text.fromCSV(config);
	}

	void savePinnedPlugins()
	{
		final String value = pluginList.stream()
			.filter(PluginListItem::isPinned)
			.map(p -> p.getPluginConfig().getName().replace(",", ""))
			.collect(Collectors.joining(","));

		configManager.setConfiguration(RUNELITE_GROUP_NAME, PINNED_PLUGINS_CONFIG_KEY, value);
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		SwingUtilities.invokeLater(this::refresh);
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(PANEL_WIDTH + SCROLLBAR_WIDTH, super.getPreferredSize().height);
	}

	@Override
	public void onActivate()
	{
		super.onActivate();

		if (searchBar.getParent() != null)
		{
			searchBar.requestFocusInWindow();
		}
	}

	@Subscribe
	private void onExternalPluginsChanged(ExternalPluginsChanged ev)
	{
		SwingUtilities.invokeLater(this::rebuildPluginList);
	}

	@Subscribe
	private void onProfileChanged(ProfileChanged ev)
	{
		SwingUtilities.invokeLater(this::rebuildPluginList);
	}
}
