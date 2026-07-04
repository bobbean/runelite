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
package net.runelite.client.ui.components.panel;

import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.theme.Theme;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

/**
 * A titled section of a panel (COMPONENTS.md §2): a {@link SectionHeader} over
 * a body container, optionally collapsible with a chevron and click-anywhere
 * toggle. Extracted from ConfigPanel's Phase 2 section code.
 *
 * Per D5, open/closed persistence stays the caller's job — register a
 * {@link #setToggleListener} and store the state; {@link #setOpen} does not
 * fire the listener.
 */
public class Section extends JPanel
{
	private static final ImageIcon EXPAND_ICON;
	private static final ImageIcon RETRACT_ICON;

	static
	{
		BufferedImage arrowRight = ImageUtil.luminanceOffset(
			ImageUtil.loadImageResource(Section.class, "/util/arrow_right.png"), -121);
		EXPAND_ICON = new ImageIcon(arrowRight);
		RETRACT_ICON = new ImageIcon(ImageUtil.rotateImage(arrowRight, Math.PI / 2));
	}

	private final SectionHeader header;
	private final JButton toggle;
	private final JPanel body;
	private final boolean collapsible;
	private boolean open;
	private Consumer<Boolean> toggleListener;

	/**
	 * A static (always-open) grouping section.
	 */
	public Section(String name)
	{
		this(name, false, true);
	}

	/**
	 * A collapsible section.
	 */
	public Section(String name, boolean open)
	{
		this(name, true, open);
	}

	private Section(String name, boolean collapsible, boolean open)
	{
		this.collapsible = collapsible;
		this.open = open;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		// pinned so a squeezed scroll pane can't collapse the section horizontally
		setMinimumSize(new Dimension(PluginPanel.PANEL_WIDTH, 0));

		header = new SectionHeader(name);
		header.setMinimumSize(new Dimension(PluginPanel.PANEL_WIDTH, 0));
		add(header);

		body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new DynamicGridLayout(0, 1, 0, Theme.SPACE_8));
		body.setMinimumSize(new Dimension(PluginPanel.PANEL_WIDTH, 0));
		body.setVisible(open);
		add(body);

		if (collapsible)
		{
			// a closing rule under the contents, so an open section reads as a unit
			body.setBorder(new CompoundBorder(
				new MatteBorder(0, 0, 1, 0, Theme.getActive().getBorderSubtle()),
				new EmptyBorder(Theme.SPACE_8, 0, Theme.SPACE_8, 0)));

			toggle = new JButton(open ? RETRACT_ICON : EXPAND_ICON);
			toggle.setPreferredSize(new Dimension(18, 0));
			toggle.setBorder(new EmptyBorder(0, 0, 0, 5));
			toggle.setToolTipText(open ? "Retract" : "Expand");
			SwingUtil.removeButtonDecorations(toggle);
			toggle.addActionListener(ev -> userToggle());
			header.addLeading(toggle);

			// the whole header is a click target, not just the chevron
			MouseAdapter clickAnywhere = new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					userToggle();
				}
			};
			header.addMouseListener(clickAnywhere);
			header.getLabel().addMouseListener(clickAnywhere);
		}
		else
		{
			toggle = null;
			body.setBorder(new EmptyBorder(Theme.SPACE_8, 0, 0, 0));
		}
	}

	/**
	 * The container section contents go into. Defaults to a single-column
	 * {@link DynamicGridLayout} with SPACE_8 row gaps; override with
	 * {@code getBody().setLayout(...)} if the contents need something else.
	 */
	public JPanel getBody()
	{
		return body;
	}

	public boolean isOpen()
	{
		return open;
	}

	/**
	 * Opens or closes the section without firing the toggle listener.
	 */
	public void setOpen(boolean open)
	{
		if (!collapsible || this.open == open)
		{
			return;
		}

		this.open = open;
		body.setVisible(open);
		toggle.setIcon(open ? RETRACT_ICON : EXPAND_ICON);
		toggle.setToolTipText(open ? "Retract" : "Expand");
		SwingUtilities.invokeLater(body::revalidate);
	}

	/**
	 * Called with the new open state on user-initiated toggles (D5: persist it here).
	 */
	public void setToggleListener(Consumer<Boolean> listener)
	{
		toggleListener = listener;
	}

	public void setHeaderTooltip(String tooltip)
	{
		header.setLabelTooltip(tooltip);
	}

	private void userToggle()
	{
		setOpen(!open);
		if (toggleListener != null)
		{
			toggleListener.accept(open);
		}
	}
}
