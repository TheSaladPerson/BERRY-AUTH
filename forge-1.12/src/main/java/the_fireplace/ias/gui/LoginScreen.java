package the_fireplace.ias.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.client.gui.GuiYesNo;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import ru.vidtu.ias.MicrosoftAuthCallback;
import ru.vidtu.ias.SharedIAS;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.account.Auth;
import ru.vidtu.ias.account.MicrosoftAccount;
import ru.vidtu.ias.account.OfflineAccount;
import ru.vidtu.ias.account.TokenAccount;
import ru.vidtu.ias.gui.IASAlertScreen;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Screen for adding and editing accounts.
 *
 * @author evilmidget38
 * @author The_Fireplace
 * @author VidTu
 */
public class LoginScreen extends GuiScreen {
    private final GuiScreen prev;
    private final String title;
    private final String buttonText;
    private final String buttonTip;
    private final Consumer<Account> handler;
    private final MicrosoftAuthCallback callback = new MicrosoftAuthCallback();
    private GuiTextField username;
    private GuiTextField tokenField;
    private GuiButton offline;
    private GuiButton microsoft;
    private GuiButton addTempToken;
    private GuiButton addPermToken;
    private String state;

    public LoginScreen(GuiScreen prev, String title, String buttonText, String buttonTip, Consumer<Account> handler) {
        this.prev = prev;
        this.title = title;
        this.buttonText = buttonText;
        this.buttonTip = buttonTip;
        this.handler = handler;
    }

    @Override
    public void initGui() {
        super.initGui();

        // Bottom buttons
        addButton(offline = new GuiButton(0, width / 2 - 152, this.height - 28, 150, 20, buttonText));
        offline.enabled = false;
        addButton(new GuiButton(1, this.width / 2 + 2, this.height - 28, 150, 20, I18n.format("gui.cancel")));

        // Layout:
        // [Nickname label]          <- height/2 - 78
        // [username field]          <- height/2 - 65
        // [Microsoft button]        <- height/2 - 40
        // [--- divider text ---]    <- height/2 - 18 (drawn)
        // [token field]             <- height/2 - 5
        // [Temp Token] [Perm Token] <- height/2 + 20

        username = new GuiTextField(2, fontRenderer, this.width / 2 - 100, height / 2 - 65, 200, 20);
        username.setMaxStringLength(16);

        addButton(microsoft = new GuiButton(3, this.width / 2 - 75, this.height / 2 - 40, 150, 20,
                I18n.format("ias.loginGui.microsoft")));

        tokenField = new GuiTextField(4, fontRenderer, this.width / 2 - 100, height / 2 - 5, 200, 20);
        tokenField.setMaxStringLength(5000);

        // Two buttons side by side for temp and perm token import
        addButton(addTempToken = new GuiButton(5, this.width / 2 - 102, this.height / 2 + 20, 100, 20,
                "Add Temp Token"));
        addButton(addPermToken = new GuiButton(6, this.width / 2 + 2, this.height / 2 + 20, 100, 20,
                "Add Perm Token"));

        addTempToken.enabled = false;
        addPermToken.enabled = false;
    }

