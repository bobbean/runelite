/*
 * Copyright (c) 2026, Josh
 * Copyright (c) 2019 Abex
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.externalplugins.ExternalPluginClient;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.externalplugins.PluginHubManifest;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.config.PluginSearch;
import net.runelite.client.plugins.config.TopLevelConfigPanel;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.panel.Chip;
import net.runelite.client.ui.components.panel.EmptyState;
import net.runelite.client.ui.components.panel.IconButton;
import net.runelite.client.ui.theme.Theme;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.VerificationException;

/**
 * The Plugin Hub as its own window (Phase 4, PLUGIN_HUB.md). Singleton;
 * created on first open, hidden (not disposed) on close. Replaces
 * PluginHubPanel.
 */
@Slf4j
@Singleton
public class PluginHubWindow extends JFrame
{
	private static final String CONFIG_GROUP = "runelite";
	private static final String CONFIG_BOUNDS = "pluginHubWindowBounds";

	private static final Dimension DEFAULT_SIZE = new Dimension(860, 620);
	private static final Dimension MIN_SIZE = new Dimension(640, 480);

	private static final int CARD_MIN_WIDTH = 280;
	private static final int MAX_TAG_CHIPS = 8;

	static final ImageIcon MISSING_ICON;
	static final ImageIcon CONFIGURE_ICON;
	static final ImageIcon ALERT_ICON;

	// browse-center card names
	private static final String STATE_GRID = "grid";
	private static final String STATE_LOADING = "loading";
	private static final String STATE_FAILED = "failed";

	// root card names
	private static final String VIEW_BROWSE = "browse";
	private static final String VIEW_DETAIL = "detail";

	static
	{
		MISSING_ICON = new ImageIcon(ImageUtil.loadImageResource(PluginHubWindow.class, "pluginhub_missingicon.png"));
		CONFIGURE_ICON = new ImageIcon(ImageUtil.loadImageResource(PluginHubWindow.class, "pluginhub_configure.png"));
		ALERT_ICON = new ImageIcon(ImageUtil.loadImageResource(PluginHubWindow.class, "mdi_alert.png"));
	}

	private enum Sort
	{
		POPULAR("Popular"),
		UPDATED("Recently updated"),
		NEWEST("Newest"),
		NAME("A–Z");

		private final String label;

		Sort(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return "Sort: " + label;
		}
	}

	private final ExternalPluginManager externalPluginManager;
	private final PluginManager pluginManager;
	private final ExternalPluginClient externalPluginClient;
	private final ScheduledExecutorService executor;
	private final ConfigManager configManager;
	private final TopLevelConfigPanel topLevelConfigPanel;

	private final CardLayout rootCards = new CardLayout();
	private final JPanel root = new JPanel(rootCards);
	private final CardLayout browseCards = new CardLayout();
	private final JPanel browseCenter = new JPanel(browseCards);
	private final JPanel gridPanel = new JPanel();
	private final JPanel detailContainer = new JPanel(new BorderLayout());

	private final IconTextField searchBar = new IconTextField();
	private final JComboBox<Sort> sortBox = new JComboBox<>(Sort.values());
	private final JButton updateAllButton = new JButton();
	private final JPanel chipBar;
	private final ButtonGroup chipGroup = new ButtonGroup();
	private final Chip allChip;
	private final Chip installedChip;
	private final Chip updatesChip;
	private final EmptyState failedState = new EmptyState();

	private final Deque<PluginIcon> iconLoadQueue = new ArrayDeque<>();

	// filter state; chipPredicate is null for All
	private Predicate<HubPluginEntry> chipPredicate;
	private boolean tagChipsBuilt;

	private boolean loading;
	private PluginHubManifest.ManifestFull lastManifest;
	private List<HubPluginEntry> entries;
	private final Map<HubPluginEntry, PluginHubCard> cards = new LinkedHashMap<>();
	private final Map<String, String> descriptionCache = new HashMap<>();
	private String detailInternalName;

