package the_fireplace.ias.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Keyboard;
import ru.vidtu.ias.SharedIAS;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.account.Auth;
import ru.vidtu.ias.gui.IASAlertScreen;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Screen for changing the player name.
 * Works with both Microsoft and Token accounts.
 */
public class ChangeNameScreen extends GuiScreen {
    private final GuiScreen prev;
    private final Account account;
    private final Runnable onSuccess;
    private GuiTextField nameField;
    private GuiButton changeButton;
    private String state;

    public ChangeNameScreen(GuiScreen prev, Account account, Runnable onSuccess) {
        this.prev = prev;
        this.account = account;
        this.onSuccess = onSuccess;
    }

    @Override
    public void initGui() {
        nameField = new GuiTextField(0, fontRenderer, width / 2 - 100, height / 2 - 10, 200, 20);
        nameField.setMaxStringLength(16);
        nameField.setText(account.name());
        nameField.setFocused(true);

        addButton(changeButton = new GuiButton(1, width / 2 - 100, height / 2 + 20, 200, 20,
                "Change Name"));
        addButton(new GuiButton(2, width / 2 - 100, height / 2 + 46, 200, 20,
                I18n.format("gui.cancel")));
    }

    @Override
    public void drawScreen(int mx, int my, float delta) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "Change Player Name", width / 2, height / 2 - 40, -1);
        drawCenteredString(fontRenderer, "Current: " + TextFormatting.YELLOW + account.name(),
                width / 2, height / 2 - 28, 0xFFAAAAAA);
        if (!isActiveSessionAccount()) {
            drawCenteredString(fontRenderer, TextFormatting.RED + "Log into this account before changing its profile.",
                    width / 2, height / 2 - 18, 0xFFFF5555);
        }
        nameField.drawTextBox();
        if (state != null) {
            drawCenteredString(fontRenderer, state, width / 2, height / 2 + 72, 0xFFFF9900);
        }
        drawCenteredString(fontRenderer, TextFormatting.GRAY + "Note: You can only change your name once every 30 days.",
                width / 2, height - 20, 0xFF808080);
        super.drawScreen(mx, my, delta);
    }

    @Override
    public void updateScreen() {
        nameField.updateCursorCounter();
        String newName = nameField.getText().trim();
        changeButton.enabled = isActiveSessionAccount() && !newName.isEmpty() && !newName.equals(account.name()) && state == null;
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        nameField.mouseClicked(mx, my, btn);
        super.mouseClicked(mx, my, btn);
    }

    @Override
    public void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(prev);
            return;
        }
        if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) && changeButton.enabled) {
            changeName();
            return;
        }
        nameField.textboxKeyTyped(c, key);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 1) changeName();
        else if (button.id == 2) mc.displayGuiScreen(prev);
    }

    private void changeName() {
        String newName = nameField.getText().trim();
        if (newName.isEmpty() || newName.equals(account.name())) return;
        if (!isActiveSessionAccount()) {
            mc.displayGuiScreen(new IASAlertScreen(
                    () -> mc.displayGuiScreen(this),
                    TextFormatting.RED + "Wrong Account",
                    "Log into " + TextFormatting.YELLOW + account.name() + TextFormatting.RESET +
                            " before changing this profile.\n \nCurrent session: " +
                            TextFormatting.YELLOW + mc.getSession().getUsername()));
            return;
        }

        state = "Changing name...";
        changeButton.enabled = false;

        state = "Validating token...";
        account.login((s, o) -> {}).whenComplete((data, loginError) -> {
            try {
                if (loginError != null) throw loginError;

                String token = data.accessToken();

                state = "Changing name to '" + newName + "'...";
                Map.Entry<UUID, String> result = Auth.changeName(token, newName);

                mc.addScheduledTask(() -> {
                    onSuccess.run();
                    mc.displayGuiScreen(new IASAlertScreen(
                            () -> mc.displayGuiScreen(prev),
                            TextFormatting.GREEN + "Name Changed!",
                            "Your name has been changed to: " + TextFormatting.YELLOW + result.getValue() +
                                    "\n \nNote: You won't be able to change it again for 30 days."));
                });
            } catch (Throwable t) {
                SharedIAS.LOG.error("Failed to change name.", t);
                String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                mc.addScheduledTask(() -> {
                    state = null;
                    mc.displayGuiScreen(new IASAlertScreen(
                            () -> mc.displayGuiScreen(this),
                            TextFormatting.RED + "Name Change Failed",
                            msg));
                });
            }
        });
    }

    private boolean isActiveSessionAccount() {
        if (mc == null || mc.getSession() == null) return false;
        String sessionId = mc.getSession().getPlayerID();
        String accountId = account.uuid().toString().replace("-", "");
        if (sessionId != null && sessionId.equalsIgnoreCase(accountId)) return true;
        return mc.getSession().getUsername().equalsIgnoreCase(account.name());
    }
}
