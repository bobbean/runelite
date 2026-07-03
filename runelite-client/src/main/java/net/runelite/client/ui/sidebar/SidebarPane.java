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
package net.runelite.client.ui.sidebar;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JPanel;
import lombok.Getter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.theme.Theme;
import net.runelite.client.util.Text;

/**
 * The whole sidebar: drag-resizable panel host + {@link SidebarRail}.
 *
 * Presents the same contract to {@code ClientUI.Layout} that the old
 * {@code JTabbedPane} did — visibility toggles and an exact preferred width —
 * so the frame layout code is untouched. Selection changes are reported
 * through a listener; {@code ClientUI} keeps owning history/activation.
 */
public class SidebarPane extends JPanel
{
	private static final String CONFIG_GROUP = "runelite";
	private static final String CONFIG_PANEL_WIDTH = "sidebarPanelWidth";
	private static final String CONFIG_PINNED = "sidebarPinnedPanels";
	private static final String CONFIG_HIDDEN = "sidebarHiddenPanels";

	/** Minimum host width: the classic panel width plus its scrollbar gutter. */
	public static final int MIN_PANEL_WIDTH = PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH;
	private static final int MAX_PANEL_WIDTH = 500;
	private static final int GRIP_WIDTH = 5;

	public interface SelectionListener
	{
		void selectionChanged(NavigationButton previous, NavigationButton selected);
	}

	private final ConfigManager configManager;
	private final SidebarRail rail;
	private final JPanel host;
	private final CardLayout hostLayout;
	private final ResizeGrip grip;

	private SelectionListener selectionListener;

	@Getter
	private NavigationButton selected;

	private int panelWidth;

	public SidebarPane(ConfigManager configManager)
	{
		this.configManager = configManager;

		Integer savedWidth = configManager.getConfiguration(CONFIG_GROUP, CONFIG_PANEL_WIDTH, Integer.class);
		panelWidth = clampWidth(savedWidth == null ? MIN_PANEL_WIDTH : savedWidth);

		rail = new SidebarRail(this::select, configListStore(CONFIG_PINNED), configListStore(CONFIG_HIDDEN));

		hostLayout = new CardLayout();
		host = new JPanel(hostLayout);
		host.setBackground(Theme.getActive().getSurface());
		host.add(new JPanel()
		{
			{
				setBackground(Theme.getActive().getSurface());
			}
		}, cardName(null));

		grip = new ResizeGrip();

		setLayout(new BorderLayout());
		add(grip, BorderLayout.WEST);
		add(host, BorderLayout.CENTER);
		add(rail, BorderLayout.EAST);
	}

	public void addNavigation(NavigationButton navBtn)
	{
		rail.addNavigation(navBtn);
		host.add(navBtn.getPanel().getWrappedPanel(), cardName(navBtn));
	}

	public void removeNavigation(NavigationButton navBtn)
	{
		rail.removeNavigation(navBtn);
		host.remove(navBtn.getPanel().getWrappedPanel());
		if (selected == navBtn)
		{
			select(null);
		}
	}

	/**
	 * Select a panel (or null to close). Fires the selection listener only on
	 * actual change, mirroring the old tabbed pane's change events.
	 */
	public void select(NavigationButton navBtn)
	{
		if (selected == navBtn)
		{
			return;
		}

		NavigationButton previous = selected;
		selected = navBtn;

		rail.setSelected(navBtn);
		hostLayout.show(host, cardName(navBtn));
		grip.setVisible(navBtn != null);
		host.setVisible(navBtn != null);

		revalidate();

		if (selectionListener != null)
		{
			selectionListener.selectionChanged(previous, navBtn);
		}
	}

	public void setSelectionListener(SelectionListener listener)
	{
		selectionListener = listener;
	}

	public void setTrailing(Component c)
	{
		rail.setTrailing(c);
	}

	@Override
	public Dimension getPreferredSize()
	{
		int w = rail.getPreferredSize().width;
		if (selected != null)
		{
			w += panelWidth + GRIP_WIDTH;
		}
		return new Dimension(w, super.getPreferredSize().height);
	}

	@Override
	public Dimension getMinimumSize()
	{
		return new Dimension(getPreferredSize().width, rail.getMinimumSize().height);
	}

	private SidebarRail.ListStore configListStore(String key)
	{
		return new SidebarRail.ListStore()
		{
			@Override
			public List<String> load()
			{
				String value = configManager.getConfiguration(CONFIG_GROUP, key);
				return value == null ? List.of() : Text.fromCSV(value);
			}

			@Override
			public void save(List<String> tooltips)
			{
				configManager.setConfiguration(CONFIG_GROUP, key, Text.toCSV(tooltips));
			}
		};
	}

	private static String cardName(NavigationButton navBtn)
	{
		return navBtn == null ? "none" : String.valueOf(System.identityHashCode(navBtn));
	}

	private static int clampWidth(int width)
	{
		return Math.max(MIN_PANEL_WIDTH, Math.min(MAX_PANEL_WIDTH, width));
	}

	/**
	 * Thin drag handle on the panel's outer edge; drag left to widen. Width
	 * persists on release.
	 */
	private class ResizeGrip extends JPanel
	{
		private int dragStartX;
		private int dragStartWidth;
		private boolean hover;

		ResizeGrip()
		{
			setOpaque(true);
			setBackground(Theme.getActive().getSurfaceSunken());
			setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
			setVisible(false);

			MouseAdapter adapter = new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					dragStartX = e.getXOnScreen();
					dragStartWidth = panelWidth;
				}

				@Override
				public void mouseDragged(MouseEvent e)
				{
					int width = clampWidth(dragStartWidth + (dragStartX - e.getXOnScreen()));
					if (width != panelWidth)
					{
						panelWidth = width;
						SidebarPane.this.revalidate();
					}
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					configManager.setConfiguration(CONFIG_GROUP, CONFIG_PANEL_WIDTH, panelWidth);
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					repaint();
				}
			};
			addMouseListener(adapter);
			addMouseMotionListener(adapter);
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(GRIP_WIDTH, 0);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			if (hover)
			{
				g.setColor(Theme.getActive().getSelection());
				g.fillRect(1, 0, GRIP_WIDTH - 2, getHeight());
			}
		}
	}
}
