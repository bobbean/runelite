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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.theme.Theme;
import net.runelite.client.util.ImageUtil;

/**
 * The grouped icon rail: Configuration on top, then user-pinned panels, then
 * everything else; icons that don't fit (and hidden icons) collapse into a
 * labeled overflow popup. Window controls arrive as a trailing component below
 * a strong divider (see docs/ui-rework/design/NAVIGATION.md §1).
 *
 * Dragging is spatial pin management: drag within the pinned zone to reorder,
 * drag an icon into the pinned zone to pin it, drag a pinned icon below the
 * divider to unpin. The unpinned zone keeps its automatic order.
 */
class SidebarRail extends JPanel
{
	private static final int PAD = 4;
	private static final int GAP = 2;
	private static final int DIVIDER_H = 9;
	private static final int DRAG_THRESHOLD = 5;

	/**
	 * Persistence for an ordered list of entries, keyed by tooltip.
	 */
	interface ListStore
	{
		List<String> load();

		void save(List<String> tooltips);
	}

	private RailButton configButton;
	private final List<RailButton> pinned = new ArrayList<>();
	private final TreeSet<RailButton> others = new TreeSet<>((a, b) -> NavigationButton.COMPARATOR.compare(a.getNavBtn(), b.getNavBtn()));
	private final List<RailButton> hidden = new ArrayList<>();
	private final JButton overflowButton;
	private final List<RailButton> overflowed = new ArrayList<>();
	private Component trailing;

	private final Consumer<NavigationButton> selectHandler;
	private final ListStore pinStore;
	private final ListStore hiddenStore;

	// layout results, consumed by paintComponent and drag handling
	private final List<Integer> dividerYs = new ArrayList<>();
	private int strongDividerY = -1;
	private final List<RailButton> laidOut = new ArrayList<>();

	// drag state
	private RailButton dragButton;
	private boolean dragActive;
	private Point dragStart;
	private int dragInsertIndex = -1;
	private int dragIndicatorY = -1;

	SidebarRail(Consumer<NavigationButton> selectHandler, ListStore pinStore, ListStore hiddenStore)
	{
		this.selectHandler = selectHandler;
		this.pinStore = pinStore;
		this.hiddenStore = hiddenStore;

		setOpaque(true);
		setBackground(Theme.getActive().getSurfaceSunken());
		setLayout(null); // doLayout() below

		// "..." not "⋯" — the RS LAF font has no U+22EF glyph
		overflowButton = new JButton("...");
		overflowButton.setToolTipText("More panels");
		overflowButton.setFocusable(false);
		overflowButton.setBorder(null);
		overflowButton.setBorderPainted(false);
		overflowButton.setContentAreaFilled(false);
		overflowButton.setFocusPainted(false);
		overflowButton.setForeground(Theme.getActive().getTextMuted());
		overflowButton.addActionListener(ev -> showOverflowMenu());
		overflowButton.setVisible(false);
		add(overflowButton);
	}

	void addNavigation(NavigationButton navBtn)
	{
		RailButton btn = new RailButton(navBtn);
		btn.addActionListener(ev ->
		{
			if (btn.consumeSuppressAction())
			{
				return;
			}
			selectHandler.accept(btn.isActive() ? null : navBtn);
		});

		if (isConfigEntry(navBtn))
		{
			configButton = btn;
			// no pin/hide for the config entry; only plugin-provided popup items
			if (navBtn.getPopup() != null)
			{
				btn.setComponentPopupMenu(buildManagedMenu(btn));
			}
		}
		else
		{
			// managed popup (setComponentPopupMenu) so show/dismiss is handled
			// by Swing — manually shown heavyweight popups don't reliably
			// auto-close over the game canvas
			btn.setComponentPopupMenu(buildManagedMenu(btn));
			installDragHandler(btn);

			if (hiddenStore.load().contains(navBtn.getTooltip()))
			{
				hidden.add(btn);
			}
			else if (pinStore.load().contains(navBtn.getTooltip()))
			{
				List<String> order = pinStore.load();
				pinned.add(btn);
				pinned.sort((a, b) -> Integer.compare(
					order.indexOf(a.getNavBtn().getTooltip()),
					order.indexOf(b.getNavBtn().getTooltip())));
			}
			else
			{
				others.add(btn);
			}
		}

		add(btn);
		revalidate();
		repaint();
	}

