package the_fireplace.ias.gui;

import com.google.gson.JsonObject;
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

import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Screen for changing the skin and cape of an account.
 * Works with both Microsoft and Token accounts.
 * Uses the native Windows file explorer for file selection.
 */
public class ChangeSkinScreen extends GuiScreen {
    private static final int CAPES_PER_PAGE = 4;
    private static final int CAPE_BUTTON_BASE = 100;

    private final GuiScreen prev;
    private final Account account;
    private final Runnable onSuccess;

    // Skin controls
    private GuiTextField urlField;
    private GuiButton slimToggle;
    private GuiButton applyUrl;
    private GuiButton uploadFile;
    private boolean slim = false;
    private String selectedFileName;

    // Cape controls
    private GuiButton hideCapeButton;
    private GuiButton capePrevButton;
    private GuiButton capeNextButton;
    private final List<GuiButton> capeButtons = new ArrayList<>();
    private List<JsonObject> availableCapes = new ArrayList<>();
    private int selectedCapeIndex = -1;
    private int capePage = 0;
    private boolean capesLoaded = false;

    private String state;

    public ChangeSkinScreen(GuiScreen prev, Account account, Runnable onSuccess) {
        this.prev = prev;
        this.account = account;
        this.onSuccess = onSuccess;
    }

    @Override
    public void initGui() {
        int cx = width / 2;
        int baseY = 45;

        // --- SKIN SECTION ---
        // URL field
        urlField = new GuiTextField(0, fontRenderer, cx - 150, baseY + 15, 300, 20);
        urlField.setMaxStringLength(512);
        urlField.setFocused(true);

        // Slim/Classic toggle
        addButton(slimToggle = new GuiButton(1, cx - 150, baseY + 40, 145, 20,
                "Model: " + (slim ? "Slim (Alex)" : "Classic (Steve)")));

        // Apply from URL
        addButton(applyUrl = new GuiButton(2, cx + 5, baseY + 40, 145, 20,
                "Apply from URL"));

        // Upload file (native file picker)
        addButton(uploadFile = new GuiButton(3, cx - 150, baseY + 64, 300, 20,
                "Browse for Skin File..."));

        // --- CAPE SECTION ---
        addButton(capePrevButton = new GuiButton(7, cx - 150, baseY + 105, 45, 20, "<"));
        addButton(capeNextButton = new GuiButton(8, cx - 100, baseY + 105, 45, 20, ">"));
        addButton(hideCapeButton = new GuiButton(5, cx + 55, baseY + 105, 95, 20, "Hide Cape"));

        // --- CANCEL ---
        addButton(new GuiButton(6, cx - 75, this.height - 28, 150, 20,
                I18n.format("gui.cancel")));
        rebuildCapeButtons();

        // Load capes async
        loadCapes();
    }

    @Override
    public void drawScreen(int mx, int my, float delta) {
        drawDefaultBackground();

        // Title
        drawCenteredString(fontRenderer, "Change Skin & Cape", width / 2, 10, -1);
        drawCenteredString(fontRenderer, "Account: " + TextFormatting.YELLOW + account.name(),
                width / 2, 22, 0xFFAAAAAA);
        if (!isActiveSessionAccount()) {
            drawCenteredString(fontRenderer, TextFormatting.RED + "Log into this account before changing its profile.",
                    width / 2, 34, 0xFFFF5555);
        }

        int baseY = 45;

        // Skin section header
        drawString(fontRenderer, TextFormatting.UNDERLINE + "Skin", width / 2 - 150, baseY, 0xFFFFFFFF);
        drawCenteredString(fontRenderer, "Paste a skin URL or browse for a PNG file:",
                width / 2, baseY + 5, 0xFF808080);
        urlField.drawTextBox();

        // Selected file info
        if (selectedFileName != null) {
            drawCenteredString(fontRenderer, "File: " + TextFormatting.GREEN + selectedFileName,
                    width / 2, baseY + 87, 0xFFAAAAAA);
        }

        // Cape section header
        drawString(fontRenderer, TextFormatting.UNDERLINE + "Cape", width / 2 - 150, baseY + 95, 0xFFFFFFFF);

        if (!capesLoaded && state == null) {
            drawCenteredString(fontRenderer, "Loading capes...", width / 2, baseY + 130, 0xFF808080);
        } else if (capesLoaded && availableCapes.isEmpty()) {
            drawCenteredString(fontRenderer, TextFormatting.GRAY + "No capes available on this account.",
                    width / 2, baseY + 130, 0xFF808080);
        } else if (capesLoaded) {
            int pages = Math.max(1, (availableCapes.size() + CAPES_PER_PAGE - 1) / CAPES_PER_PAGE);
            drawCenteredString(fontRenderer, TextFormatting.GRAY + "Page " + (capePage + 1) + "/" + pages,
                    width / 2 - 3, baseY + 111, 0xFF808080);
        }

        // Status
        if (state != null) {
            drawCenteredString(fontRenderer, state, width / 2, this.height - 42, 0xFFFF9900);
        }

        super.drawScreen(mx, my, delta);
    }

