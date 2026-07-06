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
package net.runelite.client.ui.pluginhub;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deliberately small Markdown → HTML converter for rendering plugin READMEs
 * in a Swing {@link javax.swing.JEditorPane} (HTML 3.2). It is not spec
 * complete — it covers the constructs READMEs actually use and, importantly,
 * <em>drops images and strips raw HTML tags</em> so nothing is fetched from a
 * third-party host when a description is shown. Links are kept (they open in
 * the browser on click, user-initiated).
 */
final class PluginHubMarkdown
{
	private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)");
	private static final Pattern HTML_COMMENT = Pattern.compile("(?s)<!--.*?-->");
	private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
	private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)\\s]+)[^)]*\\)");
	private static final Pattern BOLD = Pattern.compile("(\\*\\*|__)(.+?)\\1");
	private static final Pattern ITALIC = Pattern.compile("(?<![\\w*])[*_](?!\\s)(.+?)(?<!\\s)[*_](?![\\w*])");
	private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
	private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
	private static final Pattern UL_ITEM = Pattern.compile("^\\s*[-*+]\\s+(.*)$");
	private static final Pattern OL_ITEM = Pattern.compile("^\\s*\\d+[.)]\\s+(.*)$");

	private PluginHubMarkdown()
	{
	}

	static String toHtml(String markdown)
	{
		if (markdown == null || markdown.trim().isEmpty())
		{
			return null;
		}

		String md = markdown.replace("\r\n", "\n").replace("\r", "\n");
		md = HTML_COMMENT.matcher(md).replaceAll("");

		String[] lines = md.split("\n", -1);
		StringBuilder out = new StringBuilder("<html><body>");

		boolean inFence = false;
		String listTag = null; // "ul" / "ol" while inside a list
		List<String> paragraph = new ArrayList<>();
		StringBuilder code = new StringBuilder();

		for (String line : lines)
		{
			String trimmed = line.trim();

			if (trimmed.startsWith("```") || trimmed.startsWith("~~~"))
			{
				if (inFence)
				{
					out.append("<pre>").append(escape(code.toString())).append("</pre>");
					code.setLength(0);
					inFence = false;
				}
				else
				{
					flushParagraph(out, paragraph);
					listTag = closeList(out, listTag);
					inFence = true;
				}
				continue;
			}

			if (inFence)
			{
				code.append(line).append('\n');
				continue;
			}

			Matcher heading = HEADING.matcher(line);
			Matcher ul = UL_ITEM.matcher(line);
			Matcher ol = OL_ITEM.matcher(line);

			if (trimmed.isEmpty())
			{
				flushParagraph(out, paragraph);
				listTag = closeList(out, listTag);
			}
			else if (heading.matches())
			{
				flushParagraph(out, paragraph);
				listTag = closeList(out, listTag);
				int level = Math.min(6, heading.group(1).length());
				out.append("<h").append(level).append('>')
					.append(inline(heading.group(2).trim()))
					.append("</h").append(level).append('>');
			}
			else if (trimmed.matches("^([-*_])\\1{2,}$"))
			{
				flushParagraph(out, paragraph);
				listTag = closeList(out, listTag);
				out.append("<hr>");
			}
			else if (ul.matches() || ol.matches())
			{
				flushParagraph(out, paragraph);
				String want = ul.matches() ? "ul" : "ol";
				if (!want.equals(listTag))
				{
					listTag = closeList(out, listTag);
					out.append('<').append(want).append('>');
					listTag = want;
				}
				out.append("<li>").append(inline((ul.matches() ? ul : ol).group(1).trim())).append("</li>");
			}
			else
			{
				paragraph.add(trimmed);
			}
		}

		if (inFence)
		{
			out.append("<pre>").append(escape(code.toString())).append("</pre>");
		}
		flushParagraph(out, paragraph);
		closeList(out, listTag);

		out.append("</body></html>");
		return out.toString();
	}

	private static void flushParagraph(StringBuilder out, List<String> paragraph)
	{
		if (paragraph.isEmpty())
		{
			return;
		}
		String html = inline(String.join(" ", paragraph));
		paragraph.clear();
		// lines that were only an image or raw HTML (e.g. a centered banner)
		// collapse to nothing once stripped — don't emit an empty paragraph
		if (!html.trim().isEmpty())
		{
			out.append("<p>").append(html).append("</p>");
		}
	}

	private static String closeList(StringBuilder out, String listTag)
	{
		if (listTag != null)
		{
			out.append("</").append(listTag).append('>');
		}
		return null;
	}

	private static String inline(String text)
	{
		String s = IMAGE.matcher(text).replaceAll("");
		s = HTML_TAG.matcher(s).replaceAll("");
		s = escape(s);
		s = LINK.matcher(s).replaceAll("<a href=\"$2\">$1</a>");
		s = BOLD.matcher(s).replaceAll("<b>$2</b>");
		s = ITALIC.matcher(s).replaceAll("<i>$1</i>");
		s = INLINE_CODE.matcher(s).replaceAll("<code>$1</code>");
		return s;
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
