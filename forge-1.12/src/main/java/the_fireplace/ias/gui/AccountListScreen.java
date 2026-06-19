package the_fireplace.ias.gui;

import com.mojang.util.UUIDTypeAdapter;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.util.Session;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import ru.vidtu.ias.Config;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.account.MicrosoftAccount;
import ru.vidtu.ias.account.TokenAccount;
import ru.vidtu.ias.gui.IASAlertScreen;
import the_fireplace.ias.IAS;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The GUI where you can log in to, add, edit, and remove accounts.
 *
 * @author The_Fireplace
 * @author VidTu
 */
public class AccountListScreen extends GuiScreen {
    private static long nextSkinUpdate = System.currentTimeMillis();
    private final GuiScreen prev;
    private AccountList list;
    private GuiButton add;
    private GuiButton login;
    private GuiButton loginOffline;
    private GuiButton delete;
    private GuiButton edit;
    private GuiButton reloadSkins;
    private GuiButton copyTempToken;
    private GuiButton copyPermToken;
    private GuiButton changeName;
    private GuiButton changeSkin;
    private GuiButton cancel;
    private GuiTextField search;
    private String state;

    public AccountListScreen(GuiScreen prev) {
        this.prev = prev;
    }

    @Override
    public void initGui() {
        list = new AccountList(mc, width, height);

        // Top left
        addButton(reloadSkins = new GuiButton(0, 2, 2, 120, 20, I18n.format("ias.listGui.reloadSkins")));

        // Search bar
        search = new GuiTextField(1, this.fontRenderer, this.width / 2 - 80, 14, 160, 16);

        // Button layout - 4 rows of 3 at the bottom:
        // Row 1: Login          | Edit         | Add
        // Row 2: Login (Offline)| Delete       | Copy Temp Token
        // Row 3: Change Name   | Change Skin  | Copy Perm Token
        // Row 4:               |              | Cancel

        int col1 = this.width / 2 - 154;
        int col2 = this.width / 2 - 47;
        int col3 = this.width / 2 + 60;
        int btnW = 100;
        int row1 = this.height - 100;
        int row2 = this.height - 76;
        int row3 = this.height - 52;
        int row4 = this.height - 28;

        addButton(login = new GuiButton(3, col1, row1, btnW, 20,
                I18n.format("ias.listGui.login")));
        addButton(edit = new GuiButton(5, col2, row1, btnW, 20,
                I18n.format("ias.listGui.edit")));
        addButton(add = new GuiButton(2, col3, row1, btnW, 20,
                I18n.format("ias.listGui.add")));

        addButton(loginOffline = new GuiButton(4, col1, row2, btnW, 20,
                I18n.format("ias.listGui.loginOffline")));
        addButton(delete = new GuiButton(6, col2, row2, btnW, 20,
                I18n.format("ias.listGui.delete")));
        addButton(copyTempToken = new GuiButton(8, col3, row2, btnW, 20,
                "Copy Temp Token"));

        addButton(changeName = new GuiButton(10, col1, row3, btnW, 20,
                "Change Name"));
        addButton(changeSkin = new GuiButton(11, col2, row3, btnW, 20,
                "Skin & Cape"));
        addButton(copyPermToken = new GuiButton(9, col3, row3, btnW, 20,
                TextFormatting.RED + "Perm Token"));

        addButton(cancel = new GuiButton(7, col3, row4, btnW, 20,
                I18n.format("gui.cancel")));

        updateButtons();
        search.setGuiResponder(new GuiPageButtonList.GuiResponder() {
            @Override
            public void setEntryValue(int id, boolean value) {}
            @Override
            public void setEntryValue(int id, float value) {}
            @Override
            public void setEntryValue(int id, String value) {
                list.updateAccounts(value);
            }
        });
        list.updateAccounts(search.getText());
    }