    @Override
    public void updateScreen() {
        urlField.updateCursorCounter();
        boolean active = isActiveSessionAccount();
        applyUrl.enabled = active && !urlField.getText().trim().isEmpty() && state == null;
        uploadFile.enabled = active && state == null;
        slimToggle.enabled = state == null;
        updateCapeButtonState();
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        urlField.mouseClicked(mx, my, btn);
        super.mouseClicked(mx, my, btn);
    }

    @Override
    public void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(prev);
            return;
        }
        if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) && applyUrl.enabled) {
            applySkinUrl();
            return;
        }
        urlField.textboxKeyTyped(c, key);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 1: // Slim toggle
                slim = !slim;
                slimToggle.displayString = "Model: " + (slim ? "Slim (Alex)" : "Classic (Steve)");
                break;
            case 2: // Apply URL
                applySkinUrl();
                break;
            case 3: // Browse file
                browseForSkinFile();
                break;
            case 5: // Hide cape
                hideCape();
                break;
            case 6: // Cancel
                mc.displayGuiScreen(prev);
                break;
            case 7: // Previous cape page
                if (capePage > 0) {
                    capePage--;
                    rebuildCapeButtons();
                }
                break;
            case 8: // Next cape page
                if ((capePage + 1) * CAPES_PER_PAGE < availableCapes.size()) {
                    capePage++;
                    rebuildCapeButtons();
                }
                break;
            default:
                if (button.id >= CAPE_BUTTON_BASE) {
                    setCape(button.id - CAPE_BUTTON_BASE);
                }
        }
    }

    // ========== SKIN METHODS ==========

    private void applySkinUrl() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) return;
        if (!requireActiveSession()) return;

        state = "Validating token...";
        account.login((s, o) -> {}).whenComplete((data, loginError) -> {
            try {
                if (loginError != null) throw loginError;

                state = "Uploading skin from URL...";
                Auth.changeSkinUrl(data.accessToken(), url, slim);

                mc.addScheduledTask(() -> {
                    onSuccess.run();
                    mc.displayGuiScreen(new IASAlertScreen(
                            () -> mc.displayGuiScreen(prev),
                            TextFormatting.GREEN + "Skin Changed!",
                            "Your skin has been updated successfully.\n" +
                                    "Model: " + (slim ? "Slim (Alex)" : "Classic (Steve)") +
                                    "\n \nReload skins in the account list to see the change."));
                });
            } catch (Throwable t) {
                SharedIAS.LOG.error("Failed to change skin via URL.", t);
                String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                mc.addScheduledTask(() -> {
                    state = null;
                    mc.displayGuiScreen(new IASAlertScreen(
                            () -> mc.displayGuiScreen(this),
                            TextFormatting.RED + "Skin Change Failed",
                            msg));
                });
            }
        });
    }

    private void browseForSkinFile() {
        if (!requireActiveSession()) return;
        state = "Choose a skin PNG...";
        SharedIAS.EXECUTOR.execute(() -> {
            File picked;
            try {
                picked = openFilePicker();
            } catch (Throwable t) {
                SharedIAS.LOG.error("Failed to open skin file picker.", t);
                String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                mc.addScheduledTask(() -> {
                    state = null;
                    mc.displayGuiScreen(new IASAlertScreen(
                            () -> mc.displayGuiScreen(this),
                            TextFormatting.RED + "File Picker Failed",
                            msg));
                });
                return;
            }

            if (picked == null) {
                mc.addScheduledTask(() -> state = null);
                return;
            }

            mc.addScheduledTask(() -> {
                selectedFileName = picked.getName();
                state = "Uploading skin file...";
            });

            try {
                state = "Validating token...";
                account.login((s, o) -> {}).whenComplete((data, loginError) -> {
                    try {
                        if (loginError != null) throw loginError;

                        state = "Uploading " + picked.getName() + "...";
                        Auth.changeSkinFile(data.accessToken(), picked, slim);

                        mc.addScheduledTask(() -> {
                            onSuccess.run();
                            mc.displayGuiScreen(new IASAlertScreen(
                                    () -> mc.displayGuiScreen(prev),
                                    TextFormatting.GREEN + "Skin Changed!",
                                    "Skin uploaded from file:\n" +
                                            TextFormatting.YELLOW + picked.getName() +
                                            "\n \nModel: " + (slim ? "Slim (Alex)" : "Classic (Steve)") +
                                            "\n \nReload skins in the account list to see the change."));
                        });
                    } catch (Throwable t) {
                        SharedIAS.LOG.error("Failed to upload skin file.", t);
                        String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                        mc.addScheduledTask(() -> {
                            state = null;
                            mc.displayGuiScreen(new IASAlertScreen(
                                    () -> mc.displayGuiScreen(this),
                                    TextFormatting.RED + "Skin Upload Failed",
                                    msg));
                        });
                    }
                });
            } catch (Throwable t) {
                SharedIAS.LOG.error("Failed to upload skin file.", t);
                String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                mc.addScheduledTask(() -> {
                    state = null;
                    mc.displayGuiScreen(new IASAlertScreen(
                            () -> mc.displayGuiScreen(this),
                            TextFormatting.RED + "Skin Upload Failed",
                            msg));
                });
            }
        });
    }

    /**
     * Opens the native Windows file picker dialog and waits for the result.
     * Uses java.awt.FileDialog which shows the real OS file explorer dialog
     * and always appears on top of the game window.
     */
    private static File openFilePicker() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("Java is running in headless mode, so no file picker can be shown.");
        }

        AtomicReference<File> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        EventQueue.invokeLater(() -> {
            try {
                result.set(openNativeFileDialog());
            } catch (Throwable nativeFailure) {
                SharedIAS.LOG.warn("Native skin file picker failed, trying Swing fallback.", nativeFailure);
                try {
                    result.set(openSwingFileChooser());
                } catch (Throwable swingFailure) {
                    error.set(swingFailure);
                }
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        if (error.get() != null) {
            Throwable t = error.get();
            if (t instanceof Exception) throw (Exception) t;
            if (t instanceof Error) throw (Error) t;
            throw new RuntimeException(t);
        }
        return result.get();
    }

    private static File openNativeFileDialog() {
        Frame owner = new Frame("Select Skin PNG");
        owner.setUndecorated(true);
        owner.setAlwaysOnTop(true);
        owner.setLocationRelativeTo(null);
        owner.setVisible(true);

        FileDialog fd = null;
        try {
            fd = new FileDialog(owner, "Select Skin PNG", FileDialog.LOAD);
            fd.setFile("*.png");
            fd.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".png"));
            fd.setAlwaysOnTop(true);
            fd.setVisible(true);

            String dir = fd.getDirectory();
            String file = fd.getFile();
            return dir != null && file != null ? new File(dir, file) : null;
        } finally {
            if (fd != null) fd.dispose();
            owner.dispose();
        }
    }

    private static File openSwingFileChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Skin PNG");
        chooser.setFileFilter(new FileNameExtensionFilter("PNG skin files", "png"));
        chooser.setAcceptAllFileFilterUsed(false);
        int option = chooser.showOpenDialog(null);
        return option == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    // ========== CAPE METHODS ==========

    private void loadCapes() {
        account.login((s, o) -> {}).whenComplete((data, loginError) -> {
            try {
                if (loginError != null) throw loginError;

                List<JsonObject> capes = Auth.getCapes(data.accessToken());

                mc.addScheduledTask(() -> {
                    availableCapes = capes;
                    capesLoaded = true;

                    selectedCapeIndex = -1;
                    for (int i = 0; i < capes.size(); i++) {
                        JsonObject cape = capes.get(i);
                        if (cape.has("state") && cape.get("state").getAsString().equals("ACTIVE")) {
                            selectedCapeIndex = i;
                            capePage = i / CAPES_PER_PAGE;
                            break;
                        }
                    }
                    rebuildCapeButtons();
                });
            } catch (Throwable t) {
                SharedIAS.LOG.error("Failed to load capes.", t);
                mc.addScheduledTask(() -> {
                    capesLoaded = true;
                    rebuildCapeButtons();
                });
            }
        });
    }

    private void rebuildCapeButtons() {
        buttonList.removeAll(capeButtons);
        capeButtons.clear();

        if (capesLoaded && !availableCapes.isEmpty()) {
            int cx = width / 2;
            int baseY = 45;
            int start = capePage * CAPES_PER_PAGE;
            int end = Math.min(start + CAPES_PER_PAGE, availableCapes.size());
            for (int i = start; i < end; i++) {
                JsonObject cape = availableCapes.get(i);
                String alias = cape.has("alias") ? cape.get("alias").getAsString() : "Unknown";
                String label = (i == selectedCapeIndex ? "[Active] " : "") + formatCapeName(alias);
                if (label.length() > 34) label = label.substring(0, 31) + "...";
                capeButtons.add(addButton(new GuiButton(CAPE_BUTTON_BASE + i, cx - 150,
                        baseY + 130 + ((i - start) * 22), 300, 20, label)));
            }
        }

        updateCapeButtonState();
    }

    private void updateCapeButtonState() {
        boolean active = isActiveSessionAccount();
        boolean canEdit = active && capesLoaded && state == null;
        hideCapeButton.enabled = canEdit && selectedCapeIndex >= 0;
        capePrevButton.enabled = canEdit && capePage > 0;
        capeNextButton.enabled = canEdit && (capePage + 1) * CAPES_PER_PAGE < availableCapes.size();
        for (GuiButton capeButton : capeButtons) {
            int capeIndex = capeButton.id - CAPE_BUTTON_BASE;
            capeButton.enabled = canEdit && capeIndex != selectedCapeIndex;
        }
    }

    private String formatCapeName(String alias) {
        return alias.replaceAll("([a-z])([A-Z])", "$1 $2")
                    .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
    }

    private void setCape(int capeIndex) {
        if (capeIndex < 0 || capeIndex >= availableCapes.size()) return;
        if (!requireActiveSession()) return;

        String capeId = availableCapes.get(capeIndex).get("id").getAsString();
        state = "Setting cape...";
        updateCapeButtonState();

        account.login((s, o) -> {}).whenComplete((data, loginError) -> {
            try {
                if (loginError != null) throw loginError;

                Auth.setCape(data.accessToken(), capeId);
                mc.addScheduledTask(() -> {
                    state = null;
                    selectedCapeIndex = capeIndex;
                    rebuildCapeButtons();
                });
            } catch (Throwable t) {
                SharedIAS.LOG.error("Failed to set cape.", t);
                String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                mc.addScheduledTask(() -> {
                    state = null;
                    updateCapeButtonState();
                    mc.displayGuiScreen(new IASAlertScreen(
                            () -> mc.displayGuiScreen(this),
                            TextFormatting.RED + "Cape Change Failed",
                            msg));
                });
            }
        });
    }

    private void hideCape() {
        if (!requireActiveSession()) return;
        state = "Hiding cape...";
        updateCapeButtonState();

        account.login((s, o) -> {}).whenComplete((data, loginError) -> {
            try {
                if (loginError != null) throw loginError;

                Auth.hideCape(data.accessToken());
                mc.addScheduledTask(() -> {
                    state = null;
                    selectedCapeIndex = -1;
                    rebuildCapeButtons();
                });
            } catch (Throwable t) {
                SharedIAS.LOG.error("Failed to hide cape.", t);
                String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                mc.addScheduledTask(() -> {
                    state = null;
                    updateCapeButtonState();
                    mc.displayGuiScreen(new IASAlertScreen(
                            () -> mc.displayGuiScreen(this),
                            TextFormatting.RED + "Hide Cape Failed",
                            msg));
                });
            }
        });
    }

    private boolean requireActiveSession() {
        if (isActiveSessionAccount()) return true;
        mc.displayGuiScreen(new IASAlertScreen(
                () -> mc.displayGuiScreen(this),
                TextFormatting.RED + "Wrong Account",
                "Log into " + TextFormatting.YELLOW + account.name() + TextFormatting.RESET +
                        " before changing this profile.\n \nCurrent session: " +
                        TextFormatting.YELLOW + mc.getSession().getUsername()));
        return false;
    }

    private boolean isActiveSessionAccount() {
        if (mc == null || mc.getSession() == null) return false;
        String sessionId = mc.getSession().getPlayerID();
        String accountId = account.uuid().toString().replace("-", "");
        if (sessionId != null && sessionId.equalsIgnoreCase(accountId)) return true;
        return mc.getSession().getUsername().equalsIgnoreCase(account.name());
    }
}
