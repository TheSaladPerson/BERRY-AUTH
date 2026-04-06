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

        username = new GuiTextField(2, fontRenderer, this.width / 2 - 100, height / 2 - 65, 200, 20);
        username.setMaxStringLength(16);

        addButton(microsoft = new GuiButton(3, this.width / 2 - 75, this.height / 2 - 40, 150, 20,
                I18n.format("ias.loginGui.microsoft")));

        tokenField = new GuiTextField(4, fontRenderer, this.width / 2 - 100, height / 2 - 5, 200, 20);
        tokenField.setMaxStringLength(Integer.MAX_VALUE); // Increased max length just in case

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
                    TextFormatting.RED + "Perm Token (Full Account Data):\n" +
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
        // *** THE FIX: Grab the text BEFORE showing the confirmation GUI ***
        final String tokenData = tokenField.getText().trim();
        if (tokenData.isEmpty()) {
            return; // Don't do anything if the field is empty
        }

        mc.displayGuiScreen(new GuiYesNo((confirmed, id) -> {
            if (!confirmed) {
                mc.displayGuiScreen(this);
                return;
            }

            mc.displayGuiScreen(this);
            state = "";

            SharedIAS.EXECUTOR.execute(() -> {
                try {
                    // Use the 'tokenData' variable we saved earlier,
                    // not the (now empty) text field
                    state = "Importing Perm Token...";

                    com.google.gson.JsonObject json = ru.vidtu.ias.SharedIAS.GSON
                            .fromJson(tokenData, com.google.gson.JsonObject.class);

                    String accessToken = json.get("accessToken").getAsString();
                    String refreshToken = json.get("refreshToken").getAsString();
                    String name = json.get("name").getAsString();
                    UUID uuid = UUID.fromString(json.get("uuid").getAsString());

                    MicrosoftAccount account = new MicrosoftAccount(name, accessToken, refreshToken, uuid);
                    SharedIAS.LOG.info("Perm token imported for: " + name);

                    mc.addScheduledTask(() -> {
                        handler.accept(account);
                        mc.displayGuiScreen(new IASAlertScreen(
                                () -> mc.displayGuiScreen(prev),
                                TextFormatting.GREEN + "Perm Token Imported!",
                                "Account '" + name + "' imported successfully!\n" +
                                        "Click Login to authenticate.\n \n" +
                                        TextFormatting.YELLOW + "Note: " + TextFormatting.RESET +
                                        "The sender's account is NOT affected."));
                    });

                } catch (Throwable t) {
                    SharedIAS.LOG.error("Unable to import perm token.", t);
                    String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                    mc.addScheduledTask(() -> mc.displayGuiScreen(new IASAlertScreen(
                            () -> mc.displayGuiScreen(this),
                            TextFormatting.RED + "Perm Token Import Failed",
                            "Error: " + msg + "\n\n" +
                                    "Make sure you copied using 'Copy Perm Token'\n" +
                                    "and pasted the entire text block.")));
                    state = null;
                }
            });
        },
                TextFormatting.RED + "WARNING: Perm Token (Full Account Data)",
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