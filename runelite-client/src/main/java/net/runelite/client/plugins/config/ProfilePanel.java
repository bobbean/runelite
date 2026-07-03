/*
 * Copyright (c) 2023 Adam <Adam@sigterm.info>
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

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.AbstractAction;
import javax.swing.GroupLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.account.SessionManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.config.ProfileManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.events.SessionClose;
import net.runelite.client.events.SessionOpen;
import net.runelite.client.plugins.screenmarkers.ScreenMarkerPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.DragAndDropReorderPane;
import net.runelite.client.ui.components.MouseDragEventForwarder;
import net.runelite.client.ui.theme.Theme;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;
import net.runelite.client.util.Text;

@Slf4j
class ProfilePanel extends PluginPanel
{
	private static final int MAX_PROFILES = 64;

	private static final ImageIcon ADD_ICON = new ImageIcon(ImageUtil.loadImageResource(ScreenMarkerPlugin.class, "add_icon.png"));
	private static final ImageIcon LINK_ACTIVE_ICON;
	private static final ImageIcon SYNC_ACTIVE_ICON;
	private static final ImageIcon MENU_ICON;

	private final ConfigManager configManager;
	private final ProfileManager profileManager;
	private final SessionManager sessionManager;
	private final ScheduledExecutorService executor;

	private final DragAndDropReorderPane profilesList;
	private final JButton addButton;
	private final JButton importButton;

	private Map<Long, ProfileCard> cards = new HashMap<>();

	private File lastFileChooserDirectory = RuneLite.RUNELITE_DIR;

	private boolean active;

	static
	{
		BufferedImage link = ImageUtil.loadImageResource(ProfilePanel.class, "/util/link.png");
		LINK_ACTIVE_ICON = new ImageIcon(ImageUtil.recolorImage(link, ColorScheme.BRAND_ORANGE));

		BufferedImage sync = ImageUtil.loadImageResource(ProfilePanel.class, "cloud_sync.png");
		SYNC_ACTIVE_ICON = new ImageIcon(ImageUtil.recolorImage(sync, ColorScheme.BRAND_ORANGE));

		// kebab: three dots, drawn (no such asset exists and the RS font has
		// no vertical-ellipsis glyph)
		BufferedImage menu = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = menu.createGraphics();
		g.setColor(Theme.getActive().getTextMuted());
		for (int i = 0; i < 3; i++)
		{
			g.fillRect(7, 3 + i * 4, 2, 2);
		}
		g.dispose();
		MENU_ICON = new ImageIcon(menu);
	}

	@Inject
	ProfilePanel(
		ConfigManager configManager,
		ProfileManager profileManager,
		SessionManager sessionManager,
		ScheduledExecutorService executor
	)
	{
		this.profileManager = profileManager;
		this.configManager = configManager;
		this.sessionManager = sessionManager;
		this.executor = executor;

		setBorder(new EmptyBorder(10, 10, 10, 10));

		GroupLayout layout = new GroupLayout(this);
		setLayout(layout);

		profilesList = new DragAndDropReorderPane();
		profilesList.addDragListener(this::handleDrag);

		addButton = new JButton("New Profile", ADD_ICON);
		addButton.addActionListener(ev -> createProfile());

		importButton = new JButton("Import Profile");
		importButton.addActionListener(ev ->
		{
			JFileChooser fileChooser = new JFileChooser();
			fileChooser.setDialogTitle("Profile import");
			fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("RuneLite properties", "properties"));
			fileChooser.setAcceptAllFileFilterUsed(false);
			fileChooser.setCurrentDirectory(lastFileChooserDirectory);
			int selection = fileChooser.showOpenDialog(this);
			if (selection == JFileChooser.APPROVE_OPTION)
			{
				File file = fileChooser.getSelectedFile();
				lastFileChooserDirectory = file.getParentFile();
				importProfile(file);
			}
		});

		JLabel info = new JLabel("<html>"
			+ "Profiles are separate sets of plugins and settings that you can switch between at any time.");

		layout.setVerticalGroup(layout.createSequentialGroup()
			.addComponent(profilesList)
			.addGap(8)
			.addGroup(layout.createParallelGroup()
				.addComponent(addButton)
				.addComponent(importButton))
			.addGap(8)
			.addComponent(info));

		layout.setHorizontalGroup(layout.createParallelGroup()
			.addComponent(profilesList)
			.addGroup(layout.createSequentialGroup()
				.addComponent(addButton)
				.addGap(8)
				.addComponent(importButton))
			.addComponent(info));

		{
			Object refresh = "this could just be a lambda, but no, it has to be abstracted";
			getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), refresh);
			getActionMap().put(refresh, new AbstractAction()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					reload();
				}
			});
		}
	}

	@Override
	public void onActivate()
	{
		active = true;
		reload();
	}

	@Override
	public void onDeactivate()
	{
		active = false;
		SwingUtil.fastRemoveAll(profilesList);
		cards.clear();
	}

	@Subscribe
	private void onProfileChanged(ProfileChanged ev)
	{
		if (!active)
		{
			return;
		}

		SwingUtilities.invokeLater(() ->
		{
			for (ProfileCard card : cards.values())
			{
				card.setActive(false);
			}

			ProfileCard card = cards.get(configManager.getProfile().getId());
			if (card == null)
			{
				reload();
				return;
			}

			card.setActive(true);
		});
	}

	@Subscribe
	private void onRuneScapeProfileChanged(RuneScapeProfileChanged ev)
	{
		if (!active)
		{
			return;
		}

		reload();
	}

	@Subscribe
	public void onSessionOpen(SessionOpen sessionOpen)
	{
		if (!active)
		{
			return;
		}

		reload();
	}

	@Subscribe
	public void onSessionClose(SessionClose sessionClose)
	{
		if (!active)
		{
			return;
		}

		reload();
	}

	private void reload()
	{
		executor.submit(() ->
		{
			try (ProfileManager.Lock lock = profileManager.lock())
			{
				reload(lock.getProfiles());
			}
		});
	}

	private void reload(List<ConfigProfile> profiles)
	{
		SwingUtilities.invokeLater(() ->
		{
			SwingUtil.fastRemoveAll(profilesList);

			cards = new HashMap<>();

			long activePanel = configManager.getProfile().getId();
			final String rsProfileKey = configManager.getRSProfileKey();
			boolean limited = profiles.stream().filter(v -> !v.isInternal()).count() >= MAX_PROFILES;

			for (ConfigProfile profile : profiles)
			{
				if (profile.isInternal())
				{
					continue;
				}

				final long id = profile.getId();
				final List<String> defaultForRsProfiles = profile.getDefaultForRsProfiles();
				ProfileCard pc = new ProfileCard(
					profile,
					activePanel == id,
					defaultForRsProfiles != null && defaultForRsProfiles.contains(rsProfileKey),
					limited);
				cards.put(profile.getId(), pc);
				profilesList.add(pc);
			}

			addButton.setEnabled(!limited);
			importButton.setEnabled(!limited);

			profilesList.revalidate();
		});
	}

	private class ProfileCard extends JPanel
	{
		private static final int LEFT_BORDER_WIDTH = 3;
		private static final int LEFT_GAP = 6;

		private final ConfigProfile profile;
		private final JTextField name;

		private boolean active;

		private ProfileCard(ConfigProfile profile, boolean isActive, boolean rsProfileDefault, boolean limited)
		{
			this.profile = profile;

			setBackground(ColorScheme.DARKER_GRAY_COLOR);

			name = new JTextField();
			name.setText(profile.getName());
			name.setEditable(false);
			name.setEnabled(false);
			name.setOpaque(false);
			name.setBorder(null);
			name.addActionListener(ev -> stopRenaming(true));
			name.addKeyListener(new KeyAdapter()
			{
				@Override
				public void keyPressed(KeyEvent e)
				{
					if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
					{
						stopRenaming(false);
					}
				}
			});
			((AbstractDocument) name.getDocument()).setDocumentFilter(new DocumentFilter()
			{
				@Override
				public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException
				{
					super.insertString(fb, offset, filter(string), attr);
				}

				@Override
				public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException
				{
					super.replace(fb, offset, length, filter(text), attrs);
				}

				private String filter(String in)
				{
					// characters commonly forbidden in file names
					return CharMatcher.noneOf("/\\<>:\"|?*\r\n\0$")
						.retainFrom(in);
				}
			});

			// badges, shown only when applicable
			JLabel syncBadge = new JLabel(SYNC_ACTIVE_ICON);
			syncBadge.setToolTipText("Cloud sync enabled");
			syncBadge.setVisible(profile.isSync());

			JLabel defaultBadge = new JLabel(LINK_ACTIVE_ICON);
			defaultBadge.setToolTipText(defaultForRsProfilesTooltip(profile));
			defaultBadge.setVisible(rsProfileDefault);

			// selection is primary (click the card); everything else lives in
			// the kebab menu (see docs/ui-rework/design/NAVIGATION.md §5)
			JButton menuButton = new JButton(MENU_ICON);
			menuButton.setToolTipText("Profile actions");
			SwingUtil.removeButtonDecorations(menuButton);
			menuButton.setPreferredSize(new Dimension(22, 0));
			menuButton.addActionListener(ev -> buildActionsMenu(rsProfileDefault, limited).show(menuButton, 0, menuButton.getHeight()));

			{
				GroupLayout layout = new GroupLayout(this);
				this.setLayout(layout);

				layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
					.addComponent(name, 28, 28, 28)
					.addComponent(syncBadge)
					.addComponent(defaultBadge)
					.addComponent(menuButton, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));

				layout.setHorizontalGroup(layout.createSequentialGroup()
					.addGap(LEFT_GAP)
					.addComponent(name)
					.addComponent(syncBadge)
					.addGap(2)
					.addComponent(defaultBadge)
					.addGap(2)
					.addComponent(menuButton));
			}

			// single click switches; double click on the active card renames
			MouseAdapter clickListener = new MouseDragEventForwarder(profilesList)
			{
				@Override
				public void mouseClicked(MouseEvent ev)
				{
					if (!interactive(ev))
					{
						if (ev.getClickCount() == 2 && active)
						{
							startRenaming();
						}
						else if (ev.getClickCount() == 1 && !active)
						{
							switchToProfile(profile.getId());
						}
					}
				}

				@Override
				public void mouseEntered(MouseEvent ev)
				{
					if (!active)
					{
						setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
					}
				}

				@Override
				public void mouseExited(MouseEvent ev)
				{
					setBackground(active ? Theme.getActive().getSurfaceRaised() : ColorScheme.DARKER_GRAY_COLOR);
				}

				private boolean interactive(MouseEvent ev)
				{
					Component target = ev.getComponent();
					if (target instanceof JTextField)
					{
						return ((JTextField) target).isEditable();
					}
					return target instanceof JButton;
				}
			};
			addMouseListener(clickListener);
			addMouseMotionListener(clickListener);
			name.addMouseListener(clickListener);
			name.addMouseMotionListener(clickListener);
			syncBadge.addMouseListener(clickListener);
			syncBadge.addMouseMotionListener(clickListener);
			defaultBadge.addMouseListener(clickListener);
			defaultBadge.addMouseMotionListener(clickListener);
			// also on the kebab so the card's hover highlight holds over it
			// (interactive() keeps its clicks from switching profiles)
			menuButton.addMouseListener(clickListener);
			menuButton.addMouseMotionListener(clickListener);

			setActive(isActive);
		}

		private JPopupMenu buildActionsMenu(boolean rsProfileDefault, boolean limited)
		{
			JPopupMenu menu = new JPopupMenu();

			JMenuItem rename = new JMenuItem("Rename");
			rename.addActionListener(ev -> startRenaming());
			menu.add(rename);

			JMenuItem duplicate = new JMenuItem("Duplicate");
			duplicate.setEnabled(!limited);
			duplicate.addActionListener(ev -> cloneProfile(profile));
			menu.add(duplicate);

			JMenuItem export = new JMenuItem("Export...");
			export.addActionListener(ev -> exportProfileWithChooser());
			menu.add(export);

			if (configManager.getRSProfileKey() != null)
			{
				JMenuItem defaultForRsProfile = new JMenuItem(rsProfileDefault
					? "Unset default for this account"
					: "Set default for this account");
				defaultForRsProfile.addActionListener(ev ->
				{
					if (rsProfileDefault)
					{
						unsetRsProfileDefaultProfile();
					}
					else
					{
						setRsProfileDefaultProfile(profile.getId());
					}
				});
				menu.add(defaultForRsProfile);
			}

			if (sessionManager.getAccountSession() != null)
			{
				JMenuItem sync = new JMenuItem(profile.isSync() ? "Disable cloud sync" : "Enable cloud sync");
				sync.addActionListener(ev -> toggleSync(profile, !profile.isSync()));
				menu.add(sync);
			}

			menu.addSeparator();

			JMenuItem delete = new JMenuItem("Delete...");
			delete.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
			delete.setEnabled(!active);
			delete.setToolTipText(active ? "The active profile cannot be deleted" : null);
			delete.addActionListener(ev ->
			{
				int confirm = JOptionPane.showConfirmDialog(ProfileCard.this,
					"Are you sure you want to delete this profile?",
					"Warning", JOptionPane.OK_CANCEL_OPTION);
				if (confirm == 0)
				{
					deleteProfile(profile);
				}
			});
			menu.add(delete);

			return menu;
		}

		private void exportProfileWithChooser()
		{
			JFileChooser fileChooser = new JFileChooser();
			fileChooser.setDialogTitle("Profile export");
			fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("RuneLite properties", "properties"));
			fileChooser.setAcceptAllFileFilterUsed(false);
			fileChooser.setCurrentDirectory(lastFileChooserDirectory);
			fileChooser.setSelectedFile(new File(fileChooser.getCurrentDirectory(), profile.getName() + ".properties"));
			int selection = fileChooser.showSaveDialog(this);
			if (selection == JFileChooser.APPROVE_OPTION)
			{
				File file = fileChooser.getSelectedFile();
				lastFileChooserDirectory = file.getParentFile();
				// add properties file extension
				if (!file.getName().endsWith(".properties"))
				{
					file = new File(file.getParentFile(), file.getName() + ".properties");
				}
				exportProfile(profile, file);
			}
		}

		void setActive(boolean active)
		{
			this.active = active;
			setBorder(new MatteBorder(0, LEFT_BORDER_WIDTH, 0, 0, active
				? ColorScheme.BRAND_ORANGE
				: ColorScheme.DARKER_GRAY_COLOR));
			setBackground(active ? Theme.getActive().getSurfaceRaised() : ColorScheme.DARKER_GRAY_COLOR);
		}

		private void startRenaming()
		{
			name.setEnabled(true);
			name.setEditable(true);
			name.setOpaque(true);
			name.requestFocusInWindow();
			name.selectAll();
		}

		private void stopRenaming(boolean save)
		{
			name.setEditable(false);
			name.setEnabled(false);
			name.setOpaque(false);

			if (save)
			{
				renameProfile(profile.getId(), name.getText().trim());
			}
			else
			{
				name.setText(profile.getName());
			}
		}
	}

	private String defaultForRsProfilesTooltip(ConfigProfile profile)
	{
		final List<String> defaultForRsProfiles = profile.getDefaultForRsProfiles();
		final StringBuilder tooltip = new StringBuilder("<html>");
		if (defaultForRsProfiles == null || defaultForRsProfiles.isEmpty())
		{
			tooltip.append("Default for the current RuneScape account");
		}
		else
		{
			tooltip.append("This profile is the default for the following RuneScape accounts:");
			for (final String rsProfileKey : defaultForRsProfiles)
			{
				final String ign = configManager.getConfiguration(ConfigManager.RSPROFILE_GROUP, rsProfileKey, ConfigManager.RSPROFILE_DISPLAY_NAME);
				if (Strings.isNullOrEmpty(ign))
				{
					continue;
				}

				final RuneScapeProfileType worldType = configManager.getConfiguration(ConfigManager.RSPROFILE_GROUP, rsProfileKey, ConfigManager.RSPROFILE_TYPE, RuneScapeProfileType.class);

				tooltip.append("<br>");
				tooltip.append(ign);
				if (worldType != RuneScapeProfileType.STANDARD)
				{
					tooltip
						.append(" (")
						.append(Text.titleCase(worldType))
						.append(')');
				}
			}
		}
		tooltip.append("</html>");
		return tooltip.toString();
	}

	private void createProfile()
	{
		try (ProfileManager.Lock lock = profileManager.lock())
		{
			String name = "New Profile";
			int number = 1;
			while (lock.findProfile(name) != null)
			{
				name = "New Profile (" + (number++) + ")";
			}

			log.info("Creating new profile: {}", name);
			lock.createProfile(name);

			reload(lock.getProfiles());
		}
	}

	private void deleteProfile(ConfigProfile profile)
	{
		log.info("Deleting profile {}", profile.getName());

		// disabling sync causes the profile to be deleted
		configManager.toggleSync(profile, false);

		try (ProfileManager.Lock lock = profileManager.lock())
		{
			lock.removeProfile(profile.getId());

			reload(lock.getProfiles());
		}
	}

	private void renameProfile(long id, String name)
	{
		try (ProfileManager.Lock lock = profileManager.lock())
		{
			ConfigProfile profile = lock.findProfile(id);
			if (profile == null)
			{
				log.warn("rename for nonexistent profile {}", id);
				// maybe profile was removed by another client, reload the panel
				reload(lock.getProfiles());
				return;
			}

			log.info("Renaming profile {} ({}) to {}", profile, profile.getId(), name);

			lock.renameProfile(profile, name);
			configManager.renameProfile(profile, name);

			reload(lock.getProfiles());
		}
	}

	private void switchToProfile(long id)
	{
		ConfigProfile profile;
		try (ProfileManager.Lock lock = profileManager.lock())
		{
			profile = lock.findProfile(id);
			if (profile == null)
			{
				log.warn("change to nonexistent profile {}", id);
				// maybe profile was removed by another client, reload the panel
				reload(lock.getProfiles());
				return;
			}

			log.debug("Switching profile to {}", profile.getName());

			// change active profile
			lock.getProfiles().forEach(p -> p.setActive(false));
			profile.setActive(true);
			lock.dirty();
		}

		executor.submit(() -> configManager.switchProfile(profile));
	}

	private void unsetRsProfileDefaultProfile()
	{
		setRsProfileDefaultProfile(-1);
	}

	private void setRsProfileDefaultProfile(long id)
	{
		executor.submit(() ->
		{
			boolean switchProfile = false;
			try (ProfileManager.Lock lock = profileManager.lock())
			{
				final String rsProfileKey = configManager.getRSProfileKey();
				if (rsProfileKey == null)
				{
					return;
				}

				for (final ConfigProfile profile : lock.getProfiles())
				{
					final List<String> defaultForRsProfiles = profile.getDefaultForRsProfiles();
					if (defaultForRsProfiles == null)
					{
						continue;
					}
					if (profile.getDefaultForRsProfiles().remove(rsProfileKey))
					{
						lock.dirty();
					}
				}

				if (id == -1)
				{
					log.debug("Unsetting default profile for rsProfile {}", rsProfileKey);
				}
				else
				{
					final ConfigProfile profile = lock.findProfile(id);
					if (profile == null)
					{
						log.warn("setting nonexistent profile {} as default for rsprofile", id);
						// maybe profile was removed by another client, reload the panel
						reload(lock.getProfiles());
						return;
					}

					log.debug("Setting profile {} as default for rsProfile {}", profile.getName(), rsProfileKey);

					if (profile.getDefaultForRsProfiles() == null)
					{
						profile.setDefaultForRsProfiles(new ArrayList<>());
					}
					profile.getDefaultForRsProfiles().add(rsProfileKey);
					switchProfile = !profile.isActive();
					lock.dirty();
				}

				reload(lock.getProfiles());
			}

			if (switchProfile)
			{
				switchToProfile(id);
			}
		});
	}

	private void exportProfile(ConfigProfile profile, File file)
	{
		log.info("Exporting profile {} to {}", profile.getName(), file);

		executor.execute(() ->
		{
			// save config to disk so the export copies the full config
			configManager.sendConfig();

			File source = ProfileManager.profileConfigFile(profile);
			if (!source.exists())
			{
				SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Profile '" + profile.getName() + "' can not be exported because it has no settings."));
				return;
			}

			try
			{
				Files.copy(
					source.toPath(),
					file.toPath(),
					StandardCopyOption.REPLACE_EXISTING
				);
			}
			catch (IOException e)
			{
				log.error("error performing profile export", e);
			}
		});
	}

	private void importProfile(File file)
	{
		log.info("Importing profile from {}", file);

		executor.execute(() ->
		{
			try (ProfileManager.Lock lock = profileManager.lock())
			{
				String name = "Imported Profile";
				int number = 1;
				while (lock.findProfile(name) != null)
				{
					name = "Imported Profile (" + number++ + ")";
				}

				log.debug("selected new profile name: {}", name);
				ConfigProfile profile = lock.createProfile(name);

				reload(lock.getProfiles());

				configManager.importAndMigrate(lock, file, profile);
			}
		});
	}

	private void cloneProfile(ConfigProfile profile)
	{
		executor.execute(() ->
		{
			// save config to disk so the clone copies the full config
			configManager.sendConfig();

			try (ProfileManager.Lock lock = profileManager.lock())
			{
				int num = 1;
				String name;
				do
				{
					name = profile.getName() + " (" + (num++) + ")";
				}
				while (lock.findProfile(name) != null);

				log.info("Cloning profile {} to {}", profile.getName(), name);

				ConfigProfile clonedProfile = lock.createProfile(name);
				reload(lock.getProfiles());

				// copy config if present
				File from = ProfileManager.profileConfigFile(profile);
				File to = ProfileManager.profileConfigFile(clonedProfile);

				if (from.exists())
				{
					try
					{
						Files.copy(
							from.toPath(),
							to.toPath()
						);
					}
					catch (IOException e)
					{
						log.error("error cloning profile", e);
					}
				}
			}
		});
	}

	private void toggleSync(ConfigProfile profile, boolean sync)
	{
		log.info("{} sync for: {}", sync ? "Enabling" : "Disabling", profile.getName());
		configManager.toggleSync(profile, sync);
		reload();
	}

	private void handleDrag(Component component)
	{
		ProfileCard c = (ProfileCard) component;
		int newPosition = profilesList.getPosition(component);
		log.debug("Drag profile {} to position {}", c.profile.getName(), newPosition);

		try (ProfileManager.Lock lock = profileManager.lock())
		{
			// Because the profilesList indexes don't include internal profiles, they can't be used to manipulate the
			// profile list directly. Just re-sort the profiles instead.
			List<ConfigProfile> profiles = lock.getProfiles();
			profiles.sort(Comparator.comparing(p ->
			{
				Component[] components = profilesList.getComponents();
				for (int idx = 0; idx < components.length; ++idx)
				{
					ProfileCard card = (ProfileCard) components[idx];
					if (card.profile.getId() == p.getId())
					{
						return idx;
					}
				}
				return -1;
			}));

			lock.dirty();

			reload(profiles);
		}
	}
}