	void removeNavigation(NavigationButton navBtn)
	{
		RailButton btn = find(navBtn);
		if (btn == null)
		{
			return;
		}

		if (btn == configButton)
		{
			configButton = null;
		}
		pinned.remove(btn);
		others.remove(btn);
		hidden.remove(btn);
		overflowed.remove(btn);
		remove(btn);
		revalidate();
		repaint();
	}

	void setSelected(NavigationButton navBtn)
	{
		for (RailButton btn : allButtons())
		{
			btn.setActive(btn.getNavBtn() == navBtn);
		}
		repaint();
	}

	void setTrailing(Component c)
	{
		if (trailing != null)
		{
			remove(trailing);
		}
		trailing = c;
		if (c != null)
		{
			add(c);
		}
		revalidate();
	}

	private static boolean isConfigEntry(NavigationButton navBtn)
	{
		// the ConfigPlugin registration (priority 0, tooltip "Configuration")
		return navBtn.getPriority() == 0 && "Configuration".equals(navBtn.getTooltip());
	}

	private RailButton find(NavigationButton navBtn)
	{
		for (RailButton btn : allButtons())
		{
			if (btn.getNavBtn() == navBtn)
			{
				return btn;
			}
		}
		return null;
	}

	private List<RailButton> allButtons()
	{
		List<RailButton> all = new ArrayList<>();
		if (configButton != null)
		{
			all.add(configButton);
		}
		all.addAll(pinned);
		all.addAll(others);
		all.addAll(hidden);
		return all;
	}

	// ===== context menu =====