    @Override
    public void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) loginOffline();
        else if (button.id == 1) mc.displayGuiScreen(prev);
        else if (button.id == 3) loginMicrosoft();
        else if (button.id == 5) loginTempToken();
        else if (button.id == 6) loginPermToken();
        super.actionPerformed(button);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (username.mouseClicked(mouseX, mouseY, mouseButton) ||
                tokenField.mouseClicked(mouseX, mouseY, mouseButton)) return;
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mx, int my, float delta) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, this.title, this.width / 2, 5, -1);

        // Nickname label above username field
        drawCenteredString(fontRenderer, I18n.format("ias.loginGui.nickname"),
                this.width / 2, height / 2 - 78, -1);

        // Divider between Microsoft button and token field
        drawCenteredString(fontRenderer, "--- Or paste Temp/Perm token below ---",
                this.width / 2, height / 2 - 18, 0xFF808080);

        if (state != null) {
            drawCenteredString(fontRenderer, state, width / 2, height / 2 + 55, 0xFFFF9900);
            drawCenteredString(fontRenderer,
                    SharedIAS.LOADING[(int) ((System.currentTimeMillis() / 50) % SharedIAS.LOADING.length)],
                    width / 2, height / 2 + 65, 0xFFFF9900);
        }

        username.drawTextBox();
        tokenField.drawTextBox();
        super.drawScreen(mx, my, delta);

        if (offline.isMouseOver()) {
            drawHoveringText(fontRenderer.listFormattedStringToWidth(buttonTip, 150), mx, my);
        }
        if (addTempToken.isMouseOver()) {
            drawHoveringText(fontRenderer.listFormattedStringToWidth(
                    TextFormatting.YELLOW + "Temp Token (Access Token):\n" +
                            TextFormatting.RESET + "Expires in ~24 hours.\n" +
                            "Safe to share for temporary access.", 200), mx, my);
        }
        if (addPermToken.isMouseOver()) {
            drawHoveringText(fontRenderer.listFormattedStringToWidth(
                    TextFormatting.RED + "Perm Token (Refresh Token):\n" +
                            TextFormatting.RESET + "Never expires until password changed.\n" +
                            "Grants permanent account access!\n" +
                            TextFormatting.RED + "Only use if you trust the source!", 200), mx, my);
        }
    }

    @Override
    public void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(prev);
            return;
        }
        if (username.textboxKeyTyped(c, key) || tokenField.textboxKeyTyped(c, key)) return;
        super.keyTyped(c, key);
    }

    @Override
    public void onGuiClosed() {
        SharedIAS.EXECUTOR.execute(callback::close);
        super.onGuiClosed();
    }

    @Override
    public void updateScreen() {
        offline.enabled = !username.getText().trim().isEmpty() && state == null;
        username.setEnabled(state == null);
        tokenField.setEnabled(state == null);
        microsoft.enabled = state == null;
        addTempToken.enabled = !tokenField.getText().trim().isEmpty() && state == null;
        addPermToken.enabled = !tokenField.getText().trim().isEmpty() && state == null;
        username.updateCursorCounter();
        tokenField.updateCursorCounter();
        super.updateScreen();
    }

    private void loginMicrosoft() {
        state = "";
        SharedIAS.EXECUTOR.execute(() -> {
            state = I18n.format("ias.loginGui.microsoft.checkBrowser");
            openURI(MicrosoftAuthCallback.MICROSOFT_AUTH_URL);
            callback.start((s, o) -> state = I18n.format(s, o),
                    I18n.format("ias.loginGui.microsoft.canClose")).whenComplete((acc, t) -> {
                if (mc.currentScreen != this) return;
                if (t != null) {
                    mc.addScheduledTask(() -> mc.displayGuiScreen(new IASAlertScreen(
                            () -> mc.displayGuiScreen(prev),
                            TextFormatting.RED + I18n.format("ias.error"),
                            String.valueOf(t))));
                    return;
                }
                if (acc == null) {
                    mc.addScheduledTask(() -> mc.displayGuiScreen(prev));
                    return;
                }
                mc.addScheduledTask(() -> {
                    handler.accept(acc);
                    mc.displayGuiScreen(prev);
                });
            });
        });
    }

    private void loginOffline() {
        state = "";
        SharedIAS.EXECUTOR.execute(() -> {
            state = I18n.format("ias.loginGui.offline.progress");
            Account account = new OfflineAccount(username.getText(), Auth.resolveUUID(username.getText()));
            mc.addScheduledTask(() -> {
                handler.accept(account);
                mc.displayGuiScreen(prev);
            });
        });
    }

    private void loginTempToken() {
        state = "";
        SharedIAS.EXECUTOR.execute(() -> {
            try {
                String rawToken = tokenField.getText().trim();
                state = "Validating Temp Token (Access Token)...";

                TokenAccount account;
                try {
                    // Validate immediately to get username/UUID
                    Map.Entry<UUID, String> profile = Auth.getProfile(rawToken);
                    account = new TokenAccount(profile.getValue(), rawToken,
                            profile.getKey(), System.currentTimeMillis());
                    SharedIAS.LOG.info("Temp token validated for: " + profile.getValue());
                } catch (Exception e) {
                    // Token may be expired or invalid
                    SharedIAS.LOG.warn("Could not validate temp token, it may be expired.", e);
                    mc.addScheduledTask(() -> mc.displayGuiScreen(new IASAlertScreen(
                            () -> mc.displayGuiScreen(this),
                            TextFormatting.RED + "Invalid Temp Token",
                            "Could not validate this access token.\n" +
                                    "It may have already expired!\n" +
                                    "Ask for a fresh token or use a Perm Token instead.")));
                    state = null;
                    return;
                }

                TokenAccount finalAccount = account;
                mc.addScheduledTask(() -> {
                    handler.accept(finalAccount);
                    mc.displayGuiScreen(prev);
                });
            } catch (Throwable t) {
                SharedIAS.LOG.error("Unable to import temp token.", t);
                mc.addScheduledTask(() -> mc.displayGuiScreen(new IASAlertScreen(
                        () -> mc.displayGuiScreen(this),
                        TextFormatting.RED + "Temp Token Import Failed",
                        "Failed to import temp token: " + t.getMessage())));
                state = null;
            }
        });
    }

    private void loginPermToken() {
        // Show warning before doing anything
        mc.displayGuiScreen(new GuiYesNo((confirmed, id) -> {
            if (!confirmed) {
                mc.displayGuiScreen(this);
                return;
            }
            // Go back to login screen and start the import
            mc.displayGuiScreen(this);
            state = "";
            SharedIAS.EXECUTOR.execute(() -> {
                try {
                    String refreshToken = tokenField.getText().trim();
                    state = "Authenticating with Perm Token (Refresh Token)...";

                    // Do the full auth flow with the refresh token
                    state = "Step: Refreshing token...";
                    Map.Entry<String, String> authTokens = Auth.refreshToken(refreshToken);
                    String newRefreshToken = authTokens.getValue();

                    state = "Step: Authenticating with XBL...";
                    String xblToken = Auth.authXBL(authTokens.getKey());

                    state = "Step: Authenticating with XSTS...";
                    Map.Entry<String, String> xstsData = Auth.authXSTS(xblToken);

                    state = "Step: Getting Minecraft token...";
                    String accessToken = Auth.authMinecraft(xstsData.getValue(), xstsData.getKey());

                    state = "Step: Getting profile...";
                    Map.Entry<UUID, String> profile = Auth.getProfile(accessToken);

                    // Save as full MicrosoftAccount so it can keep refreshing itself
                    MicrosoftAccount account = new MicrosoftAccount(
                            profile.getValue(),
                            accessToken,
                            newRefreshToken,
                            profile.getKey()
                    );

                    SharedIAS.LOG.info("Perm token authenticated for: " + profile.getValue());

                    mc.addScheduledTask(() -> {
                        handler.accept(account);
                        mc.displayGuiScreen(prev);
                    });
                } catch (Throwable t) {
                    SharedIAS.LOG.error("Unable to import perm token.", t);
                    mc.addScheduledTask(() -> mc.displayGuiScreen(new IASAlertScreen(
                            () -> mc.displayGuiScreen(this),
                            TextFormatting.RED + "Perm Token Import Failed",
                            "Failed to import perm token: " + t.getMessage() + "\n" +
                                    "Make sure this is a valid refresh token!")));
                    state = null;
                }
            });
        },
                TextFormatting.RED + "WARNING: Perm Token (Refresh Token)",
                "This grants permanent access to the account.\n" +
                        "Only import from sources you FULLY trust!\n" +
                        "Are you sure you want to continue?", 0));
    }

    private void openURI(String uri) {
        try {
            Desktop.getDesktop().browse(new URI(uri));
        } catch (Throwable t) {
            Sys.openURL(uri);
        }
    }
}