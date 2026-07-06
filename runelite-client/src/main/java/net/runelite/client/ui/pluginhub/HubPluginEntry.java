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

import com.google.common.base.Strings;
import com.google.common.html.HtmlEscapers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.Getter;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.externalplugins.PluginHubManifest;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.config.SearchablePlugin;

/**
 * One hub plugin as the window sees it: manifest display data joined with the
 * jar data, the loaded plugin instances (if installed and running), the user
 * count, and the derived action state. Ported from PluginHubPanel.PluginItem,
 * minus the Swing.
 */
class HubPluginEntry implements SearchablePlugin
{
	private static final Pattern SPACES = Pattern.compile(" +");

	enum Action
	{
		INSTALL, REMOVE, UPDATE, UNAVAILABLE
	}

	@Getter
	private final PluginHubManifest.DisplayData manifest;

	@Getter
	@Nullable
	private final PluginHubManifest.JarData jarData;

	@Getter
	private final Collection<Plugin> loadedPlugins;

	@Getter
	private final int userCount;

	@Getter
	private final boolean installed;

	@Getter
	private final List<String> keywords = new ArrayList<>();

	HubPluginEntry(
		@Nullable PluginHubManifest.DisplayData manifest,
		@Nullable PluginHubManifest.JarData jarData,
		Collection<Plugin> loadedPlugins,
		int userCount,
		boolean installed)
	{
		this.manifest = manifest != null
			? manifest
			: ExternalPluginManager.getDisplayData(loadedPlugins.iterator().next().getClass());
		this.jarData = jarData;
		this.loadedPlugins = loadedPlugins;
		this.userCount = userCount;
		this.installed = installed;

		Collections.addAll(keywords, SPACES.split(this.manifest.getDisplayName().toLowerCase()));
		if (this.manifest.getDescription() != null)
		{
			Collections.addAll(keywords, SPACES.split(this.manifest.getDescription().toLowerCase()));
		}
		Collections.addAll(keywords, this.manifest.getAuthor().toLowerCase());
		if (this.manifest.getTags() != null)
		{
			Collections.addAll(keywords, this.manifest.getTags());
		}
	}

	boolean isUpdateAvailable()
	{
		return jarData != null
			&& !loadedPlugins.isEmpty()
			&& !jarData.equals(ExternalPluginManager.getJarData(loadedPlugins.iterator().next().getClass()));
	}

	Action getAction()
	{
		if (!installed && jarData != null)
		{
			return Action.INSTALL;
		}
		if (isUpdateAvailable())
		{
			return Action.UPDATE;
		}
		if (installed)
		{
			return Action.REMOVE;
		}
		return Action.UNAVAILABLE;
	}

	boolean isUnavailable()
	{
		return jarData == null;
	}

	boolean hasTag(String tag)
	{
		if (manifest.getTags() == null)
		{
			return false;
		}
		for (String t : manifest.getTags())
		{
			if (t.equalsIgnoreCase(tag))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Description with the unavailable-reason fallback, HTML-wrapped for
	 * multi-line JLabels (same rules as the old panel).
	 */
	String getDescriptionHtml()
	{
		String text = manifest.getDescription();
		if (isUnavailable())
		{
			if (!Strings.isNullOrEmpty(manifest.getUnavailableReason()))
			{
				text = manifest.getUnavailableReason();
			}
			else
			{
				text = "Plugin is incompatible, requires update by its author";
			}
		}
		if (text == null)
		{
			text = "";
		}
		if (!text.startsWith("<html>"))
		{
			text = "<html>" + HtmlEscapers.htmlEscaper().escape(text) + "</html>";
		}
		return text;
	}

	@Override
	public String getSearchableName()
	{
		return manifest.getDisplayName();
	}

	@Override
	public int installs()
	{
		return userCount;
	}
}
