package the_fireplace.ias.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;
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
    private GuiTextField clientIdField;
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

        // Username field
        username = new GuiTextField(2, fontRenderer, this.width / 2 - 100, height / 2 - 65, 200, 20);
        username.setMaxStringLength(16);

        // Microsoft button
        addButton(microsoft = new GuiButton(3, this.width / 2 - 75, this.height / 2 - 40, 150, 20,
                I18n.format("ias.loginGui.microsoft")));

        // Token field - extra wide and unlimited length
        tokenField = new GuiTextField(4, fontRenderer, this.width / 2 - 150, height / 2 - 5, 300, 20);
        tokenField.setMaxStringLength(Integer.MAX_VALUE);

        // Temp and Perm token buttons side by side
        addButton(addTempToken = new GuiButton(5, this.width / 2 - 102, this.height / 2 + 20, 100, 20,
                "Add Temp Token"));
        addButton(addPermToken = new GuiButton(6, this.width / 2 + 2, this.height / 2 + 20, 100, 20,
                "Add Perm Token"));

        // Client ID field (optional)
        clientIdField = new GuiTextField(8, fontRenderer, this.width / 2 - 100, height / 2 + 55, 200, 20);
        clientIdField.setMaxStringLength(64);

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
                tokenField.mouseClicked(mouseX, mouseY, mouseButton) ||
                clientIdField.mouseClicked(mouseX, mouseY, mouseButton)) return;
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mx, int my, float delta) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, this.title, this.width / 2, 5, -1);

        // Nickname label
        drawCenteredString(fontRenderer, I18n.format("ias.loginGui.nickname"),
                this.width / 2, height / 2 - 78, -1);

        // Divider
        drawCenteredString(fontRenderer, "--- Or paste token / JSON below ---",
                this.width / 2, height / 2 - 18, 0xFF808080);

        // Client ID label
        drawCenteredString(fontRenderer, "Client ID (optional, only for refresh tokens from other apps)",
                this.width / 2, height / 2 + 43, 0xFF808080);

        if (state != null) {
            drawCenteredString(fontRenderer, state, width / 2, height / 2 + 85, 0xFFFF9900);
            drawCenteredString(fontRenderer,
                    SharedIAS.LOADING[(int) ((System.currentTimeMillis() / 50) % SharedIAS.LOADING.length)],
                    width / 2, height / 2 + 95, 0xFFFF9900);
        }

        username.drawTextBox();
        tokenField.drawTextBox();
        clientIdField.drawTextBox();

        // Placeholder text for client ID field
        if (clientIdField.getText().isEmpty() && !clientIdField.isFocused()) {
            drawString(fontRenderer, "Default: " + Auth.CLIENT_ID,
                    clientIdField.x + 4, clientIdField.y + (clientIdField.height - 8) / 2, 0xFF555555);
        }

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
                    TextFormatting.RED + "Perm Token / Refresh Token:\n" +
                            TextFormatting.RESET + "Accepts either:\n" +
                            "- Raw refresh token (starts with M.)\n" +
                            "- Full JSON from 'Copy Perm Token'\n\n" +
                            "Will automatically detect the format\n" +
                            "and fetch all account info.\n\n" +
                            "Set Client ID below if the token\n" +
                            "came from a different app.", 200), mx, my);
        }
    }

    @Override
    public void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(prev);
            return;
        }
        if (username.textboxKeyTyped(c, key) ||
                tokenField.textboxKeyTyped(c, key) ||
                clientIdField.textboxKeyTyped(c, key)) return;
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
        clientIdField.setEnabled(state == null);
        microsoft.enabled = state == null;
        addTempToken.enabled = !tokenField.getText().trim().isEmpty() && state == null;
        addPermToken.enabled = !tokenField.getText().trim().isEmpty() && state == null;
        username.updateCursorCounter();
        tokenField.updateCursorCounter();
        clientIdField.updateCursorCounter();
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
                    Map.Entry<UUID, String> profile = Auth.getProfile(rawToken);
                    account = new TokenAccount(profile.getValue(), rawToken,
                            profile.getKey(), System.currentTimeMillis());
                    SharedIAS.LOG.info("Temp token validated for: " + profile.getValue());
                } catch (Exception e) {
                    SharedIAS.LOG.warn("Could not validate temp token, it may be expired.", e);
                    mc.addScheduledTask(() -> mc.displayGuiScreen(new IASAlertScreen(
                            () -> mc.displayGuiScreen(this),
                            TextFormatting.RED + "Invalid Temp Token",
                            "Could not validate this access token.\n" +
                                    "It may have already expired!")));
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
        final String tokenData = tokenField.getText().trim();
        if (tokenData.isEmpty()) return;

        mc.displayGuiScreen(new GuiYesNo((confirmed, id) -> {
            if (!confirmed) {
                mc.displayGuiScreen(this);
                return;
            }

            mc.displayGuiScreen(this);
            state = "";

            SharedIAS.EXECUTOR.execute(() -> {
                try {
                    // Try to extract refresh token from input
                    String refreshToken = null;

                    // Check if it's JSON (from Copy Perm Token)
                    if (tokenData.startsWith("{")) {
                        try {
                            com.google.gson.JsonObject json = ru.vidtu.ias.SharedIAS.GSON
                                    .fromJson(tokenData, com.google.gson.JsonObject.class);
                            if (json.has("refreshToken")) {
                                refreshToken = json.get("refreshToken").getAsString();
                                SharedIAS.LOG.info("Extracted refresh token from pasted JSON.");
                            }
                        } catch (Throwable ignored) {
                            // Not valid JSON, fall through
                        }
                    }

                    // If not JSON or no refresh token found, treat as raw refresh token
                    if (refreshToken == null) {
                        refreshToken = tokenData;
                    }

                    // Use custom client ID if provided
                    final String clientId = clientIdField.getText().trim().isEmpty()
                            ? Auth.CLIENT_ID
                            : clientIdField.getText().trim();

                    // Run the full auth chain
                    state = "Refreshing token...";
                    Map.Entry<String, String> authRefreshTokens = Auth.refreshToken(refreshToken, clientId);
                    String newRefreshToken = authRefreshTokens.getValue();

                    state = "Authenticating with XBL...";
                    String xblToken = Auth.authXBL(authRefreshTokens.getKey());

                    state = "Authenticating with XSTS...";
                    Map.Entry<String, String> xstsTokenUserhash = Auth.authXSTS(xblToken);

                    state = "Getting Minecraft token...";
                    String accessToken = Auth.authMinecraft(xstsTokenUserhash.getValue(), xstsTokenUserhash.getKey());

                    state = "Getting profile...";
                    Map.Entry<UUID, String> profile = Auth.getProfile(accessToken);

                    MicrosoftAccount account = new MicrosoftAccount(
                            profile.getValue(),
                            accessToken,
                            newRefreshToken,
                            profile.getKey()
                    );
                    SharedIAS.LOG.info("Perm/refresh token imported for: " + profile.getValue());

                    mc.addScheduledTask(() -> {
                        handler.accept(account);
                        mc.displayGuiScreen(new IASAlertScreen(
                                () -> mc.displayGuiScreen(prev),
                                TextFormatting.GREEN + "Account Imported!",
                                "Account '" + profile.getValue() + "' imported successfully!\n" +
                                        "Click Login to authenticate."));
                    });

                } catch (Throwable t) {
                    SharedIAS.LOG.error("Unable to import perm/refresh token.", t);
                    String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                    mc.addScheduledTask(() -> mc.displayGuiScreen(new IASAlertScreen(
                            () -> mc.displayGuiScreen(this),
                            TextFormatting.RED + "Import Failed",
                            "Error: " + msg + "\n\n" +
                                    "Accepts either:\n" +
                                    "- Raw refresh token (starts with M.)\n" +
                                    "- Full JSON from 'Copy Perm Token'\n\n" +
                                    "If the token came from another app,\n" +
                                    "set the correct Client ID.")));
                    state = null;
                }
            });
        },
                TextFormatting.RED + "WARNING: Perm Token / Refresh Token",
                "This grants permanent account access.\n" +
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
