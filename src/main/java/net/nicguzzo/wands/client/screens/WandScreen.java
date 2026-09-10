package net.nicguzzo.wands.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.nicguzzo.wands.WandsMod;
import net.nicguzzo.wands.client.WandsModClient;
import net.nicguzzo.wands.client.render.ClientRender;
import net.nicguzzo.wands.items.WandItem;
import net.nicguzzo.wands.networking.ClientNetworking;
import net.nicguzzo.wands.wand.WandMode;
import net.nicguzzo.wands.wand.WandProps;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Classic parchment-style wand settings screen for the 1.21.1 NeoForge backport.
 *
 * This deliberately keeps the current 3.2.x WandProps/networking backend and only
 * restores the older full-size UI/layout. The old 9 physical wand-tool slots no
 * longer exist in 3.x, so the top row is a read-only view of the player's selected
 * tool slots. The Tools button opens the current 3.x tool selector.
 */
public class WandScreen extends Screen {
    private static final int PANEL_W = 256;
    private static final int PANEL_H = 256;

    private static final int PAPER = 0xFFF0E3B6;
    private static final int PAPER_EDGE = 0xFFB9A87D;
    private static final int DARK = 0xFF403F37;
    private static final int DARK_HOVER = 0xFF5B5A50;
    private static final int SELECTED = 0xFF43AAA6;
    private static final int SELECTED_HOVER = 0xFF58BBB7;
    private static final int DISABLED = 0xFF77736A;
    private static final int SLOT = 0xFFAA988A;
    private static final int BLACK = 0xFF111111;
    private static final int WHITE = 0xFFF4F4F4;

    // Kept public because the existing HUD/mode-selector/widget classes share
    // these WandScreen palette/layout constants.
    public static final int COLOR_PANEL_BACKGROUND = DARK;
    public static final int COLOR_BTN_HOVER = DARK_HOVER;
    public static final int COLOR_BTN_SELECTED = SELECTED;
    public static final int COLOR_BTN_DISABLED = DISABLED;
    public static final int COLOR_WDGT_HOVER = DARK_HOVER;
    public static final int COLOR_TEXT_PRIMARY = WHITE;
    public static final int COLOR_WDGT_LABEL = 0xFFAAAAAA;
    public static final int COLOR_TAB_DIVIDER = 0xFF444444;
    public static final int SCREEN_MARGIN = 4;

    private static final int LEFT_X = 8;
    private static final int MID_X = 78;
    private static final int RIGHT_X = 148;

    private final ItemStack openedWand;
    private final List<Hit> hits = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int mouseX;
    private int mouseY;

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public WandScreen(ItemStack wandStack) {
        super(Component.empty());
        this.openedWand = wandStack;
    }

