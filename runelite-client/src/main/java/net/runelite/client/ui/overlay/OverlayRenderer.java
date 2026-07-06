/*
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
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
package net.runelite.client.ui.overlay;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.Ints;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.theme.Theme;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.HotkeyListener;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

@Singleton
@Slf4j
public class OverlayRenderer extends MouseAdapter
{
	private static final Marker DEDUPLICATE = MarkerFactory.getMarker("DEDUPLICATE");
	private static final int PADDING = 2;
	private static final int OVERLAY_RESIZE_TOLERANCE = 5;
	private static final int RESIZE_GRIP_SIZE = 8;
	private static final String OVERLAY_HINT_KEY = "overlayManagementHint";

	private final Client client;
	private final OverlayManager overlayManager;
	private final RuneLiteConfig runeLiteConfig;
	private final ClientUI clientUI;
	private final EventBus eventBus;
	private final ChatMessageManager chatMessageManager;
	private final SnapCorners snapCorners;
	private final ConfigManager configManager;

	// management-mode feedback colors, resolved from the active theme once
	// (theme changes are restart-to-apply) — see docs/ui-rework/design/OVERLAYS.md §3
	private final Color manageIdleColor;
	private final Color manageActiveColor;
	private final Color manageTargetColor;

	private Font font, tooltipFont, interfaceFont;

	// Overlay movement variables
	private final Point overlayOffset = new Point();
	private final Point mousePosition = new Point();
	private Overlay currentManagedOverlay;
	private Overlay dragTargetOverlay;
	private Rectangle currentManagedBounds;
	private boolean inOverlayManagingMode;
	private boolean managementLocked; // latched via middle-click; mode survives hotkey release
	private boolean hotkeyHeld;
	private boolean inOverlayResizingMode;
	private boolean inOverlayDraggingMode;
	private boolean startedMovingOverlay;
	private Overlay curHoveredOverlay; // for building menu entries
	private Overlay lastHoveredOverlay; // for off-thread access

	private boolean dragWarn;

	@Inject
	private OverlayRenderer(
		final Client client,
		final OverlayManager overlayManager,
		final RuneLiteConfig runeLiteConfig,
		final MouseManager mouseManager,
		final KeyManager keyManager,
		final ClientUI clientUI,
		final EventBus eventBus,
		final ChatMessageManager chatMessageManager,
		final SnapCorners snapCorners,
		final ConfigManager configManager
	)
	{
		this.client = client;
		this.overlayManager = overlayManager;
		this.runeLiteConfig = runeLiteConfig;
		this.clientUI = clientUI;
		this.eventBus = eventBus;
		this.chatMessageManager = chatMessageManager;
		this.snapCorners = snapCorners;
		this.configManager = configManager;

		final Theme theme = Theme.getActive();
		manageIdleColor = theme.getOverlayManageIdle();
		manageActiveColor = theme.getOverlayManageActive();
		manageTargetColor = theme.getOverlayManageTarget();

		HotkeyListener hotkeyListener = new HotkeyListener(runeLiteConfig::dragHotkey)
		{
			@Override
			public void hotkeyPressed()
			{
				hotkeyHeld = true;
				inOverlayManagingMode = true;
				snapCorners.getSnapCorners().forEach(s ->
				{
					s.overlay = s.new CornerOverlay();
					overlayManager.saveOverlay(s.overlay); // avoid loadOverlay moving the overlay
					overlayManager.add(s.overlay);
				});
			}

			@Override
			public void hotkeyReleased()
			{
				hotkeyHeld = false;
				if (inOverlayManagingMode && !managementLocked)
				{
					inOverlayManagingMode = false;
					snapCorners.getSnapCorners().forEach(s ->
					{
						overlayManager.remove(s.overlay);
						s.overlay = null;
						snapCorners.saveSnapcorner(s);
					});
					resetOverlayManagementMode();
				}
			}
		};

		keyManager.registerKeyListener(hotkeyListener);
		mouseManager.registerMouseListener(this);
		eventBus.register(this);
	}

	@Subscribe
	public void onFocusChanged(FocusChanged event)
	{
		if (!event.isFocused())
		{
			hotkeyHeld = false;
			// a latched mode survives focus loss — the latch exists precisely
			// so the key state doesn't matter
			if (inOverlayManagingMode && !managementLocked)
			{
				inOverlayManagingMode = false;
				resetOverlayManagementMode();
			}

			curHoveredOverlay = null;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (configManager.getConfiguration(RuneLiteConfig.GROUP_NAME, OVERLAY_HINT_KEY) == null)
		{
			configManager.setConfiguration(RuneLiteConfig.GROUP_NAME, OVERLAY_HINT_KEY, true);
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage("Hold " + runeLiteConfig.dragHotkey() + " to move or resize overlays. "
					+ "Shift+right-click an overlay for options, and "
					+ runeLiteConfig.dragHotkey() + "+middle-click to lock overlay editing on.")
				.build());
		}
	}

	@Subscribe
	private void onMenuOpened(MenuOpened event)
	{
		if ((client.isKeyPressed(KeyCode.KC_SHIFT) || inOverlayManagingMode) && curHoveredOverlay != null)
		{
			overlayManager.addOriginMenu(curHoveredOverlay);
		}
	}

	@Subscribe
	protected void onPostMenuSort(PostMenuSort event)
	{
		lastHoveredOverlay = curHoveredOverlay;

		final Overlay overlay = curHoveredOverlay;
		if (overlay == null || client.isMenuOpen())
		{
			return;
		}

		// overlay entries surface on shift+right-click (upstream behavior) and on
		// plain right-click while edit mode is latched on
		if (!client.isKeyPressed(KeyCode.KC_SHIFT) && !inOverlayManagingMode)
		{
			return;
		}

		final String target = overlay.getPlugin() != null
			? ColorUtil.wrapWithColorTag(overlay.getPlugin().getName(), JagexColors.MENU_TARGET)
			: "";

		if (overlay.isMovable() && overlayManager.isOverlapping(overlay))
		{
			client.createMenuEntry(-2)
				.setOption("Send backward")
				.setTarget(target)
				.setType(MenuAction.RUNELITE_OVERLAY)
				.onClick(e -> adjustZOrder(overlay, -1));
			client.createMenuEntry(-2)
				.setOption("Bring forward")
				.setTarget(target)
				.setType(MenuAction.RUNELITE_OVERLAY)
				.onClick(e -> adjustZOrder(overlay, 1));
		}

		if (overlay.isMovable() && overlay.isResettable())
		{
			client.createMenuEntry(-2)
				.setOption("Reset position")
				.setTarget(target)
				.setType(MenuAction.RUNELITE_OVERLAY)
				.onClick(e -> overlayManager.resetOverlay(overlay));
		}

		List<OverlayMenuEntry> menuEntries = overlay.getMenuEntries();

		// Add in reverse order so they display correctly in the right-click menu
		for (int i = menuEntries.size() - 1; i >= 0; --i)
		{
			OverlayMenuEntry overlayMenuEntry = menuEntries.get(i);

			client.createMenuEntry(-2)
				.setOption(overlayMenuEntry.getOption())
				.setTarget(ColorUtil.wrapWithColorTag(overlayMenuEntry.getTarget(), JagexColors.MENU_TARGET))
				.setType(overlayMenuEntry.getMenuAction())
				.onClick(MoreObjects.firstNonNull(overlayMenuEntry.callback, e -> eventBus.post(new OverlayMenuClicked(overlayMenuEntry, overlay))));
		}
	}

	private void adjustZOrder(Overlay overlay, int delta)
	{
		overlay.setZOrder(overlay.getZOrder() + delta);
		overlayManager.saveOverlay(overlay);
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		curHoveredOverlay = null;

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			positionSnapcorners();
		}
	}

	@Subscribe
	private void onCommandExecuted(CommandExecuted evt)
	{
		if (evt.getCommand().equals("resetanchors"))
		{
			snapCorners.reset();
		}
	}

	public void renderOverlayLayer(Graphics2D graphics, final OverlayLayer layer)
	{
		final Collection<Overlay> overlays = overlayManager.getLayer(layer);
		renderOverlays(graphics, overlays, layer);
	}

	public void renderAfterInterface(Graphics2D graphics, int interfaceId, Collection<WidgetItem> widgetItems)
	{
		Collection<Overlay> overlays = overlayManager.getForInterface(interfaceId);
		overlayManager.setWidgetItems(widgetItems);
		renderOverlays(graphics, overlays, OverlayLayer.ABOVE_WIDGETS);
		overlayManager.setWidgetItems(Collections.emptyList());
	}

	public void renderAfterLayer(Graphics2D graphics, Widget layer, Collection<WidgetItem> widgetItems)
	{
		Collection<Overlay> overlays = overlayManager.getForLayer(layer.getId());
		overlayManager.setWidgetItems(widgetItems);
		renderOverlays(graphics, overlays, OverlayLayer.ABOVE_WIDGETS);
		overlayManager.setWidgetItems(Collections.emptyList());
	}

	private void renderOverlays(final Graphics2D graphics, Collection<Overlay> overlays, final OverlayLayer layer)
	{
		if (overlays == null
			|| overlays.isEmpty()
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		OverlayUtil.setGraphicProperties(graphics);

		// Save graphics2d properties so we can restore them later
		final AffineTransform transform = graphics.getTransform();
		final Stroke stroke = graphics.getStroke();
		final Composite composite = graphics.getComposite();
		final Paint paint = graphics.getPaint();
		final RenderingHints renderingHints = graphics.getRenderingHints();
		final Color background = graphics.getBackground();

		// Cache overlay fonts
		this.font = runeLiteConfig.dynamicOverlayFont().getFont();
		this.tooltipFont = runeLiteConfig.tooltipFont().getFont();
		this.interfaceFont = runeLiteConfig.interfaceFont().getFont();

		final Rectangle clip = clipBounds(layer);
		graphics.setClip(clip);

		final Point location = new Point();
		for (Overlay overlay : overlays)
		{
			final OverlayPosition overlayPosition = getCorrectedOverlayPosition(overlay);
			final Rectangle bounds = overlay.getBounds();
			final Point preferredLocation = overlay.getPreferredLocation();
			SnapCorner snapCorner = null;

			// If the final position is not modified, layout it
			if (overlayPosition != OverlayPosition.DYNAMIC && overlayPosition != OverlayPosition.TOOLTIP
				&& overlayPosition != OverlayPosition.DETACHED && preferredLocation == null)
			{
				snapCorner = snapCorners.forPosition(overlayPosition);
				snapCorner.getNextDrawPosition(bounds, location);
			}
			else if (preferredLocation != null)
			{
				overlayManager.computeAbsolutePosition(overlay.getOrigin(), overlay.getOriginX(), overlay.getOriginY(), overlay.getPreferredLocation(), location);
			}
			else
			{
				location.setLocation(bounds.x, bounds.y);
			}

			// Clamp the overlay position to ensure it is on screen or within parent bounds
			clampOverlayLocation(location.x, location.y, bounds.width, bounds.height, overlay.getParentBounds(), location);

			if (overlay.getPreferredSize() != null)
			{
				bounds.setSize(overlay.getPreferredSize());
			}

			safeRender(overlay, graphics, location);

			// Adjust snap corner based on where the overlay was drawn
			if (snapCorner != null && bounds.width + bounds.height > 0)
			{
				snapCorner.shift(bounds, PADDING);
			}

			// Restore graphics2d properties prior to drawing bounds
			graphics.setTransform(transform);
			graphics.setStroke(stroke);
			graphics.setComposite(composite);
			graphics.setPaint(paint);
			graphics.setRenderingHints(renderingHints);
			graphics.setBackground(background);
			if (!graphics.getClip().equals(clip))
			{
				graphics.setClip(clip);
			}

			if (!bounds.isEmpty())
			{
				if (inOverlayManagingMode && overlay.isMovable())
				{
					Color boundsColor;
					if ((inOverlayResizingMode || inOverlayDraggingMode) && currentManagedOverlay == overlay)
					{
						boundsColor = manageActiveColor;
					}
					else if (inOverlayDraggingMode && overlay.isDragTargetable() && currentManagedOverlay.isDragTargetable()
						&& currentManagedOverlay.getBounds().intersects(bounds))
					{
						boundsColor = manageTargetColor;
						assert currentManagedOverlay != overlay;
						dragTargetOverlay = overlay;
					}
					else
					{
						boundsColor = manageIdleColor;
					}

					graphics.setColor(boundsColor);
					graphics.draw(bounds);

					if (overlay.isResizable())
					{
						// resize affordance: corner grip, only visible in management mode
						final int gx = bounds.x + bounds.width;
						final int gy = bounds.y + bounds.height;
						graphics.fillPolygon(
							new int[]{gx, gx - RESIZE_GRIP_SIZE, gx},
							new int[]{gy, gy, gy - RESIZE_GRIP_SIZE},
							3);
					}

					graphics.setPaint(paint);
				}

				if (!client.isMenuOpen() && !client.isWidgetSelected() && bounds.contains(mousePosition))
				{
					if (curHoveredOverlay == null || bounds.width * bounds.height <= curHoveredOverlay.getBounds().width * curHoveredOverlay.getBounds().height)
					{
						curHoveredOverlay = overlay;
					}
					overlay.onMouseOver();
				}
			}
		}
	}

	@Override
	public MouseEvent mousePressed(MouseEvent mouseEvent)
	{
		final Point mousePoint = mouseEvent.getPoint();
		mousePosition.setLocation(mousePoint);

		// while the menu is open, clicks operate the menu (e.g. Reset position),
		// so don't grab them for dragging
		if (!inOverlayManagingMode || client.isMenuOpen())
		{
			return mouseEvent;
		}

		if (SwingUtilities.isMiddleMouseButton(mouseEvent))
		{
			// middle-click latches management mode on so it survives releasing
			// the hotkey; a second middle-click (no hotkey needed) unlatches
			managementLocked = !managementLocked;
			if (!managementLocked && !hotkeyHeld)
			{
				inOverlayManagingMode = false;
				resetOverlayManagementMode();
			}
			mouseEvent.consume();
			return mouseEvent;
		}

		// See if we've clicked on an overlay
		currentManagedOverlay = lastHoveredOverlay;
		if (currentManagedOverlay == null || !currentManagedOverlay.isMovable())
		{
			return mouseEvent;
		}

		if (SwingUtilities.isRightMouseButton(mouseEvent))
		{
			// in latched mode (hotkey not held) right-click behaves normally;
			// the blind reset only applies while the hotkey is physically down
			if (!hotkeyHeld)
			{
				return mouseEvent;
			}

			if (currentManagedOverlay.isResettable())
			{
				overlayManager.resetOverlay(currentManagedOverlay);
			}
		}
		else if (SwingUtilities.isLeftMouseButton(mouseEvent))
		{
			final Point offset = new Point(mousePoint.x, mousePoint.y);
			offset.translate(-currentManagedOverlay.getBounds().x, -currentManagedOverlay.getBounds().y);
			overlayOffset.setLocation(offset);

			inOverlayResizingMode = currentManagedOverlay != null && currentManagedOverlay.isResizable() && clientUI.getCurrentCursor() != clientUI.getDefaultCursor();
			inOverlayDraggingMode = !inOverlayResizingMode;
			startedMovingOverlay = true;
			currentManagedBounds = new Rectangle(currentManagedOverlay.getBounds());
		}
		else
		{
			return mouseEvent;
		}

		mouseEvent.consume();
		return mouseEvent;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent mouseEvent)
	{
		final Point mousePoint = mouseEvent.getPoint();
		mousePosition.setLocation(mousePoint);

		if (!inOverlayManagingMode)
		{
			return mouseEvent;
		}

		if (client.isMenuOpen())
		{
			clientUI.setCursor(clientUI.getDefaultCursor());
			return mouseEvent;
		}

		if (!inOverlayResizingMode && !inOverlayDraggingMode)
		{
			currentManagedOverlay = lastHoveredOverlay;
		}

		if (currentManagedOverlay == null || !currentManagedOverlay.isResizable())
		{
			clientUI.setCursor(clientUI.getDefaultCursor());
			return mouseEvent;
		}

		final Rectangle toleranceRect = new Rectangle(currentManagedOverlay.getBounds());
		toleranceRect.grow(-OVERLAY_RESIZE_TOLERANCE, -OVERLAY_RESIZE_TOLERANCE);
		final int outcode = toleranceRect.outcode(mouseEvent.getPoint());

		switch (outcode)
		{
			case Rectangle.OUT_TOP:
				clientUI.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
				break;
			case Rectangle.OUT_TOP | Rectangle.OUT_LEFT:
				clientUI.setCursor(Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR));
				break;
			case Rectangle.OUT_LEFT:
				clientUI.setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
				break;
			case Rectangle.OUT_LEFT | Rectangle.OUT_BOTTOM:
				clientUI.setCursor(Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR));
				break;
			case Rectangle.OUT_BOTTOM:
				clientUI.setCursor(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR));
				break;
			case Rectangle.OUT_BOTTOM | Rectangle.OUT_RIGHT:
				clientUI.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
				break;
			case Rectangle.OUT_RIGHT:
				clientUI.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
				break;
			case Rectangle.OUT_RIGHT | Rectangle.OUT_TOP:
				clientUI.setCursor(Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR));
				break;
			default:
				// center
				clientUI.setCursor(clientUI.getDefaultCursor());
		}

		return mouseEvent;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent mouseEvent)
	{
		final Point p = mouseEvent.getPoint();
		mousePosition.setLocation(p);

		if (!inOverlayManagingMode)
		{
			return mouseEvent;
		}

		if (currentManagedOverlay == null)
		{
			return mouseEvent;
		}

		if (dragTargetOverlay != null && !currentManagedOverlay.getBounds().intersects(dragTargetOverlay.getBounds()))
		{
			// No longer over drag target
			dragTargetOverlay = null;
		}

		final Rectangle canvasRect = new Rectangle(client.getRealDimensions());

		if (!canvasRect.contains(p))
		{
			return mouseEvent;
		}

		if (inOverlayResizingMode)
		{
			final int left = p.x - currentManagedBounds.x; // Distance (in pixels) from the left edge of the bounds
			final int top = p.y - currentManagedBounds.y;  // Distance (in pixels) from the top edge of the bounds
			final int originalX = currentManagedBounds.x;
			final int originalY = currentManagedBounds.y;
			int x = originalX;
			int y = originalY;
			int width = currentManagedBounds.width;
			int height = currentManagedBounds.height;

			switch (clientUI.getCurrentCursor().getType())
			{
				case Cursor.N_RESIZE_CURSOR:
					y += top;
					height -= top;
					break;
				case Cursor.NW_RESIZE_CURSOR:
					x += left;
					y += top;
					width -= left;
					height -= top;
					break;
				case Cursor.W_RESIZE_CURSOR:
					x += left;
					width -= left;
					break;
				case Cursor.SW_RESIZE_CURSOR:
					x += left;
					width -= left;
					height = top;
					break;
				case Cursor.S_RESIZE_CURSOR:
					height = top;
					break;
				case Cursor.SE_RESIZE_CURSOR:
					width = left;
					height = top;
					break;
				case Cursor.E_RESIZE_CURSOR:
					width = left;
					break;
				case Cursor.NE_RESIZE_CURSOR:
					y += top;
					width = left;
					height -= top;
					break;
				default:
					// center
			}

			final int minOverlaySize = currentManagedOverlay.getMinimumSize();
			final int widthOverflow = Math.max(0, minOverlaySize - width);
			final int heightOverflow = Math.max(0, minOverlaySize - height);

			// If this resize operation would cause the dimensions to go below the minimum width/height, reset the
			// dimensions and adjust the x/y position accordingly as needed
			if (widthOverflow > 0)
			{
				width = minOverlaySize;

				if (x > originalX)
				{
					x -= widthOverflow;
				}
			}
			if (heightOverflow > 0)
			{
				height = minOverlaySize;

				if (y > originalY)
				{
					y -= heightOverflow;
				}
			}

			currentManagedBounds.setRect(x, y, width, height);
			currentManagedOverlay.setPreferredSize(new Dimension(currentManagedBounds.width, currentManagedBounds.height));

			Point l = currentManagedOverlay.getPreferredLocation();
			if (l != null)
			{
				l.translate(x - originalX, y -  originalY);
			}
		}
		else if (inOverlayDraggingMode)
		{
			Point overlayPosition = new Point(p);
			overlayPosition.translate(-overlayOffset.x, -overlayOffset.y); // adjust by mouse offset to get overlay position

			// Clamp drag to parent component
			final Rectangle overlayBounds = currentManagedOverlay.getBounds();
			clampOverlayLocation(overlayPosition.x, overlayPosition.y, overlayBounds.width, overlayBounds.height, currentManagedOverlay.getParentBounds(), overlayPosition);

			if (currentManagedOverlay.getOrigin() == OverlayOrigin.AUTO)
			{
				// Compute the new origins for the overlay
				overlayManager.computeOverlayOrigins(currentManagedOverlay, overlayPosition.x, overlayPosition.y, overlayBounds.width, overlayBounds.height);
			}

			// Compute new relative position
			overlayPosition = overlayManager.computeOriginPosition(overlayPosition, currentManagedOverlay.getOrigin(), currentManagedOverlay.getOriginX(), currentManagedOverlay.getOriginY());

			currentManagedOverlay.setPreferredPosition(null);
			currentManagedOverlay.setPreferredLocation(overlayPosition);

			currentManagedOverlay.onDrag();
		}
		else
		{
			return mouseEvent;
		}

		if (startedMovingOverlay)
		{
			// Move currently moved overlay to correct layer
			overlayManager.rebuildOverlayLayers();
			startedMovingOverlay = false;
		}

		return mouseEvent;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent mouseEvent)
	{
		final Point mousePoint = mouseEvent.getPoint();
		mousePosition.setLocation(mousePoint);

		if (!inOverlayManagingMode || currentManagedOverlay == null || (!inOverlayDraggingMode && !inOverlayResizingMode))
		{
			return mouseEvent;
		}

		if (dragTargetOverlay != null)
		{
			if (dragTargetOverlay.onDrag(currentManagedOverlay))
			{
				mouseEvent.consume();
				resetOverlayManagementMode();
				return mouseEvent;
			}
		}

		// Check if the overlay is over a snapcorner and snap it if so
		if (currentManagedOverlay.isSnappable() && inOverlayDraggingMode)
		{
			for (SnapCorner snapCorner : snapCorners.getSnapCorners())
			{
				if (snapCorner.corner().contains(mousePosition))
				{
					OverlayPosition position = snapCorner.position;

					if (position == getCorrectedOverlayPosition(currentManagedOverlay))
					{
						// overlay moves back to default position
						position = null;
					}

					if (overlayManager.cycleCheck(currentManagedOverlay, OverlayOrigin.AUTO, position))
					{
						// the only overlays which can be used as origins are widget overlays & snap corners, but
						// snap corners can't be put into other snapcorners.
						assert currentManagedOverlay instanceof WidgetOverlays.WidgetOverlay;
						chatMessageManager.queue(QueuedMessage.builder()
							.type(ChatMessageType.CONSOLE)
							.runeLiteFormattedMessage("The origin of this anchor is already linked to this overlay, either directly, or indirectly through multiple other overlays. " +
								"Introducing a circular dependency is not permitted.")
							.build());
						break;
					}

					currentManagedOverlay.setPreferredPosition(position);
					currentManagedOverlay.setPreferredLocation(null); // from dragging
					currentManagedOverlay.setOrigin(OverlayOrigin.AUTO);
					currentManagedOverlay.setOriginX(OverlayOriginX.LEFT);
					currentManagedOverlay.setOriginY(OverlayOriginY.TOP);
					// append to the corner's stack so snap order determines stacking order
					currentManagedOverlay.setZOrder(overlayManager.nextZOrder(currentManagedOverlay));
					currentManagedOverlay.revalidate();
					break;
				}
			}
		}

		if (inOverlayDraggingMode && currentManagedOverlay instanceof WidgetOverlays.WidgetOverlay && !dragWarn)
		{
			dragWarn = true;
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage("You've repositioned one of the in-game interfaces. Hold " + runeLiteConfig.dragHotkey() +
					" and drag to reposition the interface again, or " + runeLiteConfig.dragHotkey() + " and right-click to reset.")
				.build());
		}

		overlayManager.saveOverlay(currentManagedOverlay);
		resetOverlayManagementMode();
		mouseEvent.consume();
		return mouseEvent;
	}

	private Rectangle clipBounds(OverlayLayer layer)
	{
		if (!client.isResized() && (layer == OverlayLayer.ABOVE_SCENE || layer == OverlayLayer.UNDER_WIDGETS))
		{
			return new Rectangle(client.getViewportXOffset(),
				client.getViewportYOffset(),
				client.getViewportWidth(),
				client.getViewportHeight());
		}
		else
		{
			return new Rectangle(0, 0, client.getCanvasWidth(), client.getCanvasHeight());
		}
	}

	private void safeRender(Overlay overlay, Graphics2D graphics, Point point)
	{
		final OverlayPosition position = overlay.getPosition();

		// Set font based on configuration
		if (position == OverlayPosition.DYNAMIC || position == OverlayPosition.DETACHED)
		{
			graphics.setFont(font);
		}
		else if (position == OverlayPosition.TOOLTIP)
		{
			graphics.setFont(tooltipFont);
		}
		else
		{
			graphics.setFont(interfaceFont);
		}

		graphics.translate(point.x, point.y);
		overlay.getBounds().setLocation(point);

		final Dimension overlayDimension;
		try
		{
			overlayDimension = overlay.render(graphics);
		}
		catch (Exception ex)
		{
			log.warn(DEDUPLICATE, "Error during overlay rendering", ex);
			return;
		}

		if (overlayDimension != null)
		{
			overlay.getBounds().setSize(overlayDimension);
		}
		else
		{
			overlay.getBounds().setSize(0, 0);
		}
	}

	private OverlayPosition getCorrectedOverlayPosition(final Overlay overlay)
	{
		OverlayPosition overlayPosition = overlay.getPosition();

		if (overlay.getPreferredPosition() != null)
		{
			overlayPosition = overlay.getPreferredPosition();
		}

		if (!client.isResized())
		{
			// On fixed mode, ABOVE_CHATBOX_RIGHT is in the same location as
			// BOTTOM_RIGHT and CANVAS_TOP_RIGHT is same as TOP_RIGHT.
			// Just use BOTTOM_RIGHT and TOP_RIGHT to prevent overlays from
			// drawing over each other.
			switch (overlayPosition)
			{
				case CANVAS_TOP_RIGHT:
					overlayPosition = OverlayPosition.TOP_RIGHT;
					break;
				case ABOVE_CHATBOX_RIGHT:
					overlayPosition = OverlayPosition.BOTTOM_RIGHT;
					break;
			}
		}

		return overlayPosition;
	}

	private void resetOverlayManagementMode()
	{
		inOverlayResizingMode = false;
		inOverlayDraggingMode = false;
		currentManagedOverlay = null;
		dragTargetOverlay = null;
		currentManagedBounds = null;
		clientUI.setCursor(clientUI.getDefaultCursor());
	}

	private void positionSnapcorners()
	{
		var location = new Point();
		for (SnapCorner s : snapCorners.getSnapCorners())
		{
			location.setLocation(s.curx, s.cury); // relative x,y of align corner
			overlayManager.computeAbsolutePosition(OverlayOrigin.MANUAL, s.originX, s.originY, location, location);
			int cx = location.x, cy = location.y; // absolute x,y of the align corner
			s.translateOffsetForAlignment(location);
			int ax = location.x, ay = location.y; // absolute x,y of the top-left corner
			clampOverlayLocation(ax, ay, s.lastsx, s.lastsy, null, location);
			// apply clamp delta to aligned corner
			cx += location.x - ax;
			cy += location.y - ay;
			s.setPosition(cx, cy);
		}
	}

	/**
	 * Adjust the given overlay position to be within its parent's bounds.
	 *
	 * @param out the clamped position
	 */
	private void clampOverlayLocation(int overlayX, int overlayY, int overlayWidth, int overlayHeight, Rectangle parentBounds, Point out)
	{
		int px, py, pw, ph;
		if (parentBounds == null || parentBounds.isEmpty())
		{
			// If no bounds are set, use the full client bounds
			Dimension dim = client.getRealDimensions();
			px = py = 0;
			pw = dim.width;
			ph = dim.height;
		}
		else
		{
			px = parentBounds.x;
			py = parentBounds.y;
			pw = parentBounds.width;
			ph = parentBounds.height;
		}

		// Constrain overlay position to be within the parent bounds
		out.setLocation(
			Ints.constrainToRange(overlayX, px,
				Math.max(px, px + pw - overlayWidth)),
			Ints.constrainToRange(overlayY, py,
				Math.max(py, py + ph - overlayHeight))
		);
	}
}