    @Override
    public void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) reloadSkins();
        else if (button.id == 2) add();
        else if (button.id == 3) login();
        else if (button.id == 4) loginOffline();
        else if (button.id == 5) edit();
        else if (button.id == 6) delete();
        else if (button.id == 7) mc.displayGuiScreen(prev);
        else if (button.id == 8) copyTempToken();
        else if (button.id == 9) copyPermToken();
        else if (button.id == 10) changeName();
        else if (button.id == 11) changeSkin();
        super.actionPerformed(button);
    }

    @Override
    public void mouseClicked(int mx, int my, int btn) throws IOException {
        if (list.mouseClicked(mx, my, btn) || search.mouseClicked(mx, my, btn)) return;
        super.mouseClicked(mx, my, btn);
    }

    @Override
    public void handleMouseInput() throws IOException {
        list.handleMouseInput();
        super.handleMouseInput();
    }

    @Override
    public void updateScreen() {
        search.updateCursorCounter();
        updateButtons();
    }

    @Override
    public void onGuiClosed() {
        Config.save(mc.gameDir.toPath());
    }

    @Override
    public void drawScreen(int mx, int my, float delta) {
        drawDefaultBackground();
        list.drawScreen(mx, my, delta);
        search.drawTextBox();
        if (search.getText().isEmpty()) drawString(fontRenderer, I18n.format("ias.listGui.search"),
                search.x + 4, search.y + (search.height - 8) / 2, 0xFF808080);
        super.drawScreen(mx, my, delta);
        drawCenteredString(fontRenderer, "In-Game Account Switcher", this.width / 2, 4, -1);
        if (list.selectedElement() >= 0) {
            mc.getTextureManager().bindTexture(list.entries.get(list.selectedElement()).skin());
            GlStateManager.color(1F, 1F, 1F, 1F);
            boolean slim = list.entries.get(list.selectedElement()).slimSkin();
            GlStateManager.pushMatrix();
            GlStateManager.scale(4, 4, 4);
            GlStateManager.translate(1, height / 8D - 16D - 4D, 0);
            GuiScreen.drawModalRectWithCustomSizedTexture(4, 0, 8, 8, 8, 8, 64, 64); // Head
            GuiScreen.drawModalRectWithCustomSizedTexture(4, 8, 20, 20, 8, 12, 64, 64); // Body
            GuiScreen.drawModalRectWithCustomSizedTexture(slim ? 1 : 0, 8, 44, 20, slim ? 3 : 4, 12, 64, 64); // Right Arm
            GuiScreen.drawModalRectWithCustomSizedTexture(12, 8, 36, 52, slim ? 3 : 4, 12, 64, 64); // Left Arm
            GuiScreen.drawModalRectWithCustomSizedTexture(4, 20, 4, 20, 4, 12, 64, 64); // Right Leg
            GuiScreen.drawModalRectWithCustomSizedTexture(8, 20, 20, 52, 4, 12, 64, 64); // Left Leg
            if (mc.gameSettings.getModelParts().contains(EnumPlayerModelParts.HAT))
                GuiScreen.drawModalRectWithCustomSizedTexture(4, 0, 40, 8, 8, 8, 64, 64); // Head (Overlay)
            if (mc.gameSettings.getModelParts().contains(EnumPlayerModelParts.RIGHT_SLEEVE))
                GuiScreen.drawModalRectWithCustomSizedTexture(slim ? 1 : 0, 8, 44, 36, slim ? 3 : 4, 12, 64, 64); // Right Arm (Overlay)
            if (mc.gameSettings.getModelParts().contains(EnumPlayerModelParts.LEFT_SLEEVE))
                GuiScreen.drawModalRectWithCustomSizedTexture(12, 8, 52, 52, slim ? 3 : 4, 12, 64, 64); // Left Arm (Overlay)
            if (mc.gameSettings.getModelParts().contains(EnumPlayerModelParts.RIGHT_PANTS_LEG))
                GuiScreen.drawModalRectWithCustomSizedTexture(4, 20, 4, 36, 4, 12, 64, 64); // Right Leg (Overlay)
            if (mc.gameSettings.getModelParts().contains(EnumPlayerModelParts.LEFT_PANTS_LEG))
                GuiScreen.drawModalRectWithCustomSizedTexture(8, 20, 4, 52, 4, 12, 64, 64); // Left Leg (Overlay)
            GlStateManager.popMatrix();
        }
        if (state != null) {
            drawCenteredString(fontRenderer, state, this.width / 2, this.height - 90, 0xFFFF9900);
        }

        // Draw tooltips for token buttons
        if (copyTempToken.isMouseOver()) {
            drawHoveringText(fontRenderer.listFormattedStringToWidth(
                            TextFormatting.YELLOW + "Copy Temp Token (Access Token)\n" +
                                    TextFormatting.RESET + "Expires in ~24 hours.\n" +
                                    "Safe for temporary account sharing.", 200),
                    (int)(Mouse.getX() * width / mc.displayWidth),
                    (int)(height - Mouse.getY() * height / mc.displayHeight - 1));
        }
        if (copyPermToken.isMouseOver()) {
            drawHoveringText(fontRenderer.listFormattedStringToWidth(
                            TextFormatting.RED + "Copy Perm Token (Refresh Token)\n" +
                                    TextFormatting.RESET + "Never expires until password changed!\n" +
                                    TextFormatting.RED + "DANGEROUS: " +
                                    TextFormatting.RESET + "Grants permanent account access!", 200),
                    (int)(Mouse.getX() * width / mc.displayWidth),
                    (int)(height - Mouse.getY() * height / mc.displayHeight - 1));
        }
    }

    private void reloadSkins() {
        if (list.entries.isEmpty() || System.currentTimeMillis() <= nextSkinUpdate || state != null) return;
        IAS.SKIN_CACHE.clear();
        list.updateAccounts(search.getText());
        nextSkinUpdate = System.currentTimeMillis() + 15000L;
    }

    private void login() {
        if (list.selectedElement() < 0 || state != null) return;
        Account acc = list.entries.get(list.selectedElement()).account();
        updateButtons();
        state = "";
        acc.login((s, o) -> state = I18n.format(s, o)).whenComplete((d, t) -> {
            state = null;
            if (t != null) {
                mc.addScheduledTask(() -> mc.displayGuiScreen(new IASAlertScreen(
                        () -> mc.displayGuiScreen(this),
                        TextFormatting.RED + I18n.format("ias.error"),
                        String.valueOf(t))));
                return;
            }
            mc.addScheduledTask(() -> {
                mc.session = new Session(d.name(), UUIDTypeAdapter.fromUUID(d.uuid()),
                        d.accessToken(), d.userType());
            });
        });
    }

    private void loginOffline() {
        if (list.selectedElement() < 0 || state != null) return;
        Account acc = list.entries.get(list.selectedElement()).account();
        mc.session = new Session(acc.name(), UUIDTypeAdapter.fromUUID(UUID
                .nameUUIDFromBytes("OfflinePlayer".concat(acc.name()).getBytes(StandardCharsets.UTF_8))),
                "0", "legacy");
    }

    private void add() {
        if (state != null) return;
        mc.displayGuiScreen(new LoginScreen(this, I18n.format("ias.loginGui.add"),
                I18n.format("ias.loginGui.add.button"),
                I18n.format("ias.loginGui.add.button.tooltip"), acc -> {
            Config.accounts.add(acc);
            Config.save(mc.gameDir.toPath());
            list.updateAccounts(search.getText());
        }));
    }

    public void edit() {
        if (list.selectedElement() < 0 || state != null) return;
        Account acc = list.entries.get(list.selectedElement()).account();
        mc.displayGuiScreen(new LoginScreen(this, I18n.format("ias.loginGui.edit"),
                I18n.format("ias.loginGui.edit.button"),
                I18n.format("ias.loginGui.edit.button.tooltip"), newAcc -> {
            Config.accounts.set(Config.accounts.indexOf(acc), newAcc);
            Config.save(mc.gameDir.toPath());
        }));
    }

    public void delete() {
        if (list.selectedElement() < 0 || state != null) return;
        Account acc = list.entries.get(list.selectedElement()).account();
        if (isShiftKeyDown()) {
            Config.accounts.remove(acc);
            Config.save(mc.gameDir.toPath());
            updateButtons();
            list.updateAccounts(search.getText());
            return;
        }
        mc.displayGuiScreen(new GuiYesNo((b, id) -> {
            if (b) {
                Config.accounts.remove(acc);
                updateButtons();
                list.updateAccounts(search.getText());
            }
            mc.displayGuiScreen(this);
        }, I18n.format("ias.deleteGui.title"), I18n.format("ias.deleteGui.text", acc.name()), 0));
    }

    private void copyTempToken() {
        if (list.selectedElement() < 0 || state != null) return;
        Account acc = list.entries.get(list.selectedElement()).account();

        String tokenToCopy = null;
        String timeInfo = null;

        if (acc instanceof MicrosoftAccount) {
            MicrosoftAccount msaAcc = (MicrosoftAccount) acc;
            tokenToCopy = msaAcc.accessToken();
            timeInfo = "Valid for: ~24 hours from last refresh.";
        } else if (acc instanceof TokenAccount) {
            TokenAccount tokenAcc = (TokenAccount) acc;
            tokenToCopy = tokenAcc.accessToken();
            timeInfo = "Estimated time remaining: " + tokenAcc.getTimeRemaining();
        } else {
            mc.displayGuiScreen(new IASAlertScreen(() -> mc.displayGuiScreen(this),
                    TextFormatting.YELLOW + "Not Supported",
                    "This account type doesn't support token sharing.\n" +
                            "Only Microsoft and Token accounts can share tokens."));
            return;
        }

        final String finalToken = tokenToCopy;
        final String finalTimeInfo = timeInfo;

        mc.displayGuiScreen(new GuiYesNo((confirmed, id) -> {
            if (confirmed) {
                try {
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new StringSelection(finalToken), null);
                    mc.displayGuiScreen(new IASAlertScreen(() -> mc.displayGuiScreen(this),
                            TextFormatting.GREEN + "Temp Token (Access Token) Copied!",
                            finalTimeInfo + "\n \n" +
                                    "Your friend can import this using\n" +
                                    "'Add Temp Token' in the login screen.\n \n" +
                                    TextFormatting.RED + "WARNING: " + TextFormatting.RESET +
                                    "Anyone with this can log in until it expires!\n" +
                                    "Only share with people you trust!"));
                } catch (Throwable t) {
                    ru.vidtu.ias.SharedIAS.LOG.error("Failed to copy temp token.", t);
                    mc.displayGuiScreen(new IASAlertScreen(() -> mc.displayGuiScreen(this),
                            TextFormatting.RED + "Copy Failed",
                            "Failed to copy temp token: " + t.getMessage()));
                }
            } else {
                mc.displayGuiScreen(this);
            }
        },
                TextFormatting.YELLOW + "Copy Temp Token (Access Token)?",
                "Gives temporary (~24h) account access. Continue?", 0));
    }

    private void copyPermToken() {
        if (list.selectedElement() < 0 || state != null) return;
        Account acc = list.entries.get(list.selectedElement()).account();

        if (!(acc instanceof MicrosoftAccount)) {
            mc.displayGuiScreen(new IASAlertScreen(() -> mc.displayGuiScreen(this),
                    TextFormatting.YELLOW + "Not Supported",
                    "Only Microsoft accounts have Perm Tokens.\n" +
                            "Token accounts only have Temp Tokens."));
            return;
        }

        MicrosoftAccount msaAcc = (MicrosoftAccount) acc;

        // Package BOTH tokens as a simple JSON string
        // This mimics exactly what ias.json stores
        String tokenPackage = "{\"accessToken\":\"" + msaAcc.accessToken() +
                "\",\"refreshToken\":\"" + msaAcc.refreshToken() +
                "\",\"name\":\"" + msaAcc.name() +
                "\",\"uuid\":\"" + msaAcc.uuid().toString() + "\"}";

        // First warning
        mc.displayGuiScreen(new GuiYesNo((firstConfirm, id1) -> {
            if (!firstConfirm) {
                mc.displayGuiScreen(this);
                return;
            }
            // Second warning
            mc.displayGuiScreen(new GuiYesNo((secondConfirm, id2) -> {
                if (!secondConfirm) {
                    mc.displayGuiScreen(this);
                    return;
                }
                try {
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new StringSelection(tokenPackage), null);
                    mc.displayGuiScreen(new IASAlertScreen(() -> mc.displayGuiScreen(this),
                            TextFormatting.RED + "Perm Token (Refresh Token) Copied!",
                            TextFormatting.RED + "DANGER!\n" + TextFormatting.RESET +
                                    "This token grants PERMANENT access until\n" +
                                    "you change your Microsoft password!\n \n" +
                                    "Your friend can import using\n" +
                                    "'Add Perm Token' in the login screen.\n \n" +
                                    "Anyone with this token can:\n" +
                                    "- Log in indefinitely\n" +
                                    "- Generate new access tokens\n \n" +
                                    TextFormatting.YELLOW + "To revoke access:\n" +
                                    TextFormatting.RESET + "Change your Microsoft password!"));
                } catch (Throwable t) {
                    ru.vidtu.ias.SharedIAS.LOG.error("Failed to copy perm token.", t);
                    mc.displayGuiScreen(new IASAlertScreen(() -> mc.displayGuiScreen(this),
                            TextFormatting.RED + "Copy Failed",
                            "Failed to copy perm token: " + t.getMessage()));
                }
            },
                    TextFormatting.RED + "ARE YOU ABSOLUTELY SURE?",
                    "This grants PERMANENT account access! Really copy?", 0));
        },
                TextFormatting.RED + "WARNING: Perm Token (Refresh Token)",
                "This gives PERMANENT account access. Proceed?", 0));
    }

    private void changeName() {
        if (list.selectedElement() < 0 || state != null) return;
        Account acc = list.entries.get(list.selectedElement()).account();
        if (!(acc instanceof MicrosoftAccount) && !(acc instanceof TokenAccount)) {
            mc.displayGuiScreen(new IASAlertScreen(() -> mc.displayGuiScreen(this),
                    TextFormatting.YELLOW + "Not Supported",
                    "Name changing requires a Microsoft or Token account."));
            return;
        }
        mc.displayGuiScreen(new ChangeNameScreen(this, acc, () -> {
            Config.save(mc.gameDir.toPath());
            list.updateAccounts(search.getText());
        }));
    }

    private void changeSkin() {
        if (list.selectedElement() < 0 || state != null) return;
        Account acc = list.entries.get(list.selectedElement()).account();
        if (!(acc instanceof MicrosoftAccount) && !(acc instanceof TokenAccount)) {
            mc.displayGuiScreen(new IASAlertScreen(() -> mc.displayGuiScreen(this),
                    TextFormatting.YELLOW + "Not Supported",
                    "Skin & cape changing requires a Microsoft or Token account."));
            return;
        }
        mc.displayGuiScreen(new ChangeSkinScreen(this, acc, () -> {
            IAS.SKIN_CACHE.clear();
            Config.save(mc.gameDir.toPath());
            list.updateAccounts(search.getText());
        }));
    }

    private void updateButtons() {
        boolean selected = list.selectedElement() >= 0;
        boolean hasToken = selected && (
                list.entries.get(list.selectedElement()).account() instanceof MicrosoftAccount ||
                list.entries.get(list.selectedElement()).account() instanceof TokenAccount);
        boolean isMicrosoft = selected &&
                list.entries.get(list.selectedElement()).account() instanceof MicrosoftAccount;
        login.enabled = selected && state == null;
        loginOffline.enabled = selected;
        add.enabled = state == null;
        edit.enabled = selected && state == null;
        delete.enabled = selected && state == null;
        reloadSkins.enabled = !list.entries.isEmpty() && state == null &&
                System.currentTimeMillis() > nextSkinUpdate;
        copyTempToken.enabled = selected && state == null;
        copyPermToken.enabled = isMicrosoft && state == null;
        changeName.enabled = hasToken && state == null;
        changeSkin.enabled = hasToken && state == null;
    }

    @Override
    public void keyTyped(char c, int key) throws IOException {
        if (search.textboxKeyTyped(c, key)) return;
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(prev);
            return;
        }
        if (key == Keyboard.KEY_F5 || key == Keyboard.KEY_R) {
            reloadSkins();
            return;
        }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            if (GuiScreen.isShiftKeyDown()) loginOffline();
            else login();
            return;
        }
        if (key == Keyboard.KEY_A || key == Keyboard.KEY_EQUALS || key == Keyboard.KEY_ADD) {
            add();
            return;
        }
        if (key == Keyboard.KEY_PERIOD || key == Keyboard.KEY_DIVIDE) {
            edit();
            return;
        }
        if (key == Keyboard.KEY_DELETE || key == Keyboard.KEY_MINUS || key == Keyboard.KEY_SUBTRACT) {
            delete();
            return;
        }
        super.keyTyped(c, key);
    }
}