    private ItemStack wand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ItemStack main = mc.player.getMainHandItem();
            if (!main.isEmpty() && main.getItem() instanceof WandItem) return main;
            ItemStack off = mc.player.getOffhandItem();
            if (!off.isEmpty() && off.getItem() instanceof WandItem) return off;
        }
        return openedWand;
    }

    private WandItem wandItem() {
        ItemStack stack = wand();
        return stack.getItem() instanceof WandItem wi ? wi : null;
    }

    private void change(Consumer<ItemStack> change) {
        ItemStack stack = wand();
        if (stack.isEmpty() || !(stack.getItem() instanceof WandItem)) return;
        change.accept(stack);
        ClientNetworking.SendWand(stack);
        if (ClientRender.wand != null) {
            WandMode mode = ClientRender.wand.get_mode();
            if (mode != null) mode.redraw(ClientRender.wand);
        }
    }

    private boolean over(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private String fit(String value, int maxWidth) {
        if (value == null) return "";
        if (font.width(value) <= maxWidth) return value;
        String suffix = "..";
        int n = value.length();
        while (n > 0 && font.width(value.substring(0, n) + suffix) > maxWidth) n--;
        return n <= 0 ? "" : value.substring(0, n) + suffix;
    }

    private void heading(GuiGraphics gui, String text, int x, int y) {
        gui.drawString(font, text, x, y, BLACK, false);
    }

    private void row(GuiGraphics gui, int x, int y, int w, int h, String text,
                     boolean selected, boolean enabled, Runnable click) {
        int bg;
        if (!enabled) {
            bg = DISABLED;
        } else if (selected) {
            bg = over(x, y, w, h) ? SELECTED_HOVER : SELECTED;
        } else {
            bg = over(x, y, w, h) ? DARK_HOVER : DARK;
        }
        gui.fill(x, y, x + w, y + h, bg);
        int color = selected ? BLACK : WHITE;
        if (!enabled) color = 0xFFD0CDC5;
        gui.drawString(font, fit(text, w - 4), x + 2, y + Math.max(1, (h - font.lineHeight) / 2), color, false);
        if (enabled && click != null) hits.add(new Hit(x, y, w, h, click));
    }

    private int toggle(GuiGraphics gui, int x, int y, int w, String text, boolean selected, Runnable click) {
        row(gui, x, y, w, 10, text, selected, true, click);
        return y + 11;
    }

    private int cycle(GuiGraphics gui, int x, int y, int w, String label, String value, Runnable click) {
        row(gui, x, y, w, 10, label + ": " + value, false, true, click);
        return y + 11;
    }

    private int spinner(GuiGraphics gui, int x, int y, int w, String label, WandProps.Value value) {
        ItemStack stack = wand();
        int current = WandProps.getVal(stack, value);
        int labelW = 52;
        int minusW = 10;
        int valueW = 24;
        int plusW = 10;
        int controlsW = minusW + valueW + plusW;
        if (w - controlsW - 2 < labelW) labelW = w - controlsW - 2;

        gui.drawString(font, fit(label, labelW), x, y + 2, BLACK, false);
        int bx = x + w - controlsW;
        row(gui, bx, y, minusW, 12, "-", false, current > value.min,
            () -> change(s -> WandProps.setVal(s, value, WandProps.getVal(s, value) - increment(value))));
        row(gui, bx + minusW, y, valueW, 12, Integer.toString(current), false, false, null);
        row(gui, bx + minusW + valueW, y, plusW, 12, "+", false, current < value.max,
            () -> change(s -> WandProps.setVal(s, value, WandProps.getVal(s, value) + increment(value))));
        return y + 13;
    }

    private int gridSpinner(GuiGraphics gui, int x, int y, int w, String label, WandProps.Value value) {
        WandItem wi = wandItem();
        int limit = wi == null ? value.max : wi.limit;
        ItemStack stack = wand();
        int current = WandProps.getVal(stack, value);
        int labelW = 52;
        int controlsW = 44;
        if (w - controlsW - 2 < labelW) labelW = w - controlsW - 2;

        gui.drawString(font, fit(label, labelW), x, y + 2, BLACK, false);
        int bx = x + w - controlsW;
        row(gui, bx, y, 10, 12, "-", false, current > 1,
            () -> change(s -> WandProps.setGridVal(s, value, WandProps.getVal(s, value) - 1, limit)));
        row(gui, bx + 10, y, 24, 12, Integer.toString(current), false, false, null);
        row(gui, bx + 34, y, 10, 12, "+", false, current < limit,
            () -> change(s -> WandProps.setGridVal(s, value, WandProps.getVal(s, value) + 1, limit)));
        return y + 13;
    }

    private int increment(WandProps.Value value) {
        return value == WandProps.Value.BLASTRAD ? 2 : 1;
    }

    private void drawToolSlots(GuiGraphics gui) {
        int startX = panelX + 46;
        int y = panelY + 8;
        int totalW = 9 * 18;

        gui.fill(startX - 2, y - 2, startX + totalW + 2, y + 20, BLACK);
        for (int i = 0; i < 9; i++) {
            int x = startX + i * 18;
            gui.fill(x, y, x + 16, y + 16, SLOT);
            gui.fill(x, y, x + 16, y + 1, PAPER_EDGE);
            gui.fill(x, y, x + 1, y + 16, PAPER_EDGE);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && ClientRender.wand != null && ClientRender.wand.player_data != null) {
            int[] selectedTools = ClientRender.wand.player_data.getIntArray("Tools");
            for (int i = 0; i < selectedTools.length && i < 9; i++) {
                int inventoryIndex = selectedTools[i];
                if (inventoryIndex < 0 || inventoryIndex >= 36) continue;
                ItemStack tool = mc.player.getInventory().getItem(inventoryIndex);
                if (tool.isEmpty()) continue;
                int x = startX + i * 18;
                gui.renderFakeItem(tool, x, y);
                gui.renderItemDecorations(font, tool, x, y);
            }
        }

        hits.add(new Hit(startX - 2, y - 2, totalW + 4, 22, this::openTools));
    }

    private void openTools() {
        ClientNetworking.SendKbPacket(WandsMod.WandKeys.MENU.ordinal(), false, false);
    }

    private void drawAction(GuiGraphics gui) {
        int x = panelX + LEFT_X;
        int y = panelY + 38;
        heading(gui, "Action", x, y);
        y += 12;
        WandProps.Mode mode = WandProps.getMode(wand());
        WandProps.Action current = WandProps.getAction(wand());
        for (WandProps.Action action : WandProps.actions) {
            boolean enabled = WandProps.isActionValidForMode(action, mode);
            String name = switch (action) {
                case PLACE -> "Place";
                case REPLACE -> "Replace";
                case DESTROY -> "Destroy";
                case USE -> "Use";
            };
            WandProps.Action chosen = action;
            row(gui, x, y, 60, 10, name, current == action, enabled,
                () -> change(s -> WandProps.setAction(s, chosen)));
            y += 10;
        }
    }

    private void drawBlockRotation(GuiGraphics gui) {
        int x = panelX + LEFT_X;
        int y = panelY + 96;
        heading(gui, "Rotation", x, y);
        y += 12;
        Rotation current = WandProps.getBlockRotation(wand());
        Rotation[] rotations = WandProps.rotations;
        String[] labels = {"0°", "90°", "180°", "270°"};
        for (int i = 0; i < rotations.length && i < labels.length; i++) {
            Rotation chosen = rotations[i];
            row(gui, x, y, 60, 10, labels[i], current == chosen, true,
                () -> change(s -> WandProps.setBlockRotation(s, chosen)));
            y += 10;
        }
    }

    private void drawAxis(GuiGraphics gui) {
        int x = panelX + LEFT_X;
        int y = panelY + 154;
        heading(gui, "Axis", x, y);
        y += 12;
        Direction.Axis current = WandProps.getAxis(wand());
        for (Direction.Axis axis : WandProps.axes) {
            Direction.Axis chosen = axis;
            String label = axis.getName().toLowerCase();
            row(gui, x, y, 60, 10, label, current == chosen, true,
                () -> change(s -> WandProps.setAxis(s, chosen)));
            y += 10;
        }
    }

    private boolean canUseMode(WandProps.Mode mode) {
        if (mode == WandProps.Mode.VEIN) return WandsMod.config.enable_vein_mode;
        if (mode == WandProps.Mode.BLAST) {
            WandItem wi = wandItem();
            return wi != null && wi.can_blast && WandsMod.config.enable_blast_mode;
        }
        return true;
    }

    private String modeName(WandProps.Mode mode) {
        return Component.translatable(mode.toString()).getString();
    }

    private void drawModes(GuiGraphics gui) {
        int x = panelX + MID_X;
        int y = panelY + 38;
        heading(gui, "Mode", x, y);
        y += 12;
        WandProps.Mode current = WandProps.getMode(wand());
        for (WandProps.Mode mode : WandProps.modes) {
            WandProps.Mode chosen = mode;
            boolean enabled = canUseMode(mode);
            row(gui, x, y, 64, 10, modeName(mode), current == mode, enabled,
                () -> change(s -> WandProps.switchMode(s, chosen)));
            y += 10;
        }
    }

    private void drawStateMode(GuiGraphics gui) {
        WandProps.Mode mode = WandProps.getMode(wand());
        if (!WandProps.stateModeAppliesTo(mode)) return;

        int x = panelX + MID_X;
        int y = panelY + 194;
        WandProps.StateMode current = WandProps.getStateMode(wand());
        heading(gui, "Block State", x, y);
        y += 12;

        row(gui, x, y, 64, 10, "Use Same State", current == WandProps.StateMode.CLONE, true,
            () -> change(s -> WandProps.setStateMode(s, WandProps.StateMode.CLONE)));
        y += 10;

        boolean adjust = current == WandProps.StateMode.APPLY || current == WandProps.StateMode.APPLY_FLIP;
        row(gui, x, y, 64, 10, "Rotation/Axis", adjust, true,
            () -> change(s -> {
                WandProps.StateMode sm = WandProps.getStateMode(s);
                if (sm != WandProps.StateMode.APPLY_FLIP) WandProps.setStateMode(s, WandProps.StateMode.APPLY);
            }));
        y += 10;

        row(gui, x, y, 64, 10, "Use Target", current == WandProps.StateMode.TARGET, true,
            () -> change(s -> WandProps.setStateMode(s, WandProps.StateMode.TARGET)));
    }

    private int drawDropPosition(GuiGraphics gui, int x, int y, int w) {
        if (ClientRender.wand == null) return y;
        boolean player = ClientRender.wand.drop_on_player;
        return cycle(gui, x, y, w, "Drop on", player ? "player" : "block", () -> {
            if (ClientRender.wand != null) {
                ClientRender.wand.drop_on_player = !ClientRender.wand.drop_on_player;
                ClientNetworking.SendGlobalSettings(ClientRender.wand.drop_on_player);
            }
        });
    }

    private int drawReplaceMode(GuiGraphics gui, int x, int y, int w) {
        int v = WandProps.getVal(wand(), WandProps.Value.REPLACE_MODE);
        String[] names = {"None", "Replaceable", "All"};
        String value = names[Math.max(0, Math.min(v, names.length - 1))];
        return cycle(gui, x, y, w, "Replace", value,
            () -> change(s -> WandProps.setVal(s, WandProps.Value.REPLACE_MODE,
                (WandProps.getVal(s, WandProps.Value.REPLACE_MODE) + 1) % 3)));
    }

    private int drawCommonControls(GuiGraphics gui, int x, int y, int w, WandProps.Mode mode) {
        WandProps.Action action = WandProps.getAction(wand());

        if (action == WandProps.Action.PLACE && WandProps.valueAppliesTo(WandProps.Value.REPLACE_MODE, mode)) {
            y = drawReplaceMode(gui, x, y, w);
        }
        if (action == WandProps.Action.DESTROY || action == WandProps.Action.REPLACE) {
            y = drawDropPosition(gui, x, y, w);
        }

        WandProps.StateMode sm = WandProps.getStateMode(wand());
        if (WandProps.stateModeAppliesTo(mode) &&
            (sm == WandProps.StateMode.APPLY || sm == WandProps.StateMode.APPLY_FLIP)) {
            boolean top = sm == WandProps.StateMode.APPLY_FLIP;
            y = toggle(gui, x, y, w, "Block Flip: " + (top ? "Top" : "Bottom"), top,
                () -> change(s -> WandProps.setStateMode(s,
                    WandProps.getStateMode(s) == WandProps.StateMode.APPLY_FLIP
                        ? WandProps.StateMode.APPLY : WandProps.StateMode.APPLY_FLIP)));
        }

        if (WandProps.flagAppliesTo(WandProps.Flag.INCSELBLOCK, mode)) {
            boolean v = WandProps.getFlag(wand(), WandProps.Flag.INCSELBLOCK);
            y = toggle(gui, x, y, w, "Include in sel", v,
                () -> change(s -> WandProps.toggleFlag(s, WandProps.Flag.INCSELBLOCK)));
        }

        if (WandProps.flagAppliesTo(WandProps.Flag.MATCHSTATE, mode)) {
            boolean v = WandProps.getFlag(wand(), WandProps.Flag.MATCHSTATE);
            y = toggle(gui, x, y, w, "Match State", v,
                () -> change(s -> WandProps.toggleFlag(s, WandProps.Flag.MATCHSTATE)));
        }

        if (WandProps.flagAppliesTo(WandProps.Flag.TARGET_AIR, mode)) {
            boolean v = WandProps.getFlag(wand(), WandProps.Flag.TARGET_AIR);
            y = toggle(gui, x, y, w, "Target air", v,
                () -> change(s -> WandProps.toggleFlag(s, WandProps.Flag.TARGET_AIR)));
        }

        if (WandProps.flagAppliesTo(WandProps.Flag.CLEAR_P1, mode)) {
            boolean keep = !WandProps.getFlag(wand(), WandProps.Flag.CLEAR_P1);
            y = toggle(gui, x, y, w, "Keep Start", keep,
                () -> change(s -> WandProps.toggleFlag(s, WandProps.Flag.CLEAR_P1)));
        }

        boolean stair = WandProps.getFlag(wand(), WandProps.Flag.STAIRSLAB);
        y = toggle(gui, x, y, w, "Stairs/Slabs", stair,
            () -> change(s -> WandProps.toggleFlag(s, WandProps.Flag.STAIRSLAB)));

        return spinner(gui, x, y, w, "Reach", WandProps.Value.REACH_DISTANCE);
    }

    private void drawModeControls(GuiGraphics gui) {
        int x = panelX + RIGHT_X;
        int y = panelY + 38;
        int w = 100;

        row(gui, x + 26, y, 48, 12, "Tools", false, true, this::openTools);
        y += 18;

        WandProps.Mode mode = WandProps.getMode(wand());

        switch (mode) {
            case DIRECTION -> {
                y = spinner(gui, x, y, w, "Multiplier", WandProps.Value.MULTIPLIER);
                boolean inv = WandProps.getFlag(wand(), WandProps.Flag.INVERTED);
                y = toggle(gui, x, y, w, "Invert", inv,
                    () -> change(s -> WandProps.toggleFlag(s, WandProps.Flag.INVERTED)));
            }
            case ROW_COL -> {
                WandProps.Orientation o = WandProps.getOrientation(wand());
                y = cycle(gui, x, y, w, "Orientation", o == WandProps.Orientation.ROW ? "Row" : "Column",
                    () -> change(WandProps::nextOrientation));
                y = spinner(gui, x, y, w, "Limit", WandProps.Value.ROWCOLLIM);
            }
            case FILL -> {
                boolean filled = WandProps.getFlag(wand(), WandProps.Flag.RFILLED);
                y = toggle(gui, x, y, w, "Filled", filled,
                    () -> change(s -> WandProps.toggleFlag(s, WandProps.Flag.RFILLED)));
            }
            case AREA -> {
                boolean diagonal = !WandProps.getFlag(wand(), WandProps.Flag.DIAGSPREAD);
                y = toggle(gui, x, y, w, "Diagonal Spread", diagonal,
                    () -> change(s -> WandProps.toggleFlag(s, WandProps.Flag.DIAGSPREAD)));
                y = spinner(gui, x, y, w, "Limit", WandProps.Value.AREALIM);
                y = spinner(gui, x, y, w, "Skip %", WandProps.Value.SKIPBLOCK);
            }
            case GRID -> {
                y = gridSpinner(gui, x, y, w, "Rows", WandProps.Value.GRIDM);
                y = gridSpinner(gui, x, y, w, "Cols", WandProps.Value.GRIDN);
                y = spinner(gui, x, y, w, "Row Skip", WandProps.Value.GRIDMS);
                y = spinner(gui, x, y, w, "Col Skip", WandProps.Value.GRIDNS);
                y = spinner(gui, x, y, w, "Row Offset", WandProps.Value.GRIDMOFF);
                y = spinner(gui, x, y, w, "Col Offset", WandProps.Value.GRIDNOFF);
                Rotation r = WandProps.getRotation(wand());
                String[] rot = {"0°", "90°", "180°", "270°"};
                int ri = Math.max(0, Math.min(r.ordinal(), rot.length - 1));
                y = cycle(gui, x, y, w, "Pattern", rot[ri],
                    () -> change(WandProps::nextRotation));
            }
            case LINE -> {
                // No unique numeric option in 3.2.x.
            }
            case CIRCLE -> {
                WandProps.Plane p = WandProps.getPlane(wand());
                y = cycle(gui, x, y, w, "Plane", p.name(),
                    () -> change(WandProps::nextPlane));
                boolean filled = WandProps.getFlag(wand(), WandProps.Flag.CFILLED);
                y = toggle(gui, x, y, w, "Filled", filled,
                    () -> change(s -> WandProps.toggleFlag(s, WandProps.Flag.CFILLED)));
                boolean even = WandProps.getFlag(wand(), WandProps.Flag.EVEN);
                y = toggle(gui, x, y, w, "Even", even,
                    () -> change(s -> WandProps.toggleFlag(s, WandProps.Flag.EVEN)));
            }
            case BOX -> {
                y = spinner(gui, x, y, w, "Width", WandProps.Value.BOX_W);
                y = spinner(gui, x, y, w, "Height", WandProps.Value.BOX_H);
                y = spinner(gui, x, y, w, "Depth", WandProps.Value.BOX_DEPTH);
                y = spinner(gui, x, y, w, "Offset x", WandProps.Value.BOX_OX);
                y = spinner(gui, x, y, w, "Offset y", WandProps.Value.BOX_OY);
                boolean inv = WandProps.getFlag(wand(), WandProps.Flag.BOX_INVERTED);
                y = toggle(gui, x, y, w, "Invert Depth", inv,
                    () -> change(s -> WandProps.toggleFlag(s, WandProps.Flag.BOX_INVERTED)));
            }
            case VEIN -> y = spinner(gui, x, y, w, "Limit", WandProps.Value.AREALIM);
            case BLAST -> y = spinner(gui, x, y, w, "Blast Radius", WandProps.Value.BLASTRAD);
            case SPHERE -> {
                // Shape size is selected in-world, like the current 3.2.x screen.
            }
            case ROCK -> {
                y = spinner(gui, x, y, w, "Radius", WandProps.Value.ROCK_RADIUS);
                y = spinner(gui, x, y, w, "Noise", WandProps.Value.ROCK_NOISE);
            }
            case COPY -> {
                // Selection is defined in-world.
            }
            case PASTE -> {
                int mirror = WandProps.getVal(wand(), WandProps.Value.MIRRORAXIS);
                String mirrorName = mirror == 1 ? "L/R" : mirror == 2 ? "F/B" : "None";
                y = cycle(gui, x, y, w, "Mirror", mirrorName,
                    () -> change(s -> WandProps.setVal(s, WandProps.Value.MIRRORAXIS,
                        (WandProps.getVal(s, WandProps.Value.MIRRORAXIS) + 1) % 3)));
                Rotation r = WandProps.getRotation(wand());
                String[] rot = {"0°", "90°", "180°", "270°"};
                int ri = Math.max(0, Math.min(r.ordinal(), rot.length - 1));
                y = cycle(gui, x, y, w, "Pattern", rot[ri],
                    () -> change(WandProps::nextRotation));
            }
        }

        drawCommonControls(gui, x, y, w, mode);
    }

    private void drawConfigButton(GuiGraphics gui) {
        int x = panelX + LEFT_X;
        int y = panelY + 232;
        row(gui, x, y, 38, 12, "Conf", false, true,
            () -> Minecraft.getInstance().setScreen(WandConfigScreen.create(this)));
    }

    private void drawPanel(GuiGraphics gui) {
        gui.fill(panelX - 2, panelY - 2, panelX + PANEL_W + 2, panelY + PANEL_H + 2, PAPER_EDGE);
        gui.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, PAPER);

        // Small uneven parchment-like edge accents so the panel is not a sterile rectangle.
        gui.fill(panelX + 1, panelY + 2, panelX + 5, panelY + 4, PAPER_EDGE);
        gui.fill(panelX + PANEL_W - 6, panelY + 1, panelX + PANEL_W - 1, panelY + 3, PAPER_EDGE);
        gui.fill(panelX + 2, panelY + PANEL_H - 4, panelX + 7, panelY + PANEL_H - 2, PAPER_EDGE);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.panelX = (width - PANEL_W) / 2;
        this.panelY = (height - PANEL_H) / 2;
        this.hits.clear();

        gui.fill(0, 0, width, height, 0x99000000);
        drawPanel(gui);
        drawToolSlots(gui);
        drawAction(gui);
        drawBlockRotation(gui);
        drawAxis(gui);
        drawModes(gui);
        drawStateMode(gui);
        drawModeControls(gui);
        drawConfigButton(gui);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (hit.contains(mouseX, mouseY)) {
                    hit.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 || (WandsModClient.wand_menu_km != null &&
            WandsModClient.wand_menu_km.matches(keyCode, scanCode))) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
