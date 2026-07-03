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
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.theme.Theme;
import net.runelite.client.util.ImageUtil;

/**
 * The grouped icon rail: Configuration on top, then user-pinned panels, then
 * everything else. When the stack outgrows the rail height it wraps into
 * additional columns (like the old tabbed sidebar); hidden icons (and icons
 * that still don't fit) collapse into a labeled overflow popup. Window
 * controls arrive as a trailing component below a strong divider (see
 * docs/ui-rework/design/NAVIGATION.md §1).
 *
 * Dragging is spatial pin management: drag within a zone to reorder it, drag
 * an icon across the divider to pin or unpin it. Both zone orders persist.
 * Extra columns fill right-to-left, keeping the first column against the
 * window edge.
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
	private final List<RailButton> others = new ArrayList<>();
	private final List<RailButton> hidden = new ArrayList<>();
	private final JButton overflowButton;
	private final List<RailButton> overflowed = new ArrayList<>();
	private Component trailing;

	private final Consumer<NavigationButton> selectHandler;
	private final ListStore pinStore;
	private final ListStore hiddenStore;
	private final ListStore orderStore;

	// layout results, consumed by paintComponent and drag handling
	private final List<Rectangle> dividers = new ArrayList<>();
	private int strongDividerY = -1;
	private final List<RailButton> laidOut = new ArrayList<>();
	// columns the stack needs at the current height; getPreferredSize turns
	// this into width, so the rail widens instead of overflowing
	private int columns = 1;

	// drag state
	private RailButton dragButton;
	private boolean dragActive;
	private Point dragStart;
	private int dragInsertIndex = -1;
	private boolean dragToPinned;
	private int dragBoundary;
	private int dragIndicatorX = -1;
	private int dragIndicatorY = -1;

	SidebarRail(Consumer<NavigationButton> selectHandler, ListStore pinStore, ListStore hiddenStore, ListStore orderStore)
	{
		this.selectHandler = selectHandler;
		this.pinStore = pinStore;
		this.hiddenStore = hiddenStore;
		this.orderStore = orderStore;

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

		installPopupHandler(btn);

		if (isConfigEntry(navBtn))
		{
			configButton = btn;
		}
		else
		{
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
				sortOthers();
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

	private void installPopupHandler(RailButton btn)
	{
		btn.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				maybeShow(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				maybeShow(e);
			}

			private void maybeShow(MouseEvent e)
			{
				if (!e.isPopupTrigger())
				{
					return;
				}

				JPopupMenu menu = new JPopupMenu();
				populateContextMenu(menu, btn);
				if (menu.getComponentCount() > 0)
				{
					menu.show(btn, e.getX(), e.getY());
				}
			}
		});
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
			sortOthers();
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
			sortOthers();
		}

		persistPins();
		persistHidden();
		revalidate();
		repaint();
	}

	/**
	 * Saved order first, then anything unsaved in the automatic order. Until
	 * the user drag-reorders (nothing saved yet) this is the old automatic
	 * COMPARATOR order.
	 */
	private void sortOthers()
	{
		List<String> order = orderStore.load();
		others.sort((a, b) ->
		{
			int ia = order.indexOf(a.getNavBtn().getTooltip());
			int ib = order.indexOf(b.getNavBtn().getTooltip());
			if (ia >= 0 && ib >= 0)
			{
				return Integer.compare(ia, ib);
			}
			if (ia >= 0 || ib >= 0)
			{
				return ia >= 0 ? -1 : 1;
			}
			return NavigationButton.COMPARATOR.compare(a.getNavBtn(), b.getNavBtn());
		});
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

	private void persistOrder()
	{
		List<String> names = new ArrayList<>();
		for (RailButton b : others)
		{
			names.add(b.getNavBtn().getTooltip());
		}
		orderStore.save(names);
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
				// clicking the entry itself opens the panel; the submenu only
				// holds the restore actions
				sub.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseClicked(MouseEvent e)
					{
						if (SwingUtilities.isLeftMouseButton(e))
						{
							MenuSelectionManager.defaultManager().clearSelectedPath();
							selectHandler.accept(btn.isActive() ? null : navBtn);
						}
					}
				});

				JMenuItem show = new JMenuItem("Show in sidebar");
				show.addActionListener(ev -> setHidden(btn, false));
				sub.add(show);

				JMenuItem pin = new JMenuItem("Pin");
				pin.addActionListener(ev -> setPinned(btn, true));
				sub.add(pin);

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
					updateDragTarget(p);
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
				dragIndicatorX = -1;
				dragIndicatorY = -1;
				repaint();
			}
		};
		btn.addMouseListener(adapter);
		btn.addMouseMotionListener(adapter);
	}

	private void updateDragTarget(Point p)
	{
		// laidOut is in column-major visual order with columns filling
		// right-to-left: a button precedes the pointer if it sits in an
		// earlier (further right) column, or above it in the same one
		int x = Math.max(0, Math.min(p.x, getWidth() - 1));
		int idx = 0;
		for (RailButton b : laidOut)
		{
			if (x < b.getX()
				|| (x < b.getX() + RailButton.WIDTH && p.y > b.getY() + b.getHeight() / 2))
			{
				idx++;
			}
			else
			{
				break;
			}
		}
		dragInsertIndex = idx;

		// which zone the drop lands in; the boundary index itself is ambiguous
		// (end of pinned vs. front of others) and resolves geometrically
		int laidPinned = 0;
		while (laidPinned < laidOut.size() && pinned.contains(laidOut.get(laidPinned)))
		{
			laidPinned++;
		}
		dragBoundary = laidPinned;

		if (idx != laidPinned)
		{
			dragToPinned = idx < laidPinned;
		}
		else if (pinned.isEmpty() || laidPinned >= laidOut.size())
		{
			// the top slot pins the first item; a drop past everything with no
			// unpinned icons visible appends to the pins
			dragToPinned = true;
		}
		else
		{
			RailButton firstOther = laidOut.get(laidPinned);
			boolean inOthersColumn = x >= firstOther.getX() && x < firstOther.getX() + RailButton.WIDTH;
			dragToPinned = !inOthersColumn || p.y <= firstOther.getY() - DIVIDER_H / 2;
		}

		if (laidOut.isEmpty())
		{
			dragIndicatorY = -1;
		}
		else if (idx < laidOut.size())
		{
			RailButton next = laidOut.get(idx);
			dragIndicatorX = next.getX();
			dragIndicatorY = next.getY() - 2;
		}
		else
		{
			RailButton last = laidOut.get(laidOut.size() - 1);
			dragIndicatorX = last.getX();
			dragIndicatorY = last.getY() + last.getHeight() + 1;
		}
	}

	private void applyDrag(RailButton btn, int index)
	{
		if (btn == null || index < 0)
		{
			return;
		}

		if (dragToPinned)
		{
			int target = Math.min(index, pinned.size());
			int current = pinned.indexOf(btn);
			if (current >= 0 && current < target)
			{
				target--;
			}
			pinned.remove(btn);
			others.remove(btn);
			pinned.add(Math.max(0, Math.min(target, pinned.size())), btn);
		}
		else
		{
			int target = Math.max(0, index - dragBoundary);
			int current = others.indexOf(btn);
			if (current >= 0 && current < target)
			{
				target--;
			}
			pinned.remove(btn);
			others.remove(btn);
			others.add(Math.max(0, Math.min(target, others.size())), btn);
		}

		persistPins();
		persistOrder();
		revalidate();
		repaint();
	}

	// ===== layout & paint =====

	@Override
	public Dimension getPreferredSize()
	{
		int visible = (configButton != null ? 1 : 0) + pinned.size() + others.size();
		int perColumn = (visible + columns - 1) / Math.max(1, columns);
		int h = PAD * 2 + stackHeight(perColumn);
		if (trailing != null)
		{
			h += DIVIDER_H + trailing.getPreferredSize().height;
		}
		return new Dimension(RailButton.WIDTH * columns, h);
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
		dividers.clear();
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

		List<RailButton> rest = new ArrayList<>(pinned);
		int pinnedCount = rest.size();
		rest.addAll(others);

		// when the single-column stack outgrows the rail, request another
		// column: getPreferredSize widens and the parent re-lays us out
		int needed = columnsNeeded(rest.size(), bottom);
		if (needed != columns)
		{
			columns = needed;
			SwingUtilities.invokeLater(this::revalidate);
		}

		int colsAvail = Math.max(1, w / RailButton.WIDTH);
		int col = 0;
		int y = PAD;
		boolean pendingDivider = false;

		if (configButton != null)
		{
			configButton.setBounds(colX(col), y, RailButton.WIDTH, RailButton.HEIGHT);
			y += RailButton.HEIGHT + GAP;
			pendingDivider = true;
		}

		for (int i = 0; i < rest.size(); i++)
		{
			RailButton btn = rest.get(i);
			pendingDivider |= i == pinnedCount && pinnedCount > 0;

			int need = RailButton.HEIGHT + (pendingDivider ? DIVIDER_H : 0);
			if (y + need > bottom)
			{
				if (col + 1 < colsAvail)
				{
					col++;
					y = PAD;
					pendingDivider = false; // the wrap itself separates the groups
				}
				else
				{
					btn.setVisible(false);
					overflowed.add(btn);
					continue;
				}
			}

			if (pendingDivider)
			{
				dividers.add(new Rectangle(colX(col) + 6, y + DIVIDER_H / 2 - GAP, RailButton.WIDTH - 12, 1));
				y += DIVIDER_H;
				pendingDivider = false;
			}

			btn.setVisible(true);
			btn.setBounds(colX(col), y, RailButton.WIDTH, RailButton.HEIGHT);
			laidOut.add(btn);
			y += RailButton.HEIGHT + GAP;
		}

		if (!overflowed.isEmpty() || !hidden.isEmpty())
		{
			if (y + RailButton.HEIGHT > bottom && col + 1 < colsAvail)
			{
				col++;
				y = PAD;
			}
			if (y + RailButton.HEIGHT > bottom && !laidOut.isEmpty())
			{
				// no room left: evict the last icon into the overflow to make
				// space for the overflow button itself
				RailButton evicted = laidOut.remove(laidOut.size() - 1);
				evicted.setVisible(false);
				overflowed.add(0, evicted);
				col = (w - evicted.getX()) / RailButton.WIDTH - 1;
				y = evicted.getY();
			}
			overflowButton.setVisible(true);
			overflowButton.setBounds(colX(col), y, RailButton.WIDTH, RailButton.HEIGHT);
		}
		else
		{
			overflowButton.setVisible(false);
		}
	}

	/** Columns fill right-to-left so the first column hugs the window edge. */
	private int colX(int col)
	{
		return getWidth() - (col + 1) * RailButton.WIDTH;
	}

	/**
	 * Columns the full stack needs at this rail height, capped at 4. Slightly
	 * conservative: dividers dropped at column wraps are still counted.
	 */
	private int columnsNeeded(int restCount, int bottom)
	{
		int colH = bottom - PAD;
		if (colH < RailButton.HEIGHT)
		{
			return 1;
		}

		int buttons = (configButton != null ? 1 : 0) + restCount
			+ (hidden.isEmpty() ? 0 : 1); // the overflow button
		int total = buttons * (RailButton.HEIGHT + GAP) + 2 * DIVIDER_H;
		int cols = (total + colH + GAP - 1) / (colH + GAP);
		return Math.max(1, Math.min(4, cols));
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);

		Theme theme = Theme.getActive();
		g.setColor(theme.getBorderSubtle());
		for (Rectangle r : dividers)
		{
			g.fillRect(r.x, r.y, r.width, r.height);
		}

		if (strongDividerY >= 0)
		{
			g.setColor(theme.getBorder());
			g.fillRect(4, strongDividerY, getWidth() - 8, 2);
		}

		if (dragActive && dragIndicatorY >= 0)
		{
			g.setColor(theme.getAccent());
			g.fillRect(dragIndicatorX + 2, dragIndicatorY, RailButton.WIDTH - 4, 2);
		}
	}
}