	@Inject
	PluginHubWindow(
		ExternalPluginManager externalPluginManager,
		PluginManager pluginManager,
		ExternalPluginClient externalPluginClient,
		ScheduledExecutorService executor,
		ConfigManager configManager,
		TopLevelConfigPanel topLevelConfigPanel,
		EventBus eventBus)
	{
		super("Plugin Hub");
		this.externalPluginManager = externalPluginManager;
		this.pluginManager = pluginManager;
		this.externalPluginClient = externalPluginClient;
		this.executor = executor;
		this.configManager = configManager;
		this.topLevelConfigPanel = topLevelConfigPanel;

		setIconImages(Arrays.asList(ClientUI.ICON_128, ClientUI.ICON_16));
		setDefaultCloseOperation(HIDE_ON_CLOSE);
		setMinimumSize(MIN_SIZE);

		// ---- header ----
		JLabel title = new JLabel("Plugin Hub");
		title.setFont(FontManager.getTitleFont());
		title.setForeground(Theme.getActive().getTextPrimary());

		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchBar.setPreferredSize(new Dimension(340, 30));
		searchBar.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				executor.execute(PluginHubWindow.this::filter);
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				executor.execute(PluginHubWindow.this::filter);
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				executor.execute(PluginHubWindow.this::filter);
			}
		});

		sortBox.setFocusable(false);
		sortBox.setMaximumSize(new Dimension(180, 30));
		sortBox.addActionListener(ev -> executor.execute(this::filter));

		IconButton refresh = new IconButton(new ImageIcon(ImageUtil.loadImageResource(PluginHubWindow.class, "pluginhub_refresh.png")), "Refresh", this::reload);

		updateAllButton.setVisible(false);
		updateAllButton.setFocusPainted(false);
		updateAllButton.setContentAreaFilled(false);
		updateAllButton.setForeground(Theme.getActive().getAccent());
		updateAllButton.setBorder(new CompoundBorder(
			new LineBorder(Theme.getActive().getAccent()),
			new EmptyBorder(4, Theme.SPACE_12, 4, Theme.SPACE_12)));
		updateAllButton.addActionListener(ev ->
		{
			updateAllButton.setEnabled(false);
			updateAllButton.setText("Updating…");
			externalPluginManager.update();
		});

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
		header.setBorder(new CompoundBorder(
			new EmptyBorder(Theme.SPACE_12, Theme.SPACE_12, Theme.SPACE_8, Theme.SPACE_12),
			new CompoundBorder(
				new MatteBorder(0, 0, 1, 0, Theme.getActive().getBorderSubtle()),
				new EmptyBorder(0, 0, Theme.SPACE_8, 0))));
		header.add(title);
		header.add(Box.createHorizontalStrut(Theme.SPACE_16));
		header.add(searchBar);
		header.add(Box.createHorizontalStrut(Theme.SPACE_8));
		header.add(sortBox);
		header.add(Box.createHorizontalStrut(Theme.SPACE_8));
		header.add(refresh);
		header.add(Box.createHorizontalGlue());
		header.add(updateAllButton);

		// ---- third-party notice (D6: persistent) ----
		JLabel notice = new JLabel("<html>⚠ Plugin Hub plugins are provided by third parties not affiliated with RuneLite. <u>Learn more</u></html>");
		notice.setFont(FontManager.getSmallFont());
		notice.setForeground(Theme.getActive().getTextMuted());
		notice.setBorder(new EmptyBorder(Theme.SPACE_4, Theme.SPACE_12, Theme.SPACE_4, Theme.SPACE_12));
		notice.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		notice.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				LinkBrowser.browse("https://github.com/runelite/runelite/wiki/Plugin-Hub-Review");
			}
		});
		JPanel noticeStrip = new JPanel(new BorderLayout());
		noticeStrip.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		noticeStrip.add(notice);

		// ---- chips ----
		chipBar = new JPanel(new WrapLayout(java.awt.FlowLayout.LEFT, Theme.SPACE_4, Theme.SPACE_4));
		chipBar.setOpaque(false);
		chipBar.setBorder(new EmptyBorder(Theme.SPACE_8, Theme.SPACE_8, Theme.SPACE_4, Theme.SPACE_8));
		allChip = addChip("All", null);
		allChip.setSelected(true);
		installedChip = addChip("Installed", HubPluginEntry::isInstalled);
		updatesChip = addChip("Updates", HubPluginEntry::isUpdateAvailable);

		// ---- browse center: grid / loading / failed ----
		gridPanel.setLayout(new java.awt.GridLayout(0, 3, Theme.SPACE_8, Theme.SPACE_8));
		gridPanel.setOpaque(false);
		gridPanel.setBorder(new EmptyBorder(Theme.SPACE_4, Theme.SPACE_12, Theme.SPACE_12, Theme.SPACE_12));

		// Scrollable view pinned to the viewport width — without this the grid
		// keeps its own (wider) preferred width and, since the horizontal
		// scrollbar is disabled, the rightmost column is clipped out of view
		WidthTrackingView gridAnchor = new WidthTrackingView();
		gridAnchor.add(gridPanel, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(gridAnchor);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getViewport().addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				// same width the grid is now pinned to, so the column count and
				// the available width can't disagree
				int cols = Math.max(2, Math.min(4, e.getComponent().getWidth() / CARD_MIN_WIDTH));
				java.awt.GridLayout gl = (java.awt.GridLayout) gridPanel.getLayout();
				if (gl.getColumns() != cols)
				{
					gl.setColumns(cols);
					gridPanel.revalidate();
				}
			}
		});

		JLabel loadingLabel = new JLabel("Loading…", JLabel.CENTER);
		loadingLabel.setForeground(Theme.getActive().getTextMuted());

		failedState.setContent("Couldn't load the Plugin Hub",
			"Downloading the plugin manifest failed. Check your connection — or the pinned pluginhub version if this is a dev build.");
		JButton retry = new JButton("Retry");
		retry.addActionListener(ev -> reload());
		JPanel failedPanel = new JPanel();
		failedPanel.setLayout(new BoxLayout(failedPanel, BoxLayout.Y_AXIS));
		failedPanel.setOpaque(false);
		failedState.setAlignmentX(CENTER_ALIGNMENT);
		retry.setAlignmentX(CENTER_ALIGNMENT);
		failedPanel.add(Box.createVerticalGlue());
		failedPanel.add(failedState);
		failedPanel.add(Box.createVerticalStrut(Theme.SPACE_8));
		failedPanel.add(retry);
		failedPanel.add(Box.createVerticalGlue());

		browseCenter.add(scroll, STATE_GRID);
		browseCenter.add(loadingLabel, STATE_LOADING);
		browseCenter.add(failedPanel, STATE_FAILED);

		// ---- browse composite ----
		JPanel browseNorth = new JPanel();
		browseNorth.setLayout(new BoxLayout(browseNorth, BoxLayout.Y_AXIS));
		browseNorth.add(header);
		browseNorth.add(noticeStrip);
		browseNorth.add(chipBar);

		JPanel browse = new JPanel(new BorderLayout());
		browse.add(browseNorth, BorderLayout.NORTH);
		browse.add(browseCenter, BorderLayout.CENTER);

		root.add(browse, VIEW_BROWSE);
		root.add(detailContainer, VIEW_DETAIL);
		setContentPane(root);

		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				saveBounds();
				teardown();
			}
		});
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				saveBounds();
			}

			@Override
			public void componentMoved(ComponentEvent e)
			{
				saveBounds();
			}
		});

		eventBus.register(this);
	}

	/**
	 * Opens (or focuses) the window and reloads the plugin list.
	 */
	public void open()
	{
		if (!isVisible())
		{
			if (!restoreBounds())
			{
				setSize(DEFAULT_SIZE);
				setLocationRelativeTo(null);
			}
			setVisible(true);
			showBrowse();
			reload();
		}
		else
		{
			toFront();
			requestFocus();
		}
		searchBar.requestFocusInWindow();
	}

	// ------------------------------------------------------------------ views

	void showBrowse()
	{
		detailInternalName = null;
		detailContainer.removeAll();
		rootCards.show(root, VIEW_BROWSE);
	}

	void showDetail(HubPluginEntry entry)
	{
		detailInternalName = entry.getManifest().getInternalName();
		detailContainer.removeAll();
		detailContainer.add(new PluginHubDetailPanel(this, entry), BorderLayout.CENTER);
		detailContainer.revalidate();
		detailContainer.repaint();
		rootCards.show(root, VIEW_DETAIL);
	}

	// ------------------------------------------------------------------ data

	private void reload()
	{
		if (loading)
		{
			return;
		}
		loading = true;
		browseCards.show(browseCenter, STATE_LOADING);

		executor.submit(() ->
		{
			PluginHubManifest.ManifestFull manifest;
			try
			{
				manifest = externalPluginClient.downloadManifestFull();
			}
			catch (IOException | VerificationException e)
			{
				log.error("unable to download plugin manifest", e);
				SwingUtilities.invokeLater(() ->
				{
					loading = false;
					browseCards.show(browseCenter, STATE_FAILED);
				});
				return;
			}

			Map<String, Integer> pluginCounts = Collections.emptyMap();
			try
			{
				pluginCounts = externalPluginClient.getPluginCounts();
			}
			catch (IOException e)
			{
				log.warn("unable to download plugin counts", e);
			}

			buildEntries(manifest, pluginCounts);
		});
	}

	// off-EDT: join manifest + loaded plugins + counts into entries
	private void buildEntries(PluginHubManifest.ManifestFull manifest, Map<String, Integer> pluginCounts)
	{
		lastManifest = manifest;
		Map<String, PluginHubManifest.DisplayData> display = manifest.getDisplay().stream()
			.collect(ImmutableMap.toImmutableMap(PluginHubManifest.DisplayData::getInternalName, Function.identity()));
		Map<String, PluginHubManifest.JarData> jars = manifest.getJars().stream()
			.collect(ImmutableMap.toImmutableMap(PluginHubManifest.JarData::getInternalName, Function.identity()));

		Multimap<String, Plugin> loadedPlugins = HashMultimap.create();
		for (Plugin p : pluginManager.getPlugins())
		{
			String iname = ExternalPluginManager.getInternalName(p.getClass());
			if (iname != null)
			{
				loadedPlugins.put(iname, p);
			}
		}

		Set<String> installed = new HashSet<>(externalPluginManager.getInstalledExternalPlugins());

		List<HubPluginEntry> built = Sets.union(display.keySet(), loadedPlugins.keySet())
			.stream()
			.map(id -> new HubPluginEntry(display.get(id), jars.get(id), loadedPlugins.get(id),
				pluginCounts.getOrDefault(id, -1), installed.contains(id)))
			.collect(Collectors.toList());

		SwingUtilities.invokeLater(() -> populate(built));
	}

	// EDT: rebuild cards and chrome from a fresh entry list
	private void populate(List<HubPluginEntry> built)
	{
		entries = built;
		loading = false;

		synchronized (iconLoadQueue)
		{
			iconLoadQueue.clear();
		}
		cards.clear();
		for (HubPluginEntry entry : built)
		{
			cards.put(entry, new PluginHubCard(this, entry));
		}

		if (!tagChipsBuilt)
		{
			buildTagChips();
			tagChipsBuilt = true;
		}

		long installedCount = built.stream().filter(HubPluginEntry::isInstalled).count();
		long updateCount = built.stream().filter(HubPluginEntry::isUpdateAvailable).count();
		installedChip.setText("Installed · " + installedCount);
		updatesChip.setText("Updates · " + updateCount);
		updateAllButton.setText("Update all (" + updateCount + ")");
		updateAllButton.setEnabled(true);
		updateAllButton.setVisible(updateCount >= 2);

		browseCards.show(browseCenter, STATE_GRID);
		executor.execute(this::filter);

		// keep an open detail view in sync after install/remove/update
		if (detailInternalName != null)
		{
			HubPluginEntry current = built.stream()
				.filter(e -> e.getManifest().getInternalName().equals(detailInternalName))
				.findFirst()
				.orElse(null);
			if (current != null)
			{
				showDetail(current);
			}
			else
			{
				showBrowse();
			}
		}
	}

	private void buildTagChips()
	{
		Map<String, Integer> tagCounts = new HashMap<>();
		for (HubPluginEntry entry : entries)
		{
			String[] tags = entry.getManifest().getTags();
			if (tags == null)
			{
				continue;
			}
			for (String tag : tags)
			{
				tagCounts.merge(tag.toLowerCase(Locale.ROOT), 1, Integer::sum);
			}
		}

		tagCounts.entrySet().stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
			.limit(MAX_TAG_CHIPS)
			.forEach(e -> addChip(e.getKey(), entry -> entry.hasTag(e.getKey())));
		chipBar.revalidate();
	}

	private Chip addChip(String label, Predicate<HubPluginEntry> predicate)
	{
		Chip chip = new Chip(label);
		chip.addActionListener(ev ->
		{
			if (chipPredicate == predicate && predicate != null)
			{
				// clicking the active chip again clears back to All
				chipPredicate = null;
				chipGroup.setSelected(allChip.getModel(), true);
			}
			else
			{
				chipPredicate = predicate;
			}
			executor.execute(this::filter);
		});
		chipGroup.add(chip);
		chipBar.add(chip);
		return chip;
	}

	// off-EDT: compute the visible, ordered entry list then swap the grid
	private void filter()
	{
		List<HubPluginEntry> current = entries;
		if (loading || current == null)
		{
			return;
		}

		String query = searchBar.getText();
		boolean searching = query != null && !query.trim().isEmpty();

		List<HubPluginEntry> shown;
		if (searching)
		{
			shown = PluginSearch.search(current, query);
		}
		else
		{
			shown = current.stream()
				.filter(e -> e.isInstalled() || e.getJarData() != null)
				.sorted(comparator((Sort) sortBox.getSelectedItem()))
				.collect(Collectors.toList());
		}

		Predicate<HubPluginEntry> chipP = chipPredicate;
		if (chipP != null)
		{
			shown = shown.stream().filter(chipP).collect(Collectors.toList());
		}

		List<HubPluginEntry> finalShown = shown;
		SwingUtilities.invokeLater(() ->
		{
			gridPanel.removeAll();
			for (HubPluginEntry entry : finalShown)
			{
				PluginHubCard card = cards.get(entry);
				if (card != null)
				{
					gridPanel.add(card);
				}
			}
			gridPanel.revalidate();
			gridPanel.repaint();
		});
	}

	private static Comparator<HubPluginEntry> comparator(Sort sort)
	{
		// unavailable always sinks, regardless of sort
		Comparator<HubPluginEntry> available = Comparator.comparing(HubPluginEntry::isUnavailable);
		switch (sort == null ? Sort.POPULAR : sort)
		{
			case UPDATED:
				return available.thenComparing(
					Comparator.comparingLong((HubPluginEntry e) -> e.getManifest().getLastUpdatedAt()).reversed());
			case NEWEST:
				return available.thenComparing(
					Comparator.comparingLong((HubPluginEntry e) -> e.getManifest().getCreatedAt()).reversed());
			case NAME:
				return available.thenComparing(e -> e.getManifest().getDisplayName(), String.CASE_INSENSITIVE_ORDER);
			case POPULAR:
			default:
				return available
					.thenComparing(HubPluginEntry::isInstalled, Comparator.reverseOrder())
					.thenComparing(Comparator.comparingInt(HubPluginEntry::getUserCount).reversed())
					.thenComparing(e -> e.getManifest().getDisplayName(), String.CASE_INSENSITIVE_ORDER);
		}
	}

	@Subscribe
	private void onExternalPluginsChanged(ExternalPluginsChanged ev)
	{
		if (!isVisible() || lastManifest == null || loading)
		{
			return;
		}

		Map<String, Integer> pluginCounts = entries == null ? Collections.emptyMap()
			: entries.stream().collect(Collectors.toMap(e -> e.getManifest().getInternalName(), HubPluginEntry::getUserCount, (a, b) -> a));
		PluginHubManifest.ManifestFull manifest = lastManifest;
		executor.submit(() -> buildEntries(manifest, pluginCounts));
	}

	// ------------------------------------------------------------------ actions shared by card + detail

	JButton makeActionButton(HubPluginEntry entry)
	{
		Theme theme = Theme.getActive();
		JButton button = new JButton();
		button.setFont(FontManager.getSmallFont());
		button.setFocusPainted(false);
		button.setContentAreaFilled(false);

		switch (entry.getAction())
		{
			case INSTALL:
				styleActionButton(button, "Install", theme.getSuccess());
				button.addActionListener(l ->
				{
					if (entry.getManifest().getWarning() != null)
					{
						int result = JOptionPane.showConfirmDialog(
							this,
							"<html><p>" + entry.getManifest().getWarning() + "</p><strong>Are you sure you want to install this plugin?</strong></html>",
							"Installing " + entry.getManifest().getDisplayName(),
							JOptionPane.YES_NO_OPTION,
							JOptionPane.WARNING_MESSAGE);
						if (result != JOptionPane.OK_OPTION)
						{
							return;
						}
					}
					styleActionButton(button, "Installing…", theme.getTextDisabled());
					button.setEnabled(false);
					externalPluginManager.install(entry.getManifest().getInternalName());
				});
				break;
			case UPDATE:
				styleActionButton(button, "Update", theme.getAccent());
				button.addActionListener(l ->
				{
					styleActionButton(button, "Updating…", theme.getTextDisabled());
					button.setEnabled(false);
					externalPluginManager.update();
				});
				break;
			case REMOVE:
				styleActionButton(button, "Remove", theme.getError());
				button.addActionListener(l ->
				{
					styleActionButton(button, "Removing…", theme.getTextDisabled());
					button.setEnabled(false);
					externalPluginManager.remove(entry.getManifest().getInternalName());
				});
				break;
			case UNAVAILABLE:
			default:
				styleActionButton(button, "Unavailable", theme.getTextDisabled());
				button.setEnabled(false);
				break;
		}
		return button;
	}

	JButton makeRemoveButton(HubPluginEntry entry)
	{
		Theme theme = Theme.getActive();
		JButton button = new JButton();
		button.setFont(FontManager.getSmallFont());
		button.setFocusPainted(false);
		button.setContentAreaFilled(false);
		styleActionButton(button, "Remove", theme.getError());
		button.addActionListener(l ->
		{
			styleActionButton(button, "Removing…", theme.getTextDisabled());
			button.setEnabled(false);
			externalPluginManager.remove(entry.getManifest().getInternalName());
		});
		return button;
	}

	private static void styleActionButton(JButton button, String text, java.awt.Color color)
	{
		button.setText(text);
		button.setForeground(color);
		button.setBorder(new CompoundBorder(
			new LineBorder(color),
			new EmptyBorder(2, Theme.SPACE_8, 2, Theme.SPACE_8)));
	}

	void openConfig(HubPluginEntry entry)
	{
		if (entry.getLoadedPlugins().isEmpty())
		{
			return;
		}

		if (entry.getLoadedPlugins().size() > 1)
		{
			topLevelConfigPanel.openWithFilter(entry.getManifest().getInternalName());
			return;
		}

		Plugin plugin = entry.getLoadedPlugins().iterator().next();
		Config cfg = pluginManager.getPluginConfigProxy(plugin);
		if (cfg == null)
		{
			topLevelConfigPanel.openWithFilter(entry.getManifest().getInternalName());
		}
		else
		{
			topLevelConfigPanel.openConfigurationPanel(plugin);
		}
	}

	void openWebsite(HubPluginEntry entry)
	{
		LinkBrowser.browse("https://runelite.net/plugin-hub/show/" + entry.getManifest().getInternalName());
	}

	/**
	 * Loads a plugin's full description (its source repo README) off-thread and
	 * calls back on the EDT with rendered HTML, or an empty string when none is
	 * available. Cached per session so reopening a plugin is instant.
	 */
	void loadFullDescription(String internalName, Consumer<String> onHtml)
	{
		String cached = descriptionCache.get(internalName);
		if (cached != null)
		{
			onHtml.accept(cached);
			return;
		}

		executor.submit(() ->
		{
			String html = null;
			try
			{
				html = PluginHubMarkdown.toHtml(externalPluginClient.downloadDescription(internalName));
			}
			catch (IOException e)
			{
				log.debug("failed to load full description for {}", internalName, e);
			}

			String result = html == null ? "" : html;
			SwingUtilities.invokeLater(() ->
			{
				descriptionCache.put(internalName, result);
				onHtml.accept(result);
			});
		});
	}

	// ------------------------------------------------------------------ icons

	/**
	 * Lazy plugin icon: queues its download the first time it paints, same
	 * scheme as the old panel so offscreen cards never fetch.
	 */
	class PluginIcon extends JLabel
	{
		private final PluginHubManifest.DisplayData manifest;
		private boolean loadingStarted;
		private boolean loaded;

		PluginIcon(PluginHubManifest.DisplayData manifest)
		{
			setIcon(MISSING_ICON);
			this.manifest = manifest.hasIcon() ? manifest : null;
			this.loaded = !manifest.hasIcon();
		}

		@Override
		public void paint(Graphics g)
		{
			super.paint(g);

			if (!loaded && !loadingStarted)
			{
				loadingStarted = true;
				synchronized (iconLoadQueue)
				{
					iconLoadQueue.add(this);
					if (iconLoadQueue.size() == 1)
					{
						executor.submit(PluginHubWindow.this::pumpIconQueue);
					}
				}
			}
		}

		private void load()
		{
			try
			{
				BufferedImage img = externalPluginClient.downloadIcon(manifest);
				loaded = true;
				SwingUtilities.invokeLater(() -> setIcon(new ImageIcon(img)));
			}
			catch (IOException e)
			{
				log.info("Cannot download icon for plugin \"{}\"", manifest.getInternalName(), e);
			}
		}
	}

	private void pumpIconQueue()
	{
		PluginIcon pi;
		synchronized (iconLoadQueue)
		{
			pi = iconLoadQueue.poll();
		}

		if (pi == null)
		{
			return;
		}

		pi.load();

		synchronized (iconLoadQueue)
		{
			if (iconLoadQueue.isEmpty())
			{
				return;
			}
		}

		executor.submit(this::pumpIconQueue);
	}

	// ------------------------------------------------------------------ lifecycle

	private void teardown()
	{
		gridPanel.removeAll();
		detailContainer.removeAll();
		cards.clear();
		entries = null;
		lastManifest = null;
		detailInternalName = null;
		searchBar.setText("");
		// clear loading so a reopen refetches even if a load was in flight when
		// the window was closed; the orphaned task will repopulate a hidden
		// window harmlessly and the next open() reloads fresh
		loading = false;

		synchronized (iconLoadQueue)
		{
			iconLoadQueue.clear();
		}
	}

	private void saveBounds()
	{
		if (!isVisible())
		{
			return;
		}
		Rectangle b = getBounds();
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_BOUNDS, b.x + ":" + b.y + ":" + b.width + ":" + b.height);
	}

	private boolean restoreBounds()
	{
		String str = configManager.getConfiguration(CONFIG_GROUP, CONFIG_BOUNDS);
		if (str == null)
		{
			return false;
		}
		try
		{
			String[] parts = str.split(":");
			int x = Integer.parseInt(parts[0]);
			int y = Integer.parseInt(parts[1]);
			int w = Math.max(MIN_SIZE.width, Integer.parseInt(parts[2]));
			int h = Math.max(MIN_SIZE.height, Integer.parseInt(parts[3]));
			setBounds(x, y, w, h);
			return true;
		}
		catch (NumberFormatException | ArrayIndexOutOfBoundsException e)
		{
			return false;
		}
	}

	/**
	 * A BorderLayout scroll view that tracks the viewport width so its content
	 * is exactly as wide as the visible area (never wider) and wraps/reflows
	 * into it; height stays content-driven so it scrolls vertically. Used for
	 * the browse grid (keeps the rightmost column from being clipped when the
	 * horizontal scrollbar is off) and the detail page (lets the description
	 * reflow to the window width).
	 */
	static class WidthTrackingView extends JPanel implements Scrollable
	{
		WidthTrackingView()
		{
			super(new BorderLayout());
			setOpaque(false);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return visibleRect.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	/**
	 * FlowLayout whose preferred height accounts for wrapping, so the chip bar
	 * can break into multiple rows inside a BoxLayout.
	 */
	private static class WrapLayout extends java.awt.FlowLayout
	{
		WrapLayout(int align, int hgap, int vgap)
		{
			super(align, hgap, vgap);
		}

		@Override
		public Dimension preferredLayoutSize(java.awt.Container target)
		{
			synchronized (target.getTreeLock())
			{
				int targetWidth = target.getWidth();
				if (targetWidth == 0)
				{
					targetWidth = Integer.MAX_VALUE;
				}

				java.awt.Insets insets = target.getInsets();
				int maxWidth = targetWidth - (insets.left + insets.right + getHgap() * 2);

				int width = 0;
				int height = insets.top + insets.bottom + getVgap();
				int rowWidth = 0;
				int rowHeight = 0;

				for (int i = 0; i < target.getComponentCount(); i++)
				{
					java.awt.Component c = target.getComponent(i);
					if (!c.isVisible())
					{
						continue;
					}
					Dimension d = c.getPreferredSize();
					if (rowWidth + d.width > maxWidth && rowWidth > 0)
					{
						width = Math.max(width, rowWidth);
						height += rowHeight + getVgap();
						rowWidth = 0;
						rowHeight = 0;
					}
					rowWidth += d.width + getHgap();
					rowHeight = Math.max(rowHeight, d.height);
				}
				width = Math.max(width, rowWidth);
				height += rowHeight + getVgap();
				return new Dimension(width + insets.left + insets.right + getHgap() * 2, height);
			}
		}
	}
}
