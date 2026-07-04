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
package net.runelite.client.ui.components.colorpicker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.ui.theme.Theme;
import net.runelite.client.util.ColorUtil;

/**
 * The quick color chooser for config color settings (COMPONENTS.md sequencing,
 * audit item: palette/preset list with hex, full picker secondary): a preset
 * swatch grid plus a hex field, with a button into {@link RuneliteColorPicker}
 * for anything beyond them.
 */
public class ColorPalettePopup extends JPopupMenu
{
	// the classic vivid set plugin defaults draw from — game-legible colors
	private static final Color[] PRESETS = {
		Color.WHITE, Color.LIGHT_GRAY, Color.GRAY, Color.DARK_GRAY, Color.BLACK, Color.RED,
		Color.ORANGE, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA,
	};
	private static final int SWATCH_SIZE = 20;
	private static final int COLUMNS = 6;

	private final Color current;
	private final boolean alphaHidden;

	@Getter
	private Color selectedColor;

	@Setter
	private Consumer<ColorPalettePopup> onSelect;

	@Setter
	private Runnable onOpenPicker;

	public ColorPalettePopup(Color current, boolean alphaHidden)
	{
		this.current = current;
		this.alphaHidden = alphaHidden;

		JPanel content = new JPanel();
		content.setOpaque(false);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBorder(new EmptyBorder(Theme.SPACE_8, Theme.SPACE_8, Theme.SPACE_8, Theme.SPACE_8));

		JPanel grid = new JPanel(new GridLayout(0, COLUMNS, Theme.SPACE_4, Theme.SPACE_4));
		grid.setOpaque(false);
		for (Color preset : PRESETS)
		{
			grid.add(swatch(preset));
		}
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		grid.setMaximumSize(grid.getPreferredSize());
		content.add(grid);

		content.add(Box.createVerticalStrut(Theme.SPACE_8));

		JPanel hexRow = new JPanel(new BorderLayout(Theme.SPACE_4, 0));
		hexRow.setOpaque(false);
		hexRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		hexRow.add(new JLabel("#"), BorderLayout.WEST);

		JTextField hexInput = new JTextField(
			(alphaHidden ? ColorUtil.colorToHexCode(current) : ColorUtil.colorToAlphaHexCode(current)).toUpperCase(Locale.ROOT));
		((AbstractDocument) hexInput.getDocument()).setDocumentFilter(new DocumentFilter()
		{
			@Override
			public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException
			{
				if (!ColorUtil.isHex(text))
				{
					return;
				}
				super.replace(fb, offset, length, text.toUpperCase(Locale.ROOT), attrs);
			}
		});
		hexInput.addActionListener(ev ->
		{
			Color parsed = ColorUtil.fromHex(hexInput.getText());
			if (parsed != null)
			{
				// typed hex is explicit — including its (lack of) alpha
				select(alphaHidden && parsed.getAlpha() != 255
					? new Color(parsed.getRed(), parsed.getGreen(), parsed.getBlue())
					: parsed);
			}
		});
		hexRow.add(hexInput, BorderLayout.CENTER);
		content.add(hexRow);

		content.add(Box.createVerticalStrut(Theme.SPACE_8));

		JButton pickerButton = new JButton("Color picker...");
		pickerButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		pickerButton.addActionListener(ev ->
		{
			setVisible(false);
			if (onOpenPicker != null)
			{
				onOpenPicker.run();
			}
		});
		content.add(pickerButton);

		add(content);
	}

	private Swatch swatch(Color preset)
	{
		Swatch swatch = new Swatch(preset);
		swatch.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				// picking a preset keeps the setting's existing translucency
				select(withCurrentAlpha(preset));
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				swatch.setBorder(BorderFactory.createLineBorder(Theme.getActive().getAccent()));
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				swatch.setBorder(BorderFactory.createLineBorder(Theme.getActive().getBorderSubtle()));
			}
		});
		return swatch;
	}

	private void select(Color color)
	{
		selectedColor = color;
		if (onSelect != null)
		{
			onSelect.accept(this);
		}
		setVisible(false);
	}

	private Color withCurrentAlpha(Color color)
	{
		if (alphaHidden || current.getAlpha() == 255)
		{
			return color;
		}
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), current.getAlpha());
	}

	private static class Swatch extends JPanel
	{
		Swatch(Color color)
		{
			setBackground(color);
			setPreferredSize(new Dimension(SWATCH_SIZE, SWATCH_SIZE));
			setBorder(BorderFactory.createLineBorder(Theme.getActive().getBorderSubtle()));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText(String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue()));
		}
	}
}