	private JPopupMenu buildManagedMenu(RailButton btn)
	{
		JPopupMenu menu = new JPopupMenu();
		menu.addPopupMenuListener(new PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e)
			{
				menu.removeAll();
				populateContextMenu(menu, btn);
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
			{
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e)
			{
			}
		});
		return menu;
	}

	private void populateContextMenu(JPopupMenu menu, RailButton btn)
	{
		if (btn != configButton)
		{
			boolean isPinned = pinned.contains(btn);
			JMenuItem pinItem = new JMenuItem(isPinned ? "Unpin" : "Pin");
			pinItem.addActionListener(ev -> setPinned(btn, !isPinned));
			menu.add(pinItem);

			JMenuItem hideItem = new JMenuItem("Hide");
			hideItem.addActionListener(ev -> setHidden(btn, true));
			menu.add(hideItem);
		}

		if (btn.getNavBtn().getPopup() != null)
		{
			if (menu.getComponentCount() > 0)
			{
				menu.addSeparator();
			}
			btn.getNavBtn().getPopup().forEach((name, cb) ->
			{
				JMenuItem item = new JMenuItem(name);
				item.addActionListener(ev -> cb.run());
				menu.add(item);
			});
		}
	}

	private void setPinned(RailButton btn, boolean pin)
	{
		if (pin)
		{
			others.remove(btn);
			hidden.remove(btn);
			if (!pinned.contains(btn))
			{
				pinned.add(btn);
			}
		}
		else
		{
			pinned.remove(btn);
			others.add(btn);
		}

		persistPins();
		persistHidden();
		revalidate();
		repaint();
	}

	private void setHidden(RailButton btn, boolean hide)
	{
		if (hide)
		{
			pinned.remove(btn);
			others.remove(btn);
			if (!hidden.contains(btn))
			{
				hidden.add(btn);
			}
		}
		else
		{
			hidden.remove(btn);
			others.add(btn);
		}

		persistPins();
		persistHidden();
		revalidate();
		repaint();
	}

	private void persistPins()
	{
		List<String> names = new ArrayList<>();
		for (RailButton b : pinned)
		{
			names.add(b.getNavBtn().getTooltip());
		}
		pinStore.save(names);
	}

	private void persistHidden()
	{
		List<String> names = new ArrayList<>();
		for (RailButton b : hidden)
		{
			names.add(b.getNavBtn().getTooltip());
		}
		hiddenStore.save(names);
	}

	// ===== overflow =====

	private void showOverflowMenu()
	{
		JPopupMenu menu = new JPopupMenu();
		for (RailButton btn : overflowed)
		{
			NavigationButton navBtn = btn.getNavBtn();
			// icon + name, per review: labels make overflowed panels findable
			JMenuItem item = new JMenuItem(navBtn.getTooltip(), railMenuIcon(navBtn));
			item.addActionListener(ev -> selectHandler.accept(btn.isActive() ? null : navBtn));
			menu.add(item);
		}

		if (!hidden.isEmpty())
		{
			if (!overflowed.isEmpty())
			{
				menu.addSeparator();
			}
			for (RailButton btn : hidden)
			{
				NavigationButton navBtn = btn.getNavBtn();
				JMenu sub = new JMenu(navBtn.getTooltip());
				sub.setIcon(railMenuIcon(navBtn));

				JMenuItem open = new JMenuItem("Open");
				open.addActionListener(ev -> selectHandler.accept(navBtn));
				sub.add(open);

				JMenuItem show = new JMenuItem("Show in sidebar");
				show.addActionListener(ev -> setHidden(btn, false));
				sub.add(show);

				menu.add(sub);
			}
		}

		menu.show(overflowButton, 0, overflowButton.getHeight());
	}

	private static ImageIcon railMenuIcon(NavigationButton navBtn)
	{
		return new ImageIcon(ImageUtil.resizeImage(navBtn.getIcon(), 16, 16));
	}

	// ===== drag to (re)pin =====

	private void installDragHandler(RailButton btn)
	{
		MouseAdapter adapter = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					dragStart = SwingUtilities.convertPoint(btn, e.getPoint(), SidebarRail.this);
				}
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				if (dragStart == null)
				{
					return;
				}

				Point p = SwingUtilities.convertPoint(btn, e.getPoint(), SidebarRail.this);
				if (!dragActive && p.distance(dragStart) >= DRAG_THRESHOLD)
				{
					dragActive = true;
					dragButton = btn;
				}

				if (dragActive)
				{
					updateDragTarget(p.y);
					repaint();
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (dragActive)
				{
					btn.suppressAction();
					applyDrag(dragButton, dragInsertIndex);
				}
				dragStart = null;
				dragActive = false;
				dragButton = null;
				dragInsertIndex = -1;
				dragIndicatorY = -1;
				repaint();
			}
		};
		btn.addMouseListener(adapter);
		btn.addMouseMotionListener(adapter);
	}

	private void updateDragTarget(int y)
	{
		int idx = 0;
		for (RailButton b : laidOut)
		{
			if (y > b.getY() + b.getHeight() / 2)
			{
				idx++;
			}
			else
			{
				break;
			}
		}
		dragInsertIndex = idx;

		if (laidOut.isEmpty())
		{
			dragIndicatorY = -1;
		}
		else if (idx < laidOut.size())
		{
			dragIndicatorY = laidOut.get(idx).getY() - 2;
		}
		else
		{
			RailButton last = laidOut.get(laidOut.size() - 1);
			dragIndicatorY = last.getY() + last.getHeight() + 1;
		}
	}

	private void applyDrag(RailButton btn, int index)
	{
		if (btn == null || index < 0)
		{
			return;
		}

		int boundary = pinned.size();
		boolean wasPinned = pinned.contains(btn);

		if (wasPinned)
		{
			int current = pinned.indexOf(btn);
			if (index <= boundary)
			{
				// reorder within the pinned zone
				int target = Math.min(index, pinned.size());
				if (current < target)
				{
					target--;
				}
				pinned.remove(btn);
				pinned.add(Math.max(0, Math.min(target, pinned.size())), btn);
			}
			else
			{
				// dragged below the divider: unpin
				pinned.remove(btn);
				others.add(btn);
			}
		}
		else if (index <= boundary)
		{
			// dragged into the pinned zone: pin at position
			others.remove(btn);
			pinned.add(Math.max(0, Math.min(index, pinned.size())), btn);
		}
		// unpinned to unpinned zone: automatic order, nothing to do

		persistPins();
		revalidate();
		repaint();
	}

	// ===== layout & paint =====

	@Override
	public Dimension getPreferredSize()
	{
		int visible = (configButton != null ? 1 : 0) + pinned.size() + others.size();
		int h = PAD * 2 + stackHeight(visible);
		if (trailing != null)
		{
			h += DIVIDER_H + trailing.getPreferredSize().height;
		}
		return new Dimension(RailButton.WIDTH, h);
	}

	@Override
	public Dimension getMinimumSize()
	{
		// enough for config + overflow + trailing; the rest overflows gracefully
		int h = PAD * 2 + stackHeight(2) + DIVIDER_H;
		if (trailing != null)
		{
			h += DIVIDER_H + trailing.getPreferredSize().height;
		}
		return new Dimension(RailButton.WIDTH, h);
	}

	private static int stackHeight(int buttons)
	{
		int h = buttons * (RailButton.HEIGHT + GAP);
		if (buttons > 1)
		{
			h += 2 * DIVIDER_H; // group dividers
		}
		return h;
	}

	@Override
	public void doLayout()
	{
		dividerYs.clear();
		strongDividerY = -1;
		overflowed.clear();
		laidOut.clear();

		int w = getWidth();
		int bottom = getHeight() - PAD;

		// hidden buttons take no space
		for (RailButton btn : hidden)
		{
			btn.setVisible(false);
		}

		// trailing (window controls) claims the bottom, below a strong divider
		if (trailing != null)
		{
			Dimension pref = trailing.getPreferredSize();
			trailing.setBounds(0, bottom - pref.height, w, pref.height);
			bottom -= pref.height + DIVIDER_H;
			strongDividerY = bottom + DIVIDER_H / 2;
		}

		int y = PAD;

		if (configButton != null)
		{
			configButton.setBounds(0, y, w, RailButton.HEIGHT);
			y += RailButton.HEIGHT + GAP;
			dividerYs.add(y + DIVIDER_H / 2 - GAP);
			y += DIVIDER_H;
		}

		List<RailButton> rest = new ArrayList<>(pinned);
		int pinnedCount = rest.size();
		rest.addAll(others);

		// how many of the remaining buttons fit, keeping room for the overflow
		// button when not all fit (the overflow button is also needed when
		// anything is hidden)
		int avail = bottom - y;
		int fit = rest.size();
		boolean needOverflowForHidden = !hidden.isEmpty();
		if (stackNeeded(rest.size(), pinnedCount) + (needOverflowForHidden ? RailButton.HEIGHT + GAP : 0) > avail)
		{
			while (fit > 0 && stackNeeded(fit, pinnedCount) + RailButton.HEIGHT + GAP > avail)
			{
				fit--;
			}
		}

		for (int i = 0; i < rest.size(); i++)
		{
			RailButton btn = rest.get(i);
			if (i == pinnedCount && pinnedCount > 0)
			{
				dividerYs.add(y + DIVIDER_H / 2 - GAP);
				y += DIVIDER_H;
			}

			if (i < fit)
			{
				btn.setVisible(true);
				btn.setBounds(0, y, w, RailButton.HEIGHT);
				laidOut.add(btn);
				y += RailButton.HEIGHT + GAP;
			}
			else
			{
				btn.setVisible(false);
				overflowed.add(btn);
			}
		}

		if (!overflowed.isEmpty() || !hidden.isEmpty())
		{
			overflowButton.setVisible(true);
			overflowButton.setBounds(0, y, w, RailButton.HEIGHT);
		}
		else
		{
			overflowButton.setVisible(false);
		}
	}

	private static int stackNeeded(int buttons, int pinnedCount)
	{
		int h = buttons * (RailButton.HEIGHT + GAP);
		if (pinnedCount > 0 && buttons > pinnedCount)
		{
			h += DIVIDER_H;
		}
		return h;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);

		Theme theme = Theme.getActive();
		g.setColor(theme.getBorderSubtle());
		for (int y : dividerYs)
		{
			g.fillRect(6, y, getWidth() - 12, 1);
		}

		if (strongDividerY >= 0)
		{
			g.setColor(theme.getBorder());
			g.fillRect(4, strongDividerY, getWidth() - 8, 2);
		}

		if (dragActive && dragIndicatorY >= 0)
		{
			g.setColor(theme.getAccent());
			g.fillRect(2, dragIndicatorY, getWidth() - 4, 2);
		}
	}
}
