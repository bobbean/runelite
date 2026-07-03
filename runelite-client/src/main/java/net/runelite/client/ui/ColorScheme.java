/*
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
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
package net.runelite.client.ui;

import java.awt.Color;
import net.runelite.client.ui.theme.Theme;

/**
 * Compat shim: these constants are delegating aliases for the active
 * {@link Theme}'s tokens (see docs/ui-rework/design/TOKENS.md for the mapping).
 * They are reassigned only by {@link Theme#install} before the LAF loads —
 * treat them as read-only. New code should read the {@link Theme} directly.
 */
public class ColorScheme
{
	/* The accent color — user-adjustable, RuneLite orange by default */
	public static Color BRAND_ORANGE;

	/* The accent color, with lowered opacity */
	public static Color BRAND_ORANGE_TRANSPARENT;

	public static Color DARKER_GRAY_COLOR;
	public static Color DARK_GRAY_COLOR;
	public static Color MEDIUM_GRAY_COLOR;
	public static Color LIGHT_GRAY_COLOR;

	public static Color TEXT_COLOR;
	public static Color CONTROL_COLOR;
	public static Color BORDER_COLOR;

	public static Color DARKER_GRAY_HOVER_COLOR;
	public static Color DARK_GRAY_HOVER_COLOR;

	/* The color for the green progress bar (used in ge offers, farming tracker, etc)*/
	public static Color PROGRESS_COMPLETE_COLOR;

	/* The color for the red progress bar (used in ge offers, farming tracker, etc)*/
	public static Color PROGRESS_ERROR_COLOR;

	/* The color for the orange progress bar (used in ge offers, farming tracker, etc)*/
	public static Color PROGRESS_INPROGRESS_COLOR;

	/* The color for the price indicator in the ge search results */
	public static Color GRAND_EXCHANGE_PRICE;

	/* The color for the high alch indicator in the ge search results */
	public static Color GRAND_EXCHANGE_ALCH;

	/* The color for the limit indicator in the ge search results */
	public static Color GRAND_EXCHANGE_LIMIT;

	/* The background color of the scrollbar's track */
	public static Color SCROLL_TRACK_COLOR;

	static
	{
		applyTheme(Theme.getActive());
	}

	public static void applyTheme(Theme theme)
	{
		BRAND_ORANGE = theme.getAccent();
		BRAND_ORANGE_TRANSPARENT = theme.getAccentMuted();

		DARKER_GRAY_COLOR = theme.getSurfaceSunken();
		DARK_GRAY_COLOR = theme.getSurface();
		MEDIUM_GRAY_COLOR = theme.getSelection();
		LIGHT_GRAY_COLOR = theme.getTextMuted();

		TEXT_COLOR = theme.getTextPrimary();
		CONTROL_COLOR = theme.getControl();
		BORDER_COLOR = theme.getBorder();

		DARKER_GRAY_HOVER_COLOR = theme.getSurfaceSunkenHover();
		DARK_GRAY_HOVER_COLOR = theme.getSurfaceHover();

		PROGRESS_COMPLETE_COLOR = theme.getSuccess();
		PROGRESS_ERROR_COLOR = theme.getError();
		PROGRESS_INPROGRESS_COLOR = theme.getWarning();

		GRAND_EXCHANGE_PRICE = theme.getPositive();
		GRAND_EXCHANGE_ALCH = theme.getValue();
		GRAND_EXCHANGE_LIMIT = theme.getInfo();

		SCROLL_TRACK_COLOR = theme.getScrollTrack();
	}
}
