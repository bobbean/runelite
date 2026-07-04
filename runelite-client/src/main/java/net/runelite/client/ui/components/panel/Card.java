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

import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.theme.Theme;

/**
 * A raised rectangular content container (COMPONENTS.md §3): surface token
 * background, 1px subtle border, SPACE_8/SPACE_12 padding, square corners.
 * Replaces the hand-rolled "overallPanel" compound-border idiom (D4).
 *
 * For clickable cards, {@link #hoverListener()} returns the enter/exit adapter;
 * attach it to the card <em>and</em> to any child components that would
 * otherwise swallow the mouse events (see ProfilePanel for the pattern).
 */
public class Card extends JPanel
{
	private Color baseBackground;
	private Color hoverBackground;
	private boolean hovered;

	/**
	 * A card on the panel background (surfaceRaised).
	 */
	public Card()
	{
		this(Theme.getActive().getSurfaceRaised(), Theme.getActive().getSurfaceHover());
	}

	protected Card(Color base, Color hover)
	{
		baseBackground = base;
		hoverBackground = hover;
		setBackground(base);
		setBorder(new CompoundBorder(
			BorderFactory.createLineBorder(Theme.getActive().getBorderSubtle()),
			new EmptyBorder(Theme.SPACE_8, Theme.SPACE_12, Theme.SPACE_8, Theme.SPACE_12)));
	}

	/**
	 * A card inside a sunken well or list (surfaceSunken).
	 */
	public static Card sunken()
	{
		return new Card(Theme.getActive().getSurfaceSunken(), Theme.getActive().getSurfaceSunkenHover());
	}

	public void setBaseBackground(Color color)
	{
		baseBackground = color;
		if (!hovered)
		{
			setBackground(color);
		}
	}

	public void setHovered(boolean hovered)
	{
		this.hovered = hovered;
		setBackground(hovered ? hoverBackground : baseBackground);
	}

	/**
	 * Enables the hover highlight for clicks on the card itself. Cards whose
	 * children cover most of their area should attach {@link #hoverListener()}
	 * to those children too.
	 */
	public void setHoverable(boolean hoverable)
	{
		if (hoverable)
		{
			addMouseListener(hoverListener());
		}
		else
		{
			setHovered(false);
		}
	}

	public MouseAdapter hoverListener()
	{
		return new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setHovered(true);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				// only clear when the pointer truly left the card, not when it
				// crossed onto a child the listener is also attached to
				Component c = e.getComponent();
				if (!Card.this.contains(SwingUtilities.convertPoint(c, e.getPoint(), Card.this)))
				{
					setHovered(false);
				}
			}
		};
	}
}